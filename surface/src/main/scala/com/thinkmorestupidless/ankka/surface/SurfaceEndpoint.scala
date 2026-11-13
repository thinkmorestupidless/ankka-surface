package com.thinkmorestupidless.ankka.surface

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.core.{Codecs, EntityId}
import com.thinkmorestupidless.ankka.http.*
import com.thinkmorestupidless.ankka.runtime.ViewClient
import com.thinkmorestupidless.ankka.sdk.ComponentClient

import java.time.Clock

/** Give a surface its owner and its first shape; the vocabulary's home shape when none is named. */
final case class OpenBody(owner: String, shape: Option[String] = None)

/** A move, as a device sends it: the contract's `Move`, with the time stamped by the endpoint. */
final case class MoveBody(
    shape: Option[String] = None,
    anchor: Option[Anchor] = None,
    clearAnchor: Boolean = false,
    modifiers: Map[String, String] = Map.empty,
    clearModifiers: List[String] = Nil,
    pending: Option[Pending] = None,
    clearPending: Boolean = false,
    request: Option[String] = None
):
  def toMove(at: java.time.Instant): Move =
    Move(shape, anchor, clearAnchor, modifiers, clearModifiers, pending, clearPending, request, at)

final case class SayBody(text: String)
final case class ShowBody(device: String, deviceClass: String, room: Boolean = false)
final case class HideBody(device: String)

/** What `/say` answers: whether the words were understood, why not, and the surface now. */
final case class Said(understood: Boolean, reason: Option[String], surface: Surface)

/**
 * The surface over HTTP, as `specs/001-surface/contracts/surface-http.md` gives it, under the
 * prefix and the ACL the consumer chooses. Every command's time is stamped here, from the clock the
 * consumer may replace, so the entity reads none.
 */
final class SurfaceEndpoint(
    prefix: String,
    val acl: Acl,
    client: ComponentClient,
    views: ViewClient,
    surfaces: SurfaceCompanion,
    rows: SurfaceRowsCompanion,
    interpreter: Interpreter,
    clock: Clock = Clock.systemUTC()
) extends HttpEndpoint(prefix):

  private given JsonValueCodec[Surface]         = Codecs.make[Surface]
  private given JsonValueCodec[Vector[Surface]] = Codecs.make[Vector[Surface]]
  private given JsonValueCodec[OpenBody]        = Codecs.make[OpenBody]
  private given JsonValueCodec[MoveBody]        = Codecs.make[MoveBody]
  private given JsonValueCodec[SayBody]         = Codecs.make[SayBody]
  private given JsonValueCodec[ShowBody]        = Codecs.make[ShowBody]
  private given JsonValueCodec[HideBody]        = Codecs.make[HideBody]
  private given JsonValueCodec[Said]            = Codecs.make[Said]

  private def surface(id: String) = client.forEventSourcedEntity(EntityId(id))
  private def now                 = clock.instant()

  get("/") { () =>
    val owner = query.required[String]("owner")
    views.forView(rows).ask(rows.byOwner, "owner" -> owner)
  }

  get("/{id}") { (id: String) =>
    surface(id).call(surfaces.get).invoke()
  }

  // The row watched, as ankka serves a watch: `row`, `caught-up`, `removed`, `ended`. Read first,
  // so a surface nobody opened is 404 rather than an empty stream.
  sseEvents("/{id}/stream") { (id: String) =>
    val _ = surface(id).call(surfaces.get).invoke()
    views.forView(rows).watchRow(id).asSse
  }

  postBody("/{id}/open") { (id: String, body: OpenBody) =>
    surface(id).call(surfaces.open).invoke(Open(body.owner, body.shape, now))
  }

  postBody("/{id}/moves") { (id: String, body: MoveBody) =>
    surface(id).call(surfaces.move).invoke(body.toMove(now))
  }

  postBody("/{id}/say") { (id: String, body: SayBody) =>
    if body.text.isBlank then throw HttpProblem.badRequest("nothing was said")
    val before = surface(id).call(surfaces.get).invoke()
    interpreter.interpret(before, body.text) match
      case Interpretation.Understood(move) =>
        val after = surface(id).call(surfaces.move).invoke(move.withRequest(body.text).at(now))
        Said(understood = true, None, after)
      case Interpretation.NotUnderstood(reason) =>
        Said(understood = false, Some(reason), before)
  }

  postBody("/{id}/show") { (id: String, body: ShowBody) =>
    surface(id).call(surfaces.show).invoke(Show(body.device, body.deviceClass, body.room, now))
  }

  postBody("/{id}/hide") { (id: String, body: HideBody) =>
    surface(id).call(surfaces.hide).invoke(Hide(body.device, now))
  }

  delete("/{id}") { (id: String) =>
    surface(id).call(surfaces.close).invoke(Close(now))
  }
