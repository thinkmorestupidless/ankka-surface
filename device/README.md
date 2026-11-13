# ankka-surface (device)

The device half of a surface: the model a renderer reads and the calls a steering device makes.
A surface is what a person last asked for, held on the server; this package holds one surface on
a device, keeps it current from the server's stream, applies the device's own moves at once so the
person sees them before the server answers, and tells a renderer every time it changes. It speaks
the contract in `specs/001-surface/contracts/surface-http.md` and nothing else.

ESM, TypeScript, no runtime dependencies, no framework. Runs in a browser; tests run on Node 24.

## Install

```
npm install ankka-surface
```

## API

| Export | What |
|---|---|
| `SurfaceModel` | one surface on one device: `connect`, `disconnect`, `open`, `move`, `say`, `show`, `hide`, `close`, `subscribe`, and the `surface` and `connected` getters |
| `applyMove(surface, move, at, railLength?)` | the fold the server applies, pure; throws on a move that names nothing |
| `SurfaceError` | a refusal from the server, with `status` and `body` |
| `Surface`, `Move`, `Said`, `Anchor`, `Pending`, `RailEntry`, `Showing`, `Open`, `Say`, `Show`, `Hide` | the contract's records and bodies |
| `DeviceClass`, `Renderer<T>` | how a device renders, and the seam a renderer fills |
| `Source`, `Listener`, `SurfaceModelOptions` | the types `subscribe` and the constructor take |
| `eventData(event)` | reads a server-sent event's data as JSON |

`SurfaceModel` takes `baseUrl` (the prefix the endpoint is mounted under), `id`, `device`,
`deviceClass` and optionally `room`, `railLength` (3 unless the vocabulary says), `fetch` and
`EventSource` (the globals by default; tests inject fakes), `reconnectDelayMs` (1000) and `now`.

`connect` fetches the surface, opens the stream and posts `show`; a surface never opened leaves
`surface` null, so `open` it. `move` is applied locally first and replaced by the server's answer; a
refusal restores what was held and throws. `say` is not applied locally. A streamed row is taken
only when its version is higher than the one held. When the server ends the stream the model
fetches the surface and streams again after `reconnectDelayMs`.

## Usage

```ts
import { SurfaceModel } from "ankka-surface";
import type { Surface } from "ankka-surface";

function render(surface: Surface | null, deviceClass: string, room: boolean): void {
  const root = document.getElementById("app")!;
  if (surface === null) { root.textContent = "nothing to show"; return; }
  root.textContent = `${surface.shape} ${surface.anchor?.id ?? ""} (${deviceClass}${room ? ", room" : ""})`;
}

const model = new SurfaceModel({ baseUrl: "/api/surfaces", id: "ada", device: "kitchen-tv", deviceClass: "tv", room: true });
model.subscribe((surface) => render(surface, "tv", true));
await model.connect();
if (model.surface === null) await model.open("ada");

await model.move({ shape: "item", anchor: { kind: "market", id: "m1" }, request: "show me the market" });
const said = await model.say("sort by newest");
if (!said.understood) console.log(said.reason);
window.addEventListener("pagehide", () => { void model.disconnect(); });
```

## What it does not do

The package ships no renderer and no framework. `Renderer<T>` names the seam; a device fills it
with whatever it draws with. The server half, in Scala, is the `surface` module beside this one.

## Developing

`npm run typecheck`, `npm test` (Node 24 runs the `.ts` tests directly), `npm run build` (to
`dist/`). The version in `package.json` is `0.0.0`; a release job rewrites it from the tag.
