# CLAUDE.md

ankka-surface: a surface, the representation of what a person last asked for, as a library on
ankka's published SDK (`surface/`, Scala) with a device package beside it (`device/`, TypeScript).
The contract both halves build against is `specs/001-surface/contracts/surface-http.md`; the
living scenarios are `features/surface/surface.feature`, in the words of `GLOSSARY.md`; the
decisions are `docs/decisions.md`. Read the family's `../ankka-brain/conventions.md` before
writing code, and ankka's own docs (`../ankka/docs/`, or the `ankka` skills) for the SDK: the
samples there are compiled and tested against the version in `project/Dependencies.scala`.

## Principles

- **Nothing here names a product.** Shapes, anchor kinds, modifier keys and pending kinds are the
  consumer's vocabulary. A word of any product's in this repository is a defect.
- **The surface never acts.** A move changes what is shown; an act a person begins is `pending`
  until the consumer's own route commits it. The interpreter is a seam, and the library ships none.
- **The fold is the contract.** The entity, the view and the device apply a move the same way, in
  the order the contract gives; a change to the order changes all three and the contract.
- **Effects are inert data; the fold reads no clock.** Every command carries its time, stamped by
  the endpoint from a clock the consumer may replace.
- **Could this check pass while the thing it checks is false?** Ask it of every test; break the
  behaviour once and watch it go red.

## Commands

```bash
sbt test                                   # every Scala suite; the HTTP suite starts Postgres in Docker
sbt scalafmtAll scalafmtSbt                # format
cd device && npm run typecheck && npm test # the device package
just hooks                                 # the commit hooks, once per clone
```

Commit messages are Conventional Commits (`.githooks/commit-msg`); the scope is the spec number for
spec work, `feat(001): ...`. Pull request bodies follow `.github/pull_request_template.md`.

## Traps

- A watched row may be given twice at the same version; a reader keeps the highest version it has
  seen and ignores the rest (`SurfaceHttpSuite`, the stream scenario). (2026-11-13)
- A server-sent event's `event:` and `data:` lines come in either order; read an event up to its
  blank line. (2026-11-13)
- Node runs a `.ts` test directly only when `src` files import each other with `.ts` specifiers;
  `tsc` rewrites them to `.js` on emit (`rewriteRelativeImportExtensions`). (2026-11-13)
- A browser's `fetch` must be called on the window: holding `globalThis.fetch` in a field and calling
  it throws "Illegal invocation" in Chrome, and Node's tests never see it because Node's fetch does
  not care. The model wraps it in a closure. (2026-11-13)
