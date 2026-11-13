# The surface over HTTP

The contract both halves build against: the Scala module serves it, the device package speaks it,
and a renderer in any language may read it. Field names are the JSON; `null` and an absent field
read the same. Times are ISO-8601 in UTC. A consumer mounts the endpoint under a prefix of its
choosing; every path below is relative to it.

## Records

```
Anchor    { kind: string, id: string }
Pending   { kind: string, fields: { [name: string]: string } }
RailEntry { text: string, shape: string, anchor: Anchor | null, modifiers: { [key: string]: string }, at: string }
Showing   { device: string, deviceClass: string, room: boolean, since: string }
Surface   { id: string, owner: string, opened: boolean, shape: string, anchor: Anchor | null,
            modifiers: { [key: string]: string }, pending: Pending | null,
            rail: RailEntry[], showing: Showing[], version: number }
```

- `version` rises by one on every change the surface records, and never falls. A renderer holding a
  surface replaces it with one of a higher version and ignores one of a lower.
- `rail` holds the last requests, newest first, each with the shape, anchor and modifiers it left
  the surface in; its length is the consumer's vocabulary's `railLength` (3 unless it says).
- `showing` lists the devices showing the surface now; `room` is true for a device in a shared room,
  which a renderer honours by hiding what is personal.
- `opened` is false for a surface nobody has opened; every other field is then empty.

## Bodies

```
Open  { owner: string, shape?: string }
Move  { shape?: string, anchor?: Anchor, clearAnchor?: boolean,
        modifiers?: { [key: string]: string }, clearModifiers?: string[],
        pending?: Pending, clearPending?: boolean, request?: string }
Say   { text: string }
Show  { device: string, deviceClass: string, room?: boolean }
Hide  { device: string }
Said  { understood: boolean, reason: string | null, surface: Surface }
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
| `POST /{id}/show` | `Show` | `200` `Surface` | `409` not opened; `400` a device class the vocabulary lacks |
| `POST /{id}/hide` | `Hide` | `200` `Surface` | `409` not opened |
| `DELETE /{id}` | | `204`, the surface closed and its id free | `409` not opened |

`/say` hands the text to the consumer's interpreter with the surface as it stands. Understood, the
interpreter's move is applied with the text as its `request` and `Said.understood` is true; not
understood, nothing changes, `Said.understood` is false and `Said.reason` says why, in words a
renderer may show. An interpreter never acts past the surface: a forecast it hears becomes
`pending`, and committing it is the consumer's own route.

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
