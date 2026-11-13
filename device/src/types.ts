// The records and bodies of specs/001-surface/contracts/surface-http.md, as the JSON is.
// `null` and an absent field read the same on the wire; the types say `| null` where the
// contract does and `?` where the body may leave a part out.

/** The thing the shape is about, by kind and id. The vocabulary names the kinds. */
export type Anchor = { kind: string; id: string };

/** An act begun and not committed; committing it is the consumer's own route. */
export type Pending = { kind: string; fields: { [name: string]: string } };

/** One request on the rail, with the surface it left: the way back. */
export type RailEntry = {
  text: string;
  shape: string;
  anchor: Anchor | null;
  modifiers: { [key: string]: string };
  at: string;
  /** The utterance the request came from, when the device said; one entry per utterance. */
  utterance?: string | null;
};

/** A device's record on the surface. */
export type Showing = { device: string; deviceClass: string; room: boolean; since: string };

/** What a person last asked for. `opened` false means every other field is empty. */
export type Surface = {
  id: string;
  owner: string;
  opened: boolean;
  shape: string;
  anchor: Anchor | null;
  modifiers: { [key: string]: string };
  pending: Pending | null;
  rail: RailEntry[];
  showing: Showing[];
  version: number;
};

export type Open = { owner: string; shape?: string };

/** One change with as many parts as it names, applied together; one naming nothing is refused. */
export type Move = {
  shape?: string;
  anchor?: Anchor;
  clearAnchor?: boolean;
  modifiers?: { [key: string]: string };
  clearModifiers?: string[];
  pending?: Pending;
  clearPending?: boolean;
  request?: string;
  /** Names the utterance a request came from, so its moves share one rail entry. */
  utterance?: string;
};

export type Say = { text: string; utterance?: string };

export type Show = { device: string; deviceClass: string; room?: boolean };

export type Hide = { device: string };

/** The interpreter's answer to a `say`: not understood, nothing changed and `reason` says why. */
export type Said = { understood: boolean; reason: string | null; surface: Surface };

/** The interpreter's answer to a `hear`: the move it would make, with nothing persisted. */
export type Heard = { understood: boolean; reason: string | null; move: Move | null };

/**
 * Where the attention is, beside the surface: the card under a thumb, the outcome a finger rests
 * on. The latest value only; `fields` empty means nothing is focused.
 */
export type Focus = { id: string; device: string; fields: { [key: string]: string }; at: string; version: number };

export type SetFocus = { device: string; fields: { [key: string]: string } };

/** What an ear emits: words as they are heard, the final words, or the end of listening. */
export type Hearing =
  | { kind: "partial"; text: string }
  | { kind: "final"; text: string; alternatives: string[] }
  | { kind: "end" };

/**
 * The listening seam. The package ships no ear: a consumer wraps the browser's recogniser, or a
 * streaming one, and emits hearings; a device hands partials to `hear` or `say` with an utterance.
 */
export type Ear = { start(): void; stop(): void; abort(): void };

/**
 * How a device renders, named by the vocabulary. "phone", "laptop" and "tv" are the usual
 * classes; a vocabulary may name others.
 */
export type DeviceClass = string;

/**
 * The render seam. A renderer takes the surface, the device's class and whether the device is in
 * a room (and so hides what is personal), and makes whatever the device shows. The package ships
 * no renderer.
 */
export type Renderer<T> = (surface: Surface, deviceClass: DeviceClass, room: boolean) => T;
