package com.thinkmorestupidless.ankka.surface

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.*

/**
 * The focus beside one surface, by the surface's id. A key value entity: the latest value only, no
 * journal, so a thumb moving across a shelf costs nothing to keep and a device that was slow may
 * skip what it missed.
 */
final class SurfaceFocusEntity(context: KeyValueEntityContext, vocabulary: Vocabulary)
    extends KeyValueEntity[Focus]:

  def emptyState: Focus = Focus.empty(context.entityId)

  def set(set: SetFocus): Effect[Focus] =
    if set.device.isBlank then effects.error("a device needs a name")
    else
      vocabulary.refuseFocus(set.fields) match
        case Some(word) => effects.error(s"the vocabulary has no $word", ErrorCode.BadRequest)
        case None =>
          effects
            .updateState(
              Focus(context.entityId, set.device, set.fields, set.at, currentState.version + 1)
            )
            .thenReplyState

  def get: ReadOnlyEffect[Focus] = effects.reply(currentState)

/**
 * What a consumer extends to register the focus beside its surfaces: `object Focuses extends
 * SurfaceFocusCompanion(ComponentId("surface-focus"), vocabulary)`.
 */
abstract class SurfaceFocusCompanion(componentId: ComponentId, val vocabulary: Vocabulary)
    extends KeyValueEntity.Companion[SurfaceFocusEntity, Focus](
      componentId = componentId,
      stateSerializer = Codecs.serializer[Focus]("surface-focus")
    ):

  given Serializer[SetFocus] = Codecs.serializer[SetFocus]("surface-set-focus")

  def create(context: KeyValueEntityContext) = new SurfaceFocusEntity(context, vocabulary)

  val set = command("set")(_.set)
  val get = query("get")(_.get)

/** One row per focus, the latest value: what a device streams. */
final class SurfaceFocusRowsView extends View[Focus, Focus]:
  def onChange(focus: Focus): Effect = effects.updateRow(focus)

/**
 * What a consumer extends to register the rows of its focuses: `object FocusRows extends
 * SurfaceFocusRowsCompanion(ComponentId("surface-focus-rows"), Focuses)`.
 */
abstract class SurfaceFocusRowsCompanion(componentId: ComponentId, focuses: SurfaceFocusCompanion)
    extends View.Companion[SurfaceFocusRowsView, Focus, Focus](
      componentId = componentId,
      source = ChangeSource.stateOf(focuses),
      rowSerializer = Codecs.serializer[Focus]("surface-focus-row")
    ):
  def create(ctx: ViewComponentContext) = new SurfaceFocusRowsView

/** The two components of the focus lane, handed to the endpoint together. */
final case class FocusLane(focuses: SurfaceFocusCompanion, rows: SurfaceFocusRowsCompanion)
