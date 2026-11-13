# The surface over HTTP

The contract both halves build against: the Scala module serves it, the device package speaks it,
and a renderer in any language may read it. Field names are the JSON; `null` and an absent field
read the same. Times are ISO-8601 in UTC. A consumer mounts the endpoint under a prefix of its
choosing; every path below is relative to it.

## Records

```
Anchor    { kind: string, id: string }
Pending   { kind: string, fields: { [name: string]: string } }
RailEntry { text: string, shape: string, anchor: Anchor | null, modifiers: { [key: string]: string }, at: string,
            utterance: string | null }
Showing   { device: string, deviceClass: string, room: boolean, since: string }
Surface   { id: string, owner: string, opened: boolean, shape: string, anchor: Anchor | null,
            modifiers: { [key: string]: string }, pending: Pending | null,
            rail: RailEntry[], showing: Showing[], version: number }
Focus     { id: string, device: string, fields: { [key: string]: string }, at: string, version: number }
```

- `version` rises by one on every change the surface records, and never falls. A renderer holding a
  surface replaces it with one of a higher version and ignores one of a lower.
- `rail` holds the last requests, newest first, each with the shape, anchor and modifiers it left
  the surface in; its length is the consumer's vocabulary's `railLength` (3 unless it says). The
  moves made from one utterance, while it is still being heard, share one entry: a request whose
  `utterance` is the head entry's replaces it rather than pushing.
- `Focus` is where the attention is, beside the surface and apart from it: the card under a thumb,
  the outcome a finger rests on. The latest value only, with a version of its own; `fields` empty
  means nothing is focused. Its keys are the vocabulary's `focusKeys`. A consumer that registers no
  focus lane has none of its routes.
- `showing` lists the devices showing the surface now; `room` is true for a device in a shared room,
  which a renderer honours by hiding what is personal.
- `opened` is false for a surface nobody has opened; every other field is then empty.

## Bodies

```
Open  { owner: string, shape?: string }
Move  { shape?: string, anchor?: Anchor, clearAnchor?: boolean,
        modifiers?: { [key: string]: string }, clearModifiers?: string[],
        pending?: Pending, clearPending?: boolean, request?: string, utterance?: string }
Say   { text: string, utterance?: string }
Show  { device: string, deviceClass: string, room?: boolean }
Hide  { device: string }
Said  { understood: boolean, reason: string | null, surface: Surface }
Heard { understood: boolean, reason: string | null, move: Move | null }
SetFocus { device: string, fields: { [key: string]: string } }
```

A `Move` is one change with as many parts as it names, applied together: the shape, then the
anchor (`clearAnchor` wins over `anchor`), then each `clearModifiers` key removed and each
`modifiers` entry set, then the pending (`clearPending` wins over `pending`), then the request put
on the rail with the surface as it then stands. A move that names nothing is refused.

## Routes

| Route | Body | Answers | Refuses |
|---|---|---|---|
| `GET /{id}` | | `200` `Surface` | `404` when never opened |
| `GET /{id}/stream` | | `text/event-stream`, below | `404` when never opened |
| `GET /?owner=<owner>` | | `200` `Surface[]`, the owner's opened surfaces | `400` with no `owner` |
| `POST /{id}/open` | `Open` | `200` `Surface` | `409` already opened; `400` a shape the vocabulary lacks |
| `POST /{id}/moves` | `Move` | `200` `Surface` | `409` not opened; `400` a shape, anchor kind, modifier key or pending kind the vocabulary lacks, or a move naming nothing |
| `POST /{id}/say` | `Say` | `200` `Said` | `409` not opened; `400` empty text |
| `POST /{id}/hear` | `Say` | `200` `Heard`, nothing persisted | `404` not opened; `400` empty text |
| `GET /{id}/focus` | | `200` `Focus`, empty at version 0 when never set | `404` surface never opened |
| `PUT /{id}/focus` | `SetFocus` | `200` `Focus` | `404` surface never opened; `400` a focus key the vocabulary lacks |
| `GET /{id}/focus/stream` | | `text/event-stream` of `Focus` rows, as the surface's stream | `404` surface never opened |
| `POST /{id}/show` | `Show` | `200` `Surface` | `409` not opened; `400` a device class the vocabulary lacks |
| `POST /{id}/hide` | `Hide` | `200` `Surface` | `409` not opened |
| `DELETE /{id}` | | `204`, the surface closed and its id free | `409` not opened |

`/say` hands the text to the consumer's interpreter with the surface as it stands. Understood, the
interpreter's move is applied with the text as its `request` and `Said.understood` is true; not
understood, nothing changes, `Said.understood` is false and `Said.reason` says why, in words a
renderer may show. An interpreter never acts past the surface: a forecast it hears becomes
`pending`, and committing it is the consumer's own route. `/hear` is the interpreter alone: a
device tries words it is not sure of, or the words so far, and gets the move it would make, with
nothing persisted; `Say.utterance` on a later `/say` keeps the rail to one entry per utterance.

## The stream

Server-sent events, as ankka serves a watched view row:

| Event | Data | When |
|---|---|---|
| `row` | `{ "key": id, "row": Surface }` | the surface now, then every version written while the stream is open, coalesced: never an older version after a newer, possibly fewer than were written |
| `caught-up` | `{}` | once, after the first `row` |
| `removed` | `{ "key": id }` | the surface was closed |
| `ended` | `{ "reason": string }` | the server ended the stream (`rebuilt`, `instance-stopping`, `listener-lost`, `unread`); a device reconnects and is given the surface now |

A device that reconnects for any reason fetches `GET /{id}` first, so a change made while it was
away is never missed, and then opens the stream again.

## What a device does

- Holds one surface and its version.
- Applies a move it makes itself at once, locally, by the same fold the server applies, so the
  person sees the change before the server answers; replaces it with the server's answer, whose
  version is higher. A `say` is not applied locally: the device cannot know what the interpreter
  will make of it.
- Accepts a streamed surface only when its version is higher than the one held.
- Calls `show` after connecting and `hide` before disconnecting, so the surface knows what is
  showing it.
- Holds the newest `say` as the one that counts: the answer to an older say, arriving later, is
  returned to its caller but not held.
- With the focus lane on, streams `GET /{id}/focus/stream` beside the surface, applies its own
  focus at once, and sends it at most once per interval with the last value in a window always
  sent; accepts a streamed focus only at a higher version.
