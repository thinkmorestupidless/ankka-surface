# Feature Specification: The surface

**Feature Branch**: `001-surface`
**Created**: 2026-11-13
**Status**: Draft (the tick count in `tasks.md` is the real status)

## What it is

A surface is the representation of what a person last asked for: a shape, an anchor, modifiers, a
pending act, a rail of the last requests, the devices showing it, and a version. It is held on the
server as an event sourced entity, so that every device a person has renders the same thing and any
of them can change it, and so that moving from one device to another is opening the app. The
library holds what every consumer needs and nothing of any one product: the entity, a view of its
rows, an endpoint that serves and streams them, the seam an interpreter plugs into, and the
vocabulary a consumer declares. The device half, in TypeScript with no framework, holds the model a
renderer reads and the calls a steering device makes.

Why it exists is `../../docs/decisions.md` 0001; the shape of the product that asked for it is
`tradingpit-design/DESIGN.md`, "The surface".

## Scenarios

The living scenarios are `features/surface/surface.feature`, in the words of `GLOSSARY.md`. This spec
names them and adds the requirements they do not state.

## Requirements

- **R1** A surface is an event sourced entity whose state is `Surface` as `contracts/surface-http.md`
  gives it, and whose events are a closed set: opened, shape changed, anchor changed, modifier set,
  modifier cleared, pending set, pending cleared, requested, showing started, showing stopped,
  closed. Each event carries the time the command gave it; the fold reads no clock.
- **R2** The consumer declares a `Vocabulary`: shapes, anchor kinds, modifier keys, pending kinds,
  device classes, the home shape and the rail's length. The entity refuses, before persisting, any
  part of a move the vocabulary lacks, as a bad request naming the word.
- **R3** A move is one command with as many parts as it names, applied as one effect, and refused
  whole when any part is refused. A move naming nothing is refused.
- **R4** The version rises by one per event and is in the state, so a renderer orders what it is
  given without the journal.
- **R5** A view keeps one row per surface, the whole `Surface`, keyed by the surface id, and
  declares a query by owner.
- **R6** An endpoint serves the routes in `contracts/surface-http.md` under the prefix the consumer
  gives, with the ACL the consumer gives, and stamps each command's time from a clock the consumer
  may replace.
- **R7** The stream is the view row watched, as server-sent events, with ankka's own event names.
- **R8** The interpreter is a seam: `Interpreter` takes the surface and the text and answers a
  move or a reason. The library ships none and the endpoint refuses to start without one.
- **R9** The device package holds the same fold as the entity for the parts of a move, applies a
  device's own move locally before the server answers, accepts a streamed surface only at a higher
  version, fetches the surface before every stream open, and shows and hides itself.
- **R10** Nothing in the library names a product, a shape, an anchor kind or a modifier key.

## Not in this feature

- A second person steering one surface; a surface has one owner.
- Authentication of the owner: the ACL is the consumer's, and the owner is whatever the consumer's
  open command says.
- The interpreter itself, in any form; an agent that interprets is a consumer's component.
- Persistence on the device beyond what it holds in memory.
