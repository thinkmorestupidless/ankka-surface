# 002: hearing and focus

*Status: Draft, 2026-11-13.*

## Why

The first consumer measured where a spoken request's time goes: almost all of it is the recogniser
waiting for silence, and the rest is the device drawing after the surface has answered. Nothing
can be done about that while the surface only hears whole, final sentences. And a second thing
had no home: where the attention is on a device that is not steering, the card under a thumb that
a television in the room should enlarge. Journaling that as moves would bump the version at the
rate of a finger.

## Requirements

- **R1** A move may name the utterance it came from. The fold puts a request whose utterance is
  the rail's head entry's in that entry's place rather than pushing; a request with no utterance,
  or a different one, pushes. The entity, the view and the device fold the same way.
- **R2** `POST /{id}/hear` answers the interpreter's move for the words, as `Heard`, with nothing
  persisted. It refuses an empty text and a surface nobody opened.
- **R3** `Say` may carry the utterance; `/say` passes it to the move.
- **R4** The device package holds the newest say as the one that counts, has `hear`, and exports
  the types of an ear (partial, final with alternatives, end) without shipping one.
- **R5** A focus is a key value entity beside each surface, keyed by the surface's id: the device
  that set it, its fields, the time, and a version of its own. Its keys are the vocabulary's
  `focusKeys`; a key the vocabulary lacks is refused. Empty fields clear it.
- **R6** A view keeps the latest focus per surface; the endpoint serves `GET`, `PUT` and a stream
  of it under the surface's routes, each refused for a surface nobody opened. A consumer that
  registers no focus lane has none of the routes.
- **R7** The device package, with the lane on, streams the focus beside the surface, applies its
  own focus at once, sends it at most once per interval with the last value in a window always
  sent, and accepts a streamed focus only at a higher version.
- **R8** Nothing here names a product.

## Not in this feature

A streaming recogniser, phrase biasing, a wake word, and any ear implementation: the consumer's.
Several people steering one surface.
