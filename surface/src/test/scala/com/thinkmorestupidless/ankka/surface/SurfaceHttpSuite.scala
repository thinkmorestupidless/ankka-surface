package com.thinkmorestupidless.ankka.surface

import com.github.plokhotnyuk.jsoniter_scala.core.{readFromString, JsonValueCodec}
import com.thinkmorestupidless.ankka.core.{Codecs, EntityId}
import com.thinkmorestupidless.ankka.http.{Acl, HttpServer}
import com.thinkmorestupidless.ankka.runtime.ProjectionRuntime
import com.thinkmorestupidless.ankka.testkit.{AnkkaTestKit, LogCapturing}
import Fixtures.*

import java.io.{BufferedReader, InputStreamReader}
import java.net.URI
import java.net.http.{HttpClient, HttpRequest as JdkRequest, HttpResponse as JdkResponse}
import java.time.{Clock, Instant, ZoneOffset}
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}
import scala.concurrent.duration.DurationInt

/**
 * The whole stack over real HTTP: the JDK client, the endpoint, the entity, the view, Postgres, and
 * a device's stream. One test per scenario of `features/surface/surface.feature` that the endpoint
 * can show; the entity suite holds the rest.
 */
class SurfaceHttpSuite extends munit.FunSuite with LogCapturing:

  override val munitTimeout = 4.minutes

  private given JsonValueCodec[Surface]         = Codecs.make[Surface]
  private given JsonValueCodec[Vector[Surface]] = Codecs.make[Vector[Surface]]
  private given JsonValueCodec[Said]            = Codecs.make[Said]
  private given JsonValueCodec[Heard]           = Codecs.make[Heard]
  private given JsonValueCodec[Focus]           = Codecs.make[Focus]

  private var testKit: AnkkaTestKit = null
  private var server: HttpServer    = null
  private var baseUrl: String       = ""
  private val http                  = HttpClient.newBuilder().build()

  /** A clock that ticks a second per read, so every `at` in a run is distinct and known. */
  private object ticking extends Clock:
    private var n                                     = 0
    def instant(): Instant                            = synchronized { n += 1; t(n) }
    def getZone                                       = ZoneOffset.UTC
    override def withZone(z: java.time.ZoneId): Clock = this

  private def endpoint(interpreter: Interpreter) =
    HttpServer.at("127.0.0.1", 0)(clients =>
      SurfaceEndpoint(
        "/surfaces",
        Acl.AllowAll,
        clients.componentClient,
        clients.viewClient,
        Surfaces,
        Rows,
        interpreter,
        ticking,
        focus = Some(FocusLane(Focuses, FocusRows))
      )
    )

  override def beforeAll(): Unit =
    server = endpoint(NewcastleInterpreter)
    testKit = AnkkaTestKit.start(
      Seq(Surfaces.descriptor, Rows.descriptor, Focuses.descriptor, FocusRows.descriptor),
      Seq(server, ProjectionRuntime())
    )
    baseUrl = s"http://127.0.0.1:${server.boundPort.getOrElse(fail("server did not bind"))}"

  override def afterAll(): Unit = if testKit != null then testKit.stop()

  private def send(method: String, path: String, body: Option[String] = None): (Int, String) =
    val builder = JdkRequest.newBuilder(URI.create(baseUrl + path))
    body match
      case Some(json) =>
        builder
          .header("Content-Type", "application/json")
          .method(method, JdkRequest.BodyPublishers.ofString(json)): Unit
      case None => builder.method(method, JdkRequest.BodyPublishers.noBody()): Unit
    val response = http.send(builder.build(), JdkResponse.BodyHandlers.ofString())
    (response.statusCode, response.body)

  private def open(id: String, owner: String = "trevor"): Surface =
    val (status, body) = send("POST", s"/surfaces/$id/open", Some(s"""{"owner":"$owner"}"""))
    assertEquals(status, 200, body)
    readFromString[Surface](body)

  private def move(id: String, json: String): (Int, String) =
    send("POST", s"/surfaces/$id/moves", Some(json))

  private def read(id: String): Surface =
    val (status, body) = send("GET", s"/surfaces/$id")
    assertEquals(status, 200, body)
    readFromString[Surface](body)

  /** A device's stream: the named events as they arrive, read on a thread of its own. */
  private final class DeviceStream(id: String, suffix: String = "/stream"):
    val events = LinkedBlockingQueue[(String, String)]()
    private val response = http.send(
      JdkRequest.newBuilder(URI.create(s"$baseUrl/surfaces/$id$suffix")).GET().build(),
      JdkResponse.BodyHandlers.ofInputStream()
    )
    val status: Int    = response.statusCode()
    private val reader = BufferedReader(InputStreamReader(response.body()))
    private val thread = Thread
      .ofVirtual()
      .start(() =>
        try
          // An event is its lines up to a blank one, in any order; a comment line is a heartbeat.
          var name = ""
          var data = Option.empty[String]
          var line = reader.readLine()
          while line != null do
            if line.isEmpty then
              data.foreach(d => events.put((name, d)))
              name = ""
              data = None
            else if line.startsWith("event:") then name = line.drop(6).trim
            else if line.startsWith("data:") then data = Some(line.drop(5).trim)
            line = reader.readLine()
        catch case _: Exception => ()
      )
    def next(): (String, String) =
      Option(events.poll(30, TimeUnit.SECONDS)).getOrElse(fail("the stream gave nothing in 30s"))
    def close(): Unit = { reader.close(); thread.interrupt() }

  test(
    "opening gives the owner and the home shape at version 1, and a surface nobody opened is 404"
  ) {
    assertEquals(send("GET", "/surfaces/never")._1, 404)
    val s = open("http-open")
    assertEquals((s.owner, s.shape, s.anchor, s.version), ("trevor", "home", None, 1L))
    assertEquals(read("http-open"), s)
  }

  test("a move changes several parts at once; one the vocabulary lacks is a 400 naming the word") {
    val _ = open("http-move")
    val (status, body) = move(
      "http-move",
      """{"shape":"shelf","anchor":{"kind":"theme","id":"newcastle"},"modifiers":{"time":"this weekend"}}"""
    )
    assertEquals(status, 200, body)
    val s = readFromString[Surface](body)
    assertEquals(
      (s.shape, s.anchor, s.modifiers),
      ("shelf", Some(Anchor("theme", "newcastle")), Map("time" -> "this weekend"))
    )
    val (bad, why) = move("http-move", """{"shape":"basket"}""")
    assertEquals(bad, 400, why)
    assert(why.contains("basket"), why)
    assertEquals(read("http-move").shape, "shelf")
  }

  test("a move on a surface nobody opened is a 409, and a move naming nothing a 400") {
    assertEquals(move("http-unopened", """{"shape":"shelf"}""")._1, 409)
    val _ = open("http-empty")
    assertEquals(move("http-empty", "{}")._1, 400)
  }

  test(
    "what was said is interpreted, applied with the text on the rail, and answered as understood"
  ) {
    val _ = open("http-say")
    val (status, body) =
      send("POST", "/surfaces/http-say/say", Some("""{"text":"anything about Newcastle"}"""))
    assertEquals(status, 200, body)
    val said = readFromString[Said](body)
    assert(said.understood)
    assertEquals(said.surface.shape, "shelf")
    assertEquals(said.surface.rail.map(_.text), List("anything about Newcastle"))
  }

  test("what was not understood changes nothing and says why") {
    val s = open("http-sandwich")
    val (status, body) =
      send("POST", "/surfaces/http-sandwich/say", Some("""{"text":"make me a sandwich"}"""))
    assertEquals(status, 200, body)
    val said = readFromString[Said](body)
    assert(!said.understood)
    assert(said.reason.exists(_.contains("sandwich")), said.reason.toString)
    assertEquals(said.surface.version, s.version)
    assertEquals(send("POST", "/surfaces/http-sandwich/say", Some("""{"text":"  "}"""))._1, 400)
  }

  test("a device shows and hides the surface over HTTP") {
    val _ = open("http-show")
    val (status, body) = send(
      "POST",
      "/surfaces/http-show/show",
      Some("""{"device":"tv-lounge","deviceClass":"tv","room":true}""")
    )
    assertEquals(status, 200, body)
    assertEquals(
      readFromString[Surface](body).showing.map(s => (s.device, s.room)),
      List(("tv-lounge", true))
    )
    val (hidden, after) =
      send("POST", "/surfaces/http-show/hide", Some("""{"device":"tv-lounge"}"""))
    assertEquals(hidden, 200, after)
    assertEquals(readFromString[Surface](after).showing, Nil)
  }

  test("a device streams the surface now, then every version as it is written, then its removal") {
    val _ = open("http-stream")
    // The row is written shortly after the open; wait for it, so the stream's first event is it.
    val deadline = System.nanoTime() + 30.seconds.toNanos
    while testKit.service.viewClient.forView(Rows).get("http-stream").isEmpty &&
      System.nanoTime() < deadline
    do Thread.sleep(100)
    val stream = DeviceStream("http-stream")
    assertEquals(stream.status, 200)
    val (first, data) = stream.next()
    assertEquals(first, "row")
    assert(data.contains("\"shape\":\"home\""), data)
    assertEquals(stream.next()._1, "caught-up")
    val _             = move("http-stream", """{"shape":"shelf","request":"shelves please"}""")
    val (name, moved) = stream.next()
    assertEquals(name, "row")
    assert(moved.contains("\"shape\":\"shelf\""), moved)
    assert(moved.contains("\"version\":2"), moved)
    assertEquals(send("DELETE", "/surfaces/http-stream")._1, 204)
    // A watch may give the same version twice; nothing older, and then the removal.
    val untilRemoved = Iterator.continually(stream.next()).takeWhile(_._1 != "removed").toList
    assert(untilRemoved.forall((n, d) => n == "row" && d.contains("\"version\":2")), untilRemoved)
    stream.close()
    assertEquals(send("GET", "/surfaces/http-stream")._1, 404)
    assertEquals(DeviceStream("http-stream").status, 404)
  }

  test("an owner's surfaces are listed from the view") {
    val _        = open("http-list-1", "lister")
    val _        = open("http-list-2", "lister")
    val deadline = System.nanoTime() + 30.seconds.toNanos
    var ids      = Set.empty[String]
    while ids != Set("http-list-1", "http-list-2") && System.nanoTime() < deadline do
      val (status, body) = send("GET", "/surfaces/?owner=lister")
      assertEquals(status, 200, body)
      ids = readFromString[Vector[Surface]](body).map(_.id).toSet
      if ids.size < 2 then Thread.sleep(100)
    assertEquals(ids, Set("http-list-1", "http-list-2"))
    assertEquals(send("GET", "/surfaces/")._1, 400)
  }

  test("every version is rebuilt from the journal after the service restarts") {
    val id = "http-restart"
    val _  = open(id)
    val _ =
      move(id, """{"shape":"shelf","anchor":{"kind":"theme","id":"newcastle"},"request":"one"}""")
    val _ =
      move(id, """{"shape":"item","anchor":{"kind":"market","id":"nufc-ars"},"request":"two"}""")
    val before =
      testKit.componentClient.forEventSourcedEntity(EntityId(id)).call(Surfaces.get).invoke()
    testKit.restartService(extensions = Seq(server, ProjectionRuntime()))
    val after =
      testKit.componentClient.forEventSourcedEntity(EntityId(id)).call(Surfaces.get).invoke()
    assertEquals(after, before)
    assertEquals(after.rail.map(_.text), List("two", "one"))
    // The server bound again, perhaps elsewhere.
    baseUrl = s"http://127.0.0.1:${server.boundPort.getOrElse(fail("server did not bind"))}"
  }

  test("hear answers the interpreter's move and persists nothing") {
    val s              = open("http-hear")
    val (status, body) = send("POST", "/surfaces/http-hear/hear", Some("""{"text":"newcastle"}"""))
    assertEquals(status, 200, body)
    val heard = readFromString[Heard](body)
    assert(heard.understood)
    assertEquals(heard.move.flatMap(_.anchor), Some(Anchor("theme", "newcastle")))
    assertEquals(read("http-hear").version, s.version)
    val (_, no)  = send("POST", "/surfaces/http-hear/hear", Some("""{"text":"sandwich"}"""))
    val notHeard = readFromString[Heard](no)
    assert(!notHeard.understood)
    assertEquals(notHeard.move, None)
    assertEquals(send("POST", "/surfaces/http-hear/hear", Some("""{"text":" "}"""))._1, 400)
  }

  test("what is said while one utterance is heard shares one rail entry") {
    val _ = open("http-utterance")
    def say(text: String, utterance: String) =
      val (status, body) = send(
        "POST",
        "/surfaces/http-utterance/say",
        Some(s"""{"text":"$text","utterance":"$utterance"}""")
      )
      assertEquals(status, 200, body)
      readFromString[Said](body).surface
    say("newcastle", "u-1"): Unit
    val after = say("newcastle please", "u-1")
    assertEquals(after.rail.map(_.text), List("newcastle please"))
    assertEquals(
      say("newcastle again", "u-2").rail.map(_.text),
      List("newcastle again", "newcastle please")
    )
  }

  test(
    "the focus beside a surface is set, read and streamed, and refused for a surface nobody opened"
  ) {
    val _ = open("http-focus")
    assertEquals(send("GET", "/surfaces/nobody/focus")._1, 404)
    val (empty, emptyBody) = send("GET", "/surfaces/http-focus/focus")
    assertEquals(empty, 200, emptyBody)
    assertEquals(readFromString[Focus](emptyBody).version, 0L)
    val deadline = System.nanoTime() + 30.seconds.toNanos
    val stream   = DeviceStream("http-focus", "/focus/stream")
    assertEquals(stream.status, 200)
    val (status, body) = send(
      "PUT",
      "/surfaces/http-focus/focus",
      Some("""{"device":"phone-1","fields":{"card":"m1"}}""")
    )
    assertEquals(status, 200, body)
    assertEquals(readFromString[Focus](body).fields, Map("card" -> "m1"))
    var seen = Option.empty[String]
    while seen.isEmpty && System.nanoTime() < deadline do
      val (name, data) = stream.next()
      if name == "row" && data.contains("\"card\":\"m1\"") then seen = Some(data)
    assert(seen.isDefined, "the focus was not streamed")
    stream.close()
    val (bad, badBody) = send(
      "PUT",
      "/surfaces/http-focus/focus",
      Some("""{"device":"phone-1","fields":{"colour":"red"}}""")
    )
    assertEquals(bad, 400, badBody)
  }
