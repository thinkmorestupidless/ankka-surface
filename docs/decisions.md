# Decisions

The in-repo log. Numbered as the family numbers its own.

## 0001 — A surface is held on the server, and it is a library (2026-11-13)

**Context.** Trading Pit's design (`tradingpit-design/DESIGN.md`) wanted an interface that does not
navigate: one surface whose content region takes whatever shape answers the last request, steered
by voice, typing or a tap, shown on a phone, a laptop or a television at once, and picked up on
another device where it was left. Where the representation of "what is shown now" lives decides
whether any of that is possible. Four other products in the family (a civic conversation, a
village food counter, a trades office, a company's state) want the same thing.

**Decision.**
1. The representation is a record on the server, `Surface`, held as an event sourced entity. Every
   device is a renderer of it and, optionally, a steerer of it. A person has one by default.
2. It is event sourced, not a value, because its history is what the consumer's own projections
   want (what was asked, what was looked at) and the current state is a few hundred bytes.
3. It is a library on ankka's SDK, in a repository of its own from the first line, because four
   consumers are in sight and a layer extracted after its first consumer carries that consumer's
   assumptions. The platform deliberately owns nothing above its entities and streams (ankka spec
   028 leaves presence and the like to a service), so this is a layer, not a platform feature.
4. The consumer declares a vocabulary and the entity refuses what it lacks, rather than the library
   being typed by the consumer's own case classes: one JSON for every consumer, one device package,
   one contract.
5. The interpreter is a seam the library ships empty. An interpreter never acts past the surface:
   an act is `pending` until the consumer's own route commits it, so a misheard "yes, seventy"
   lands as a mark on the screen and not as money.
6. The device applies its own move locally before the server answers, and accepts a streamed
   surface only at a higher version; a `say` is never applied locally.

**Alternatives.** Hold the state on the device and sync it: lost, because a television steered
from a phone and a hand-off between devices both need a third party, and the server is already
one. A generic entity typed by the consumer's classes: lost, because the device half and the
contract would then be per consumer. A WebSocket both ways: lost for now, because server-sent
events down and HTTP up are what the platform serves today and are enough; a socket is a later
addition behind the same device API.

**Consequences.**
- The first consumer is Trading Pit (`tradingpit-design`); the second is whoever comes next, and
  until there is one the library's API is provisional.
- Nothing is asked of ankka. If a second consumer needs a socket, spec 028's socket route is there.
- Authentication of the owner is the consumer's, through the endpoint's ACL; the library holds the
  owner's name and nothing else about them.
- A second person steering one surface is not designed. One person, many devices.

## 0002 — Focus is a second lane, and words are heard before they are said (2026-11-13)

**Context.** Trading Pit's first measurements put the spoken path's time in the recogniser's wait
for silence and in the device's draw after the answer. And a device in a room had no way to follow
where the attention was on the device steering it, short of a move per finger movement.

**Decision.**
1. Focus is a key value entity beside the surface, not a part of it. It keeps its latest value and
   no history, so a thumb crossing a shelf costs one row write per throttle window and the journal
   nothing. ankka delivers a key value entity's latest state and may skip intermediates, which is
   the semantics focus wants. Its keys are the vocabulary's, as the surface's words are.
2. A device hears before it says. `/hear` is the interpreter alone, nothing persisted, so partial
   words and uncertain alternatives can be tried; a `say` carries the utterance they came from, and
   the rail keeps one entry per utterance.
3. The newest say wins on the device. Nothing blocks the prompt; an older answer arriving later is
   returned and not held.
4. The package exports the types of an ear and ships none: recognisers are the browser's or the
   consumer's.

**Alternatives.** Focus as a modifier on the surface: lost, a version per finger movement. A
non-durable broadcast beside ankka: lost, there is none in the platform and the key value entity
is cheap enough. Partials applied only locally: kept as an option a consumer may take with `/hear`,
but the room device then does not follow, and following is the point.

**Consequences.** Two more components in a consumer that wants the lane, registered beside the
surface's two. The device's stream count doubles. A consumer's interpreter should be prefix-safe
if it hears partials: each prefix a step towards the whole.
