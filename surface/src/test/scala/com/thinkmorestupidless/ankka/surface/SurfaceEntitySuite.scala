package com.thinkmorestupidless.ankka.surface

import com.thinkmorestupidless.ankka.core.{Done, ErrorCode}
import com.thinkmorestupidless.ankka.testkit.EventSourcedTestKit
import Fixtures.*

/** The entity with no runtime, through its own serializers, scenario by scenario. */
class SurfaceEntitySuite extends munit.FunSuite:

  private def opened(id: String = "s-1") =
    val kit = EventSourcedTestKit.of(Surfaces, id)
    val _   = kit.call(Surfaces.open)(Open("trevor", None, t0))
    kit

  test("opening a surface gives it an owner and the home shape, at version 1") {
    val kit    = EventSourcedTestKit.of(Surfaces, "s-1")
    val result = kit.call(Surfaces.open)(Open("trevor", None, t0))
    assertEquals(result.events, Vector(SurfaceEvent.Opened("trevor", "home", t0)))
    val s = result.replyValue
    assertEquals(
      (s.id, s.owner, s.opened, s.shape, s.anchor, s.version),
      ("s-1", "trevor", true, "home", None, 1L)
    )
  }

  test("opening twice is a conflict, and a shape the vocabulary lacks is a bad request") {
    val kit = opened()
    assertEquals(kit.call(Surfaces.open)(Open("trevor", None, t0)).error.code, ErrorCode.Conflict)
    val other = EventSourcedTestKit.of(Surfaces, "s-2")
    val bad   = other.call(Surfaces.open)(Open("trevor", Some("basket"), t0))
    assertEquals(bad.error.code, ErrorCode.BadRequest)
    assert(bad.errorMessage.contains("basket"), bad.errorMessage)
    assertEquals(other.allEvents, Vector.empty)
  }

  test("a move changes several parts at once and the version rises once per part") {
    val kit = opened()
    val result = kit.call(Surfaces.move)(
      Move(
        shape = Some("shelf"),
        anchor = Some(Anchor("theme", "newcastle")),
        modifiers = Map("time" -> "this weekend"),
        at = t(1)
      )
    )
    val s = result.replyValue
    assertEquals(s.shape, "shelf")
    assertEquals(s.anchor, Some(Anchor("theme", "newcastle")))
    assertEquals(s.modifiers, Map("time" -> "this weekend"))
    assertEquals(result.events.size, 3)
    assertEquals(s.version, 4L)
  }

  test("a move naming a shape the vocabulary lacks is refused whole and changes nothing") {
    val kit = opened()
    val result = kit.call(Surfaces.move)(
      Move(shape = Some("basket"), anchor = Some(Anchor("theme", "x")), at = t(1))
    )
    assertEquals(result.error.code, ErrorCode.BadRequest)
    assert(result.errorMessage.contains("shape 'basket'"), result.errorMessage)
    assertEquals(result.events, Vector.empty)
    assertEquals(kit.currentState.shape, "home")
    assertEquals(kit.currentState.version, 1L)
  }

  test("an anchor kind, a modifier key and a pending kind the vocabulary lacks are each named") {
    val kit  = opened()
    val kind = kit.call(Surfaces.move)(Move(anchor = Some(Anchor("person", "p1")), at = t(1)))
    assert(kind.errorMessage.contains("anchor kind 'person'"), kind.errorMessage)
    val key = kit.call(Surfaces.move)(Move(modifiers = Map("order" -> "closing"), at = t(1)))
    assert(key.errorMessage.contains("modifier key 'order'"), key.errorMessage)
    val cleared = kit.call(Surfaces.move)(Move(clearModifiers = List("order"), at = t(1)))
    assert(cleared.errorMessage.contains("modifier key 'order'"), cleared.errorMessage)
    val pending =
      kit.call(Surfaces.move)(Move(pending = Some(Pending("stake", Map.empty)), at = t(1)))
    assert(pending.errorMessage.contains("pending kind 'stake'"), pending.errorMessage)
    assertEquals(kit.currentState.version, 1L)
  }

  test("a move on a surface nobody opened is a conflict, and so is every other command") {
    val kit = EventSourcedTestKit.of(Surfaces, "s-9")
    assertEquals(
      kit.call(Surfaces.move)(Move(shape = Some("shelf"), at = t0)).error.code,
      ErrorCode.Conflict
    )
    assertEquals(
      kit.call(Surfaces.show)(Show("d", "phone", false, t0)).error.code,
      ErrorCode.Conflict
    )
    assertEquals(kit.call(Surfaces.close)(Close(t0)).error.code, ErrorCode.Conflict)
    assertEquals(kit.call(Surfaces.get).error.code, ErrorCode.NotFound)
  }

  test("a move naming nothing is refused") {
    val result = opened().call(Surfaces.move)(Move(at = t(1)))
    assertEquals(result.error.code, ErrorCode.BadRequest)
    assert(result.errorMessage.contains("names nothing"), result.errorMessage)
  }

  test("a request is kept on the rail with the surface it left, newest first") {
    val kit = opened()
    val _ = kit.call(Surfaces.move)(
      Move(
        shape = Some("shelf"),
        anchor = Some(Anchor("theme", "newcastle")),
        request = Some("anything about Newcastle"),
        at = t(1)
      )
    )
    val _ = kit.call(Surfaces.move)(
      Move(
        shape = Some("item"),
        anchor = Some(Anchor("market", "nufc-ars")),
        request = Some("tell me about the derby"),
        at = t(2)
      )
    )
    assertEquals(
      kit.currentState.rail.map(e => (e.text, e.shape, e.anchor.map(_.id), e.at)),
      List(
        ("tell me about the derby", "item", Some("nufc-ars"), t(2)),
        ("anything about Newcastle", "shelf", Some("newcastle"), t(1))
      )
    )
  }

  test("the rail keeps only its length") {
    val kit = EventSourcedTestKit.of(ShortRail, "s-1")
    val _   = kit.call(ShortRail.open)(Open("trevor", None, t0))
    (1 to 3).foreach(i =>
      kit.call(ShortRail.move)(Move(request = Some(s"request $i"), at = t(i))): Unit
    )
    assertEquals(kit.currentState.rail.map(_.text), List("request 3", "request 2"))
  }

  test("a pending act is held and cleared, and clearing wins over setting") {
    val kit = opened()
    val _ = kit.call(Surfaces.move)(
      Move(
        pending = Some(Pending("forecast", Map("outcome" -> "Yes", "estimate" -> "70"))),
        at = t(1)
      )
    )
    assertEquals(kit.currentState.pending.map(_.fields("estimate")), Some("70"))
    val _ = kit.call(Surfaces.move)(
      Move(pending = Some(Pending("forecast", Map.empty)), clearPending = true, at = t(2))
    )
    assertEquals(kit.currentState.pending, None)
  }

  test("a device shows and hides the surface, and showing again replaces its record") {
    val kit = opened()
    val _   = kit.call(Surfaces.show)(Show("tv-lounge", "tv", room = true, t(1)))
    val _   = kit.call(Surfaces.show)(Show("phone-1", "phone", room = false, t(2)))
    assertEquals(
      kit.currentState.showing.map(s => (s.device, s.deviceClass, s.room)).toSet,
      Set(("tv-lounge", "tv", true), ("phone-1", "phone", false))
    )
    val _ = kit.call(Surfaces.show)(Show("phone-1", "laptop", room = false, t(3)))
    assertEquals(kit.currentState.showing.count(_.device == "phone-1"), 1)
    assertEquals(
      kit.currentState.showing.find(_.device == "phone-1").map(_.deviceClass),
      Some("laptop")
    )
    val _ = kit.call(Surfaces.hide)(Hide("tv-lounge", t(4)))
    assertEquals(kit.currentState.showing.map(_.device), List("phone-1"))
    // Hiding a device that is not showing changes nothing and refuses nothing.
    val before = kit.allEvents.size
    assertEquals(
      kit.call(Surfaces.hide)(Hide("ghost", t(5))).replyValue.version,
      kit.currentState.version
    )
    assertEquals(kit.allEvents.size, before)
  }

  test("a device class the vocabulary lacks is refused") {
    val result = opened().call(Surfaces.show)(Show("watch-1", "watch", false, t(1)))
    assertEquals(result.error.code, ErrorCode.BadRequest)
    assert(result.errorMessage.contains("watch"), result.errorMessage)
  }

  test("closing records the close and deletes the surface, which then reads as never opened") {
    val kit    = opened()
    val result = kit.call(Surfaces.close)(Close(t(1)))
    assertEquals(result.replyValue, Done)
    assertEquals(result.events, Vector(SurfaceEvent.Closed(t(1))))
    assert(kit.isDeleted)
  }

  test("the fold alone rebuilds the state from the journal") {
    val kit = opened()
    val _ = kit.call(Surfaces.move)(
      Move(
        shape = Some("shelf"),
        anchor = Some(Anchor("theme", "newcastle")),
        request = Some("r"),
        at = t(1)
      )
    )
    val _ = kit.call(Surfaces.show)(Show("phone-1", "phone", false, t(2)))
    val folded =
      kit.allEvents.foldLeft(Surface.empty("s-1"))(_.applyEvent(_, vocabulary.railLength))
    assertEquals(folded, kit.currentState)
  }
