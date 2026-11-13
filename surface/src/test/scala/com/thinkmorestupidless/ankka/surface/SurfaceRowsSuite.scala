package com.thinkmorestupidless.ankka.surface

import com.thinkmorestupidless.ankka.core.effect.ViewEffect
import com.thinkmorestupidless.ankka.testkit.ViewTestKit
import Fixtures.*

/** The rows with no projection: each event reaches the handler with the row it holds. */
class SurfaceRowsSuite extends munit.FunSuite:

  test("the row is the surface as the entity would answer it") {
    val kit = ViewTestKit(Rows)
    val move = Move(
      shape = Some("shelf"),
      anchor = Some(Anchor("theme", "newcastle")),
      request = Some("anything about Newcastle"),
      at = t(1)
    )
    kit.change("s-1", SurfaceEvent.Opened("trevor", "home", t0)): Unit
    kit.change("s-1", SurfaceEvent.Moved(move)): Unit
    val expected = Surface
      .empty("s-1")
      .applyEvent(SurfaceEvent.Opened("trevor", "home", t0), 3)
      .applyEvent(SurfaceEvent.Moved(move), 3)
    assertEquals(kit.row("s-1"), Some(expected))
    assertEquals(kit.row("s-1").map(_.version), Some(2L))
    assertEquals(kit.row("s-1").map(_.rail.map(_.text)), Some(List("anything about Newcastle")))
  }

  test("a close changes nothing, and the deletion that follows removes the row") {
    val kit = ViewTestKit(Rows)
    kit.change("s-1", SurfaceEvent.Opened("trevor", "home", t0)): Unit
    assertEquals(kit.change("s-1", SurfaceEvent.Closed(t(1))), ViewEffect.Ignore)
    assertEquals(kit.deleted("s-1"), ViewEffect.DeleteRow)
    assertEquals(kit.row("s-1"), None)
  }
