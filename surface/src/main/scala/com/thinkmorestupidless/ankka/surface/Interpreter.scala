package com.thinkmorestupidless.ankka.surface

/** What an interpreter made of a request: a move, or a reason it could make none. */
enum Interpretation:
  case Understood(move: Move)
  case NotUnderstood(reason: String)

/**
 * The seam a consumer fills: from the surface as it stands and the words a person said or typed, a
 * move. The library ships none. An interpreter never acts past the surface: an act it hears becomes
 * `pending`, and committing it is the consumer's own route.
 */
trait Interpreter:
  def interpret(surface: Surface, text: String): Interpretation
