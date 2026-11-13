package com.thinkmorestupidless.ankka.surface

import java.time.Instant

/** The thing a shape is about, by kind and id: a theme, a market, a person. */
final case class Anchor(kind: String, id: String)

/** An act the person has begun and not committed, held so every device shows it. */
final case class Pending(kind: String, fields: Map[String, String])

/** One request on the rail, with the surface as it stood once the request was applied. */
final case class RailEntry(
    text: String,
    shape: String,
    anchor: Option[Anchor],
    modifiers: Map[String, String],
    at: Instant
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
    case SurfaceEvent.ShapeChanged(shape, _)   => copy(shape = shape).next
    case SurfaceEvent.AnchorChanged(anchor, _) => copy(anchor = anchor).next
    case SurfaceEvent.ModifierSet(key, value, _) =>
      copy(modifiers = modifiers.updated(key, value)).next
    case SurfaceEvent.ModifierCleared(key, _) => copy(modifiers = modifiers - key).next
    case SurfaceEvent.PendingSet(pending, _)  => copy(pending = Some(pending)).next
    case SurfaceEvent.PendingCleared(_)       => copy(pending = None).next
    case SurfaceEvent.Requested(text, at) =>
      copy(rail = (RailEntry(text, shape, anchor, modifiers, at) :: rail).take(railLength)).next
    case SurfaceEvent.ShowingStarted(showing) =>
      copy(showing = showing :: this.showing.filterNot(_.device == showing.device)).next
    case SurfaceEvent.ShowingStopped(device, _) =>
      copy(showing = showing.filterNot(_.device == device)).next
    // The entity is deleted straight after; the event is there for what reads the journal.
    case SurfaceEvent.Closed(_) => this

object Surface:
  def empty(id: String): Surface =
    Surface(id, "", opened = false, "", None, Map.empty, None, Nil, Nil, 0L)

/** What happened to a surface. A closed set, each case carrying the time the command gave it. */
enum SurfaceEvent:
  case Opened(owner: String, shape: String, at: Instant)
  case ShapeChanged(shape: String, at: Instant)
  case AnchorChanged(anchor: Option[Anchor], at: Instant)
  case ModifierSet(key: String, value: String, at: Instant)
  case ModifierCleared(key: String, at: Instant)
  case PendingSet(pending: Pending, at: Instant)
  case PendingCleared(at: Instant)
  case Requested(text: String, at: Instant)
  case ShowingStarted(showing: Showing)
  case ShowingStopped(device: String, at: Instant)
  case Closed(at: Instant)

/** Give a surface its owner and its first shape; the home shape when none is named. */
final case class Open(owner: String, shape: Option[String], at: Instant)

/**
 * One change with as many parts as it names, applied together: the shape, then the anchor
 * (`clearAnchor` wins), then each `clearModifiers` key removed and each `modifiers` entry set, then
 * the pending (`clearPending` wins), then the request put on the rail.
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
    at: Instant = Instant.EPOCH
):
  def namesNothing: Boolean =
    shape.isEmpty && anchor.isEmpty && !clearAnchor && modifiers.isEmpty &&
      clearModifiers.isEmpty && pending.isEmpty && !clearPending && request.isEmpty

  def withRequest(text: String): Move = copy(request = Some(text))
  def at(instant: Instant): Move      = copy(at = instant)

final case class Show(device: String, deviceClass: String, room: Boolean, at: Instant)
final case class Hide(device: String, at: Instant)
final case class Close(at: Instant)
