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
