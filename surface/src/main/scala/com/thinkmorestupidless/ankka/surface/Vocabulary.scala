package com.thinkmorestupidless.ankka.surface

/**
 * What a consumer declares a surface may hold. The entity refuses, before persisting, any part of a
 * move that names a word the vocabulary lacks, so a journal never holds a shape no renderer knows.
 */
final case class Vocabulary(
    shapes: Set[String],
    homeShape: String,
    anchorKinds: Set[String] = Set.empty,
    modifierKeys: Set[String] = Set.empty,
    pendingKinds: Set[String] = Set.empty,
    deviceClasses: Set[String] = Set("phone", "laptop", "tv"),
    railLength: Int = 3,
    focusKeys: Set[String] = Set.empty
):
  require(shapes.contains(homeShape), s"the home shape '$homeShape' is not one of the shapes")
  require(railLength >= 0, "the rail's length is not negative")

  /** The first word a move names that this vocabulary lacks, with what it was meant as. */
  def refuse(move: Move): Option[String] =
    move.shape.filterNot(shapes).map(s => s"shape '$s'") orElse
      move.anchor.map(_.kind).filterNot(anchorKinds).map(k => s"anchor kind '$k'") orElse
      (move.modifiers.keys ++ move.clearModifiers)
        .find(!modifierKeys(_))
        .map(k => s"modifier key '$k'") orElse
      move.pending.map(_.kind).filterNot(pendingKinds).map(k => s"pending kind '$k'")

  /** The first focus key this vocabulary lacks. */
  def refuseFocus(fields: Map[String, String]): Option[String] =
    fields.keys.find(!focusKeys(_)).map(k => s"focus key '$k'")
