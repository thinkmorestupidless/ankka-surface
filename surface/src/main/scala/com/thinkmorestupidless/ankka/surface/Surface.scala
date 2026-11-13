package com.thinkmorestupidless.ankka.surface

import java.time.Instant

/** The thing a shape is about, by kind and id: a theme, a market, a person. */
final case class Anchor(kind: String, id: String)

/** An act the person has begun and not committed, held so every device shows it. */
final case class Pending(kind: String, fields: Map[String, String])

/**
 * One request on the rail, with the surface as it stood once the request was applied. `utterance`
 * names the utterance the request came from, when the device said; the moves of one utterance share
 * one entry.
 */
final case class RailEntry(
    text: String,
    shape: String,
    anchor: Option[Anchor],
    modifiers: Map[String, String],
    at: Instant,
    utterance: Option[String] = None
)

/** A device showing the surface. */
final case class Showing(device: String, deviceClass: String, room: Boolean, since: Instant)

/**
 * The representation of what a person last asked for. Field names are the contract
 * (`specs/001-surface/contracts/surface-http.md`): a renderer in any language reads this JSON.
 */
final case class Surface(
    id: String,
    owner: String,
    opened: Boolean,
    shape: String,
    anchor: Option[Anchor],
    modifiers: Map[String, String],
    pending: Option[Pending],
    rail: List[RailEntry],
    showing: List[Showing],
    version: Long
):
  private def next: Surface = copy(version = version + 1)

  def applyEvent(event: SurfaceEvent, railLength: Int): Surface = event match
    case SurfaceEvent.Opened(owner, shape, _) =>
      copy(owner = owner, opened = true, shape = shape).next
    case SurfaceEvent.Moved(move) => applyMove(move, railLength).next
    case SurfaceEvent.ShowingStarted(showing) =>
      copy(showing = showing :: this.showing.filterNot(_.device == showing.device)).next
    case SurfaceEvent.ShowingStopped(device, _) =>
      copy(showing = showing.filterNot(_.device == device)).next
    // The entity is deleted straight after; the event is there for what reads the journal.
    case SurfaceEvent.Closed(_) => this

  /**
   * The fold of one move, in the contract's order: the shape, the anchor (`clearAnchor` wins), the
   * modifiers cleared then set, the pending (`clearPending` wins), then the request put on the rail
   * with the surface it left. The device package applies the same fold. The version is the event's.
   */
  def applyMove(move: Move, railLength: Int): Surface =
    val shaped = move.shape.fold(this)(s => copy(shape = s))
    val anchored =
      if move.clearAnchor then shaped.copy(anchor = None)
      else move.anchor.fold(shaped)(a => shaped.copy(anchor = Some(a)))
    val modified =
      anchored.copy(modifiers = (anchored.modifiers -- move.clearModifiers) ++ move.modifiers)
    val pended =
      if move.clearPending then modified.copy(pending = None)
      else move.pending.fold(modified)(p => modified.copy(pending = Some(p)))
    move.request.fold(pended) { text =>
      val entry =
        RailEntry(text, pended.shape, pended.anchor, pended.modifiers, move.at, move.utterance)
      // A later move of the same utterance replaces the entry the earlier one made.
      val rest = pended.rail match
        case head :: tail if move.utterance.isDefined && head.utterance == move.utterance => tail
        case all                                                                          => all
      pended.copy(rail = (entry :: rest).take(railLength))
    }

object Surface:
  def empty(id: String): Surface =
    Surface(id, "", opened = false, "", None, Map.empty, None, Nil, Nil, 0L)

/** What happened to a surface. A closed set, each case carrying the time the command gave it. */
enum SurfaceEvent:
  case Opened(owner: String, shape: String, at: Instant)

  /**
   * One move, whole: the journal holds what was asked, and a device streams one row per request.
   */
  case Moved(move: Move)
  case ShowingStarted(showing: Showing)
  case ShowingStopped(device: String, at: Instant)
  case Closed(at: Instant)

/** Give a surface its owner and its first shape; the home shape when none is named. */
final case class Open(owner: String, shape: Option[String], at: Instant)

/**
 * One change with as many parts as it names, applied together: the shape, then the anchor
 * (`clearAnchor` wins), then each `clearModifiers` key removed and each `modifiers` entry set, then
 * the pending (`clearPending` wins), then the request put on the rail. `utterance` names the
 * utterance a request came from, so the moves made while one is still being heard share one rail
 * entry.
 */
final case class Move(
    shape: Option[String] = None,
    anchor: Option[Anchor] = None,
    clearAnchor: Boolean = false,
    modifiers: Map[String, String] = Map.empty,
    clearModifiers: List[String] = Nil,
    pending: Option[Pending] = None,
    clearPending: Boolean = false,
    request: Option[String] = None,
    at: Instant = Instant.EPOCH,
    utterance: Option[String] = None
):
  def namesNothing: Boolean =
    shape.isEmpty && anchor.isEmpty && !clearAnchor && modifiers.isEmpty &&
      clearModifiers.isEmpty && pending.isEmpty && !clearPending && request.isEmpty

  def withRequest(text: String): Move                = copy(request = Some(text))
  def at(instant: Instant): Move                     = copy(at = instant)
  def fromUtterance(utterance: Option[String]): Move = copy(utterance = utterance)

final case class Show(device: String, deviceClass: String, room: Boolean, at: Instant)
final case class Hide(device: String, at: Instant)
final case class Close(at: Instant)

/**
 * Where the attention is, as distinct from what was asked for: the card under a thumb, the outcome
 * a finger rests on. Held beside the surface as a key value entity, so it keeps its latest value
 * and no history, and a device in a room follows it live. The keys are the vocabulary's
 * `focusKeys`; empty fields mean nothing is focused.
 */
final case class Focus(
    id: String,
    device: String,
    fields: Map[String, String],
    at: Instant,
    version: Long
)

object Focus:
  def empty(id: String): Focus = Focus(id, "", Map.empty, Instant.EPOCH, 0L)

final case class SetFocus(device: String, fields: Map[String, String], at: Instant)
