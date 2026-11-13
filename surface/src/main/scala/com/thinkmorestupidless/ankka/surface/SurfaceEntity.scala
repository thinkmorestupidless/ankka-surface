package com.thinkmorestupidless.ankka.surface

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.core.Serializers.given
import com.thinkmorestupidless.ankka.sdk.*

/**
 * One surface, by id. The only place it changes, one command at a time: a move's parts are refused
 * whole or persisted together, so a renderer never sees half of one.
 */
final class SurfaceEntity(context: EventSourcedEntityContext, vocabulary: Vocabulary)
    extends EventSourcedEntity[Surface, SurfaceEvent]:

  def emptyState: Surface = Surface.empty(context.entityId)

  def applyEvent(event: SurfaceEvent): Surface =
    currentState.applyEvent(event, vocabulary.railLength)

  def open(open: Open): Effect[Surface] =
    val shape = open.shape.getOrElse(vocabulary.homeShape)
    if currentState.opened then effects.error("surface is already opened", ErrorCode.Conflict)
    else if open.owner.isBlank then effects.error("an owner is needed to open a surface")
    else if !vocabulary.shapes(shape) then
      effects.error(s"the vocabulary has no shape '$shape'", ErrorCode.BadRequest)
    else effects.persist(SurfaceEvent.Opened(open.owner, shape, open.at)).thenReplyState

  def move(move: Move): Effect[Surface] =
    if !currentState.opened then effects.error("surface is not opened", ErrorCode.Conflict)
    else if move.namesNothing then effects.error("a move names nothing")
    else
      vocabulary.refuse(move) match
        case Some(word) => effects.error(s"the vocabulary has no $word", ErrorCode.BadRequest)
        case None       => effects.persist(SurfaceEvent.Moved(move)).thenReplyState

  def show(show: Show): Effect[Surface] =
    if !currentState.opened then effects.error("surface is not opened", ErrorCode.Conflict)
    else if show.device.isBlank then effects.error("a device needs a name")
    else if !vocabulary.deviceClasses(show.deviceClass) then
      effects.error(
        s"the vocabulary has no device class '${show.deviceClass}'",
        ErrorCode.BadRequest
      )
    else
      effects
        .persist(
          SurfaceEvent.ShowingStarted(Showing(show.device, show.deviceClass, show.room, show.at))
        )
        .thenReplyState

  def hide(hide: Hide): Effect[Surface] =
    if !currentState.opened then effects.error("surface is not opened", ErrorCode.Conflict)
    else if !currentState.showing.exists(_.device == hide.device) then effects.reply(currentState)
    else effects.persist(SurfaceEvent.ShowingStopped(hide.device, hide.at)).thenReplyState

  /** Records the close and then deletes the surface, so the same id starts again unopened. */
  def close(close: Close): Effect[Done] =
    if !currentState.opened then effects.error("surface is not opened", ErrorCode.Conflict)
    else effects.persist(SurfaceEvent.Closed(close.at)).deleteEntity().thenReply(_ => Done)

  def get: ReadOnlyEffect[Surface] =
    if !currentState.opened then effects.error("surface is not opened", ErrorCode.NotFound)
    else effects.reply(currentState)

/**
 * What a consumer extends to register surfaces: `object Surfaces extends
 * SurfaceCompanion(ComponentId("surface"), vocabulary)`. The component id is the consumer's, so two
 * kinds of surface in one service keep apart.
 */
abstract class SurfaceCompanion(componentId: ComponentId, val vocabulary: Vocabulary)
    extends EventSourcedEntity.Companion[SurfaceEntity, Surface, SurfaceEvent](
      componentId = componentId,
      stateSerializer = Codecs.serializer[Surface]("surface"),
      eventSerializer = Codecs.serializer[SurfaceEvent]("surface-event")
    ):

  // Declared before the handlers below: object initialisation runs in order.
  given Serializer[Open]  = Codecs.serializer[Open]("surface-open")
  given Serializer[Move]  = Codecs.serializer[Move]("surface-move")
  given Serializer[Show]  = Codecs.serializer[Show]("surface-show")
  given Serializer[Hide]  = Codecs.serializer[Hide]("surface-hide")
  given Serializer[Close] = Codecs.serializer[Close]("surface-close")

  def create(context: EventSourcedEntityContext) = new SurfaceEntity(context, vocabulary)

  val open  = command("open")(_.open)
  val move  = command("move")(_.move)
  val show  = command("show")(_.show)
  val hide  = command("hide")(_.hide)
  val close = command("close")(_.close)
  val get   = query("get")(_.get)
