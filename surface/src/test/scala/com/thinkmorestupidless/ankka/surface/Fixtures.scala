package com.thinkmorestupidless.ankka.surface

import com.thinkmorestupidless.ankka.core.ComponentId

import java.time.Instant

/** The vocabulary the living feature's background names, and a surface registered under it. */
object Fixtures:
  val vocabulary: Vocabulary = Vocabulary(
    shapes = Set("home", "shelf", "item"),
    homeShape = "home",
    anchorKinds = Set("theme", "market"),
    modifierKeys = Set("time"),
    pendingKinds = Set("forecast")
  )

  val t0: Instant              = Instant.parse("2026-11-13T09:00:00Z")
  def t(seconds: Int): Instant = t0.plusSeconds(seconds.toLong)

  object Surfaces extends SurfaceCompanion(ComponentId("surface"), vocabulary)
  object Rows     extends SurfaceRowsCompanion(ComponentId("surface-rows"), Surfaces)

  /** A rail of two, for the scenario that trims it. */
  object ShortRail
      extends SurfaceCompanion(ComponentId("surface-short"), vocabulary.copy(railLength = 2))

  /**
   * Turns any text naming Newcastle into a shelf anchored on the theme; understands nothing else.
   */
  object NewcastleInterpreter extends Interpreter:
    def interpret(surface: Surface, text: String): Interpretation =
      if text.toLowerCase.contains("newcastle") then
        Interpretation.Understood(
          Move(shape = Some("shelf"), anchor = Some(Anchor("theme", "newcastle")))
        )
      else Interpretation.NotUnderstood(s"nothing on the shelves matches '$text'")

  object NothingInterpreter extends Interpreter:
    def interpret(surface: Surface, text: String): Interpretation =
      Interpretation.NotUnderstood("this interpreter understands nothing")
