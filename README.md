# ankka-surface

A **surface** is the representation of what a person last asked for: a shape, the thing it is
about, a few modifiers, an act begun and not committed, the last requests, and the devices showing
it. It is held on the server as an [ankka](https://github.com/thinkmorestupidless/ankka) event
sourced entity, so that every device a person has renders the same thing, any of them can change
it, and moving from a laptop to a phone to a television is opening the app. A device renders the
surface and steers it; the server interprets what the person said and holds the result.

Two halves, one contract (`specs/001-surface/contracts/surface-http.md`):

| Half | Artifact | What it holds |
|---|---|---|
| `surface/` | `com.thinkmorestupidless:ankka-surface_3` on Maven Central | the entity, a view of its rows, the endpoint that serves and streams them, the interpreter seam, and the vocabulary a consumer declares |
| `device/` | `ankka-surface` on npm | the model a renderer reads and the calls a steering device makes; TypeScript, no framework, no runtime dependency |

Nothing in either half names a product, a shape, an anchor kind or a modifier key. Those are the
consumer's, declared as a vocabulary, and the entity refuses a move that uses a word the vocabulary
lacks.

## In a service

```scala
import com.thinkmorestupidless.ankka.core.ComponentId
import com.thinkmorestupidless.ankka.http.Acl
import com.thinkmorestupidless.ankka.surface.*

val vocabulary = Vocabulary(
  shapes = Set("home", "shelf", "item", "why", "forecast", "people", "result", "me"),
  homeShape = "home",
  anchorKinds = Set("theme", "market", "person"),
  modifierKeys = Set("time", "order", "stance"),
  pendingKinds = Set("forecast")
)

object Surfaces    extends SurfaceCompanion(ComponentId("surface"), vocabulary)
object SurfaceRows extends SurfaceRowsCompanion(ComponentId("surface-rows"), Surfaces)
// Optional: the focus lane, where the attention is beside what was asked for.
object Focuses     extends SurfaceFocusCompanion(ComponentId("surface-focus"), vocabulary)
object FocusRows   extends SurfaceFocusRowsCompanion(ComponentId("surface-focus-rows"), Focuses)

object Interpreter extends com.thinkmorestupidless.ankka.surface.Interpreter:
  def interpret(surface: Surface, text: String): Interpretation = ...

Ankka.service
  .register(Surfaces.descriptor)
  .register(SurfaceRows.descriptor)
  .register(Focuses.descriptor)
  .register(FocusRows.descriptor)
  .withExtension(ProjectionRuntime())
  .withExtension(HttpServer.of(clients =>
    SurfaceEndpoint("/surfaces", Acl.AllowAll, clients.componentClient, clients.viewClient,
      Surfaces, SurfaceRows, Interpreter, focus = Some(FocusLane(Focuses, FocusRows)))
  ))
  .start()
```

The endpoint serves `GET /surfaces/{id}`, streams `GET /surfaces/{id}/stream` as server-sent
events, and takes `open`, `moves`, `say`, `hear`, `show`, `hide` and a `DELETE`; with the focus
lane, `GET`, `PUT` and a stream of `/surfaces/{id}/focus`. The contract lists every route, body
and refusal. `hear` is the interpreter alone, nothing persisted, for words a device is not sure
of or has not finished hearing; a `say` names the utterance its words came from, and the rail
keeps one entry per utterance. Focus is where the attention is, beside the surface: a key value
entity, the latest value only, for a room device to follow a thumb. The interpreter is a seam: from the surface as it stands and the words a
person said, a move, or a reason there is none. It never acts past the surface: an act it hears
becomes `pending`, and committing it is the consumer's own route.

## On a device

```ts
import { SurfaceModel } from "ankka-surface";

const model = new SurfaceModel({ baseUrl: "/api/surfaces", id: "trevor", device: "phone-1", deviceClass: "phone" });
model.subscribe((surface) => render(surface));
await model.connect();
await model.say("anything about Newcastle");
```

`device/README.md` has the rest. The device applies its own moves locally before the server
answers, accepts a streamed surface only at a higher version, fetches the surface before every
stream it opens, and shows and hides itself.

## Build and test

```bash
sbt test                 # the Scala module; the integration suite starts Postgres in Docker
cd device && npm ci && npm run typecheck && npm test
just hooks               # the commit hooks, once per clone
```

Built against ankka `0.16.3` (`V.ankka` in `project/Dependencies.scala`); `sbt -Dankka.version=<v>`
builds against another. Until there is a release, a consumer on this machine takes it from
`~/.ivy2/local` at a version it can name:

```bash
sbt 'set ThisBuild / version := "0.1.0-SNAPSHOT"' publishLocal
cd device && npm run build       # a `file:` dependency reads dist/
```

## Where things are

- `specs/001-surface/`: the spec, its tasks and the HTTP contract. The living scenarios are
  `features/surface/surface.feature`, in the words of `GLOSSARY.md`.
- `docs/decisions.md`: why a surface is held on the server, why it is a library, and what it is
  not.
- The first consumer is Trading Pit, in `tradingpit-design`, whose `DESIGN.md` says what asked for
  this.
