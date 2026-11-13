import type { Move, RailEntry, Surface } from "./types.ts";

/**
 * Applies a move to a surface the way the contract says: shape, then anchor (`clearAnchor` wins),
 * then modifiers (each `clearModifiers` key removed, each `modifiers` entry set), then pending
 * (`clearPending` wins), then the request put on the rail with the surface as it then stands.
 *
 * The same fold the server applies, so a device can show its own move before the server answers.
 * It leaves `version` alone: the server owns versions. Pure: returns a new surface and never
 * mutates its input. Throws on a move that names nothing.
 */
export function applyMove(surface: Surface, move: Move, at: string, railLength = 3): Surface {
  if (namesNothing(move)) throw new Error("a move must name at least one part");

  const shape = move.shape ?? surface.shape;

  const anchor = move.clearAnchor ? null : move.anchor ?? surface.anchor;

  const modifiers = { ...surface.modifiers };
  for (const key of move.clearModifiers ?? []) delete modifiers[key];
  Object.assign(modifiers, move.modifiers ?? {});

  const pending = move.clearPending ? null : move.pending ?? surface.pending;

  let rail = surface.rail;
  if (move.request !== undefined) {
    const entry: RailEntry = { text: move.request, shape, anchor, modifiers: { ...modifiers }, at, utterance: move.utterance ?? null };
    // A later move of the same utterance replaces the entry the earlier one made.
    const head = surface.rail[0];
    const rest = move.utterance !== undefined && head !== undefined && head.utterance === move.utterance ? surface.rail.slice(1) : surface.rail;
    rail = [entry, ...rest].slice(0, railLength);
  }

  return { ...surface, shape, anchor, modifiers, pending, rail };
}

function namesNothing(move: Move): boolean {
  return (
    move.shape === undefined &&
    move.anchor === undefined &&
    !move.clearAnchor &&
    Object.keys(move.modifiers ?? {}).length === 0 &&
    (move.clearModifiers ?? []).length === 0 &&
    move.pending === undefined &&
    !move.clearPending &&
    move.request === undefined
  );
}
