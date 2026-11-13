package com.thinkmorestupidless.ankka.surface

import com.thinkmorestupidless.ankka.core.{Codecs, ComponentId}
import com.thinkmorestupidless.ankka.sdk.*

/**
 * One row per surface, the whole surface, keyed by its id: what a device streams, and what lists a
 * person's surfaces. The fold is the entity's, so a row is what the entity would answer.
 */
final class SurfaceRowsView(railLength: Int) extends View[SurfaceEvent, Surface]:

  def onChange(event: SurfaceEvent): Effect = event match
    // The deletion that follows removes the row.
    case SurfaceEvent.Closed(_) => effects.ignore()
    case other =>
      val current = rowState.getOrElse(Surface.empty(updateContext.subject))
      effects.updateRow(current.applyEvent(other, railLength))

/**
 * What a consumer extends to register the rows of its surfaces: `object SurfaceRows extends
 * SurfaceRowsCompanion(ComponentId("surface-rows"), Surfaces)`.
 */
abstract class SurfaceRowsCompanion(componentId: ComponentId, surfaces: SurfaceCompanion)
    extends View.Companion[SurfaceRowsView, SurfaceEvent, Surface](
      componentId = componentId,
      source = ChangeSource.eventsOf(surfaces),
      rowSerializer = Codecs.serializer[Surface]("surface-row")
    ):

  /** An owner's opened surfaces. */
  val byOwner = query("by-owner")(
    s"SELECT row_key, payload FROM $table WHERE payload::jsonb->>'owner' = :owner"
  )

  def create(ctx: ViewComponentContext) = new SurfaceRowsView(surfaces.vocabulary.railLength)
