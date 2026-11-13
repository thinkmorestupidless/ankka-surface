import { test } from "node:test";
import assert from "node:assert/strict";
import { SurfaceError, SurfaceModel } from "../src/index.ts";
import type { Said, Surface } from "../src/index.ts";
import { FakeEventSource, asEventSource, fakeFetch, surface, tick } from "./fakes.ts";
import type { Answer } from "./fakes.ts";

const base = "/api/surfaces";

function model(script: Answer[], extra: Partial<ConstructorParameters<typeof SurfaceModel>[0]> = {}) {
  FakeEventSource.reset();
  const { fetch, calls } = fakeFetch(script);
  const m = new SurfaceModel({
    baseUrl: base,
    id: "s1",
    device: "d1",
    deviceClass: "phone",
    room: false,
    fetch,
    EventSource: asEventSource,
    reconnectDelayMs: 0,
    now: () => "2026-11-13T09:00:00Z",
    ...extra,
  });
  return { m, calls };
}

test("connect fetches, opens the stream, and shows the device", async () => {
  const { m, calls } = model([
    { status: 200, body: surface({ version: 3 }) },
    { status: 200, body: surface({ version: 4, showing: [{ device: "d1", deviceClass: "phone", room: false, since: "t" }] }) },
  ]);
  await m.connect();

  assert.deepEqual(calls[0], { url: `${base}/s1`, method: "GET", body: undefined });
  assert.equal(FakeEventSource.last.url, `${base}/s1/stream`);
  assert.deepEqual(calls[1], { url: `${base}/s1/show`, method: "POST", body: { device: "d1", deviceClass: "phone", room: false } });
  assert.equal(m.connected, true);
  assert.equal(m.surface?.version, 4);
});

test("connect on a surface never opened leaves surface null and opens nothing", async () => {
  const { m, calls } = model([{ status: 404, body: "not found" }]);
  await m.connect();
  assert.equal(m.surface, null);
  assert.equal(m.connected, false);
  assert.equal(calls.length, 1);
  assert.equal(FakeEventSource.instances.length, 0);
});

test("a streamed row with a higher version replaces the surface; a lower one is ignored", async () => {
  const { m } = model([{ status: 200, body: surface({ version: 3 }) }, { status: 200, body: surface({ version: 3 }) }]);
  const seen: Array<[number | undefined, string]> = [];
  m.subscribe((s, source) => seen.push([s?.version, source]));
  await m.connect();

  FakeEventSource.last.emit("row", { key: "s1", row: surface({ version: 5, shape: "item" }) });
  assert.equal(m.surface?.version, 5);
  assert.equal(m.surface?.shape, "item");

  FakeEventSource.last.emit("row", { key: "s1", row: surface({ version: 4, shape: "shelf" }) });
  assert.equal(m.surface?.version, 5);
  assert.equal(m.surface?.shape, "item");

  FakeEventSource.last.emit("caught-up", {});
  assert.deepEqual(seen, [[3, "fetch"], [5, "stream"]]);
});

test("move applies locally first, then takes the server's answer", async () => {
  const { m, calls } = model([
    { status: 200, body: surface({ version: 3 }) },
    { status: 200, body: surface({ version: 3 }) },
    { status: 200, body: surface({ version: 4, shape: "item", rail: [{ text: "r", shape: "item", anchor: null, modifiers: {}, at: "server" }] }) },
  ]);
  await m.connect();
  const seen: Array<[Surface | null, string]> = [];
  m.subscribe((s, source) => seen.push([s, source]));

  const answer = m.move({ shape: "item", request: "r" });
  // Before the POST resolves the listener has already seen the local fold.
  assert.equal(seen.length, 1);
  assert.equal(seen[0]![1], "local");
  assert.equal(seen[0]![0]?.shape, "item");
  assert.equal(seen[0]![0]?.version, 3);
  assert.equal(seen[0]![0]?.rail[0]?.at, "2026-11-13T09:00:00Z");

  const s = await answer;
  assert.equal(s.version, 4);
  assert.equal(seen[1]![1], "answer");
  assert.equal(m.surface?.rail[0]?.at, "server");
  assert.deepEqual(calls[2], { url: `${base}/s1/moves`, method: "POST", body: { shape: "item", request: "r" } });
});

test("a refused move restores the previous surface and throws with status and body", async () => {
  const { m } = model([
    { status: 200, body: surface({ version: 3, shape: "shelf" }) },
    { status: 200, body: surface({ version: 3 }) },
    { status: 400, body: "shape 'nope' is not in the vocabulary" },
  ]);
  await m.connect();
  const seen: string[] = [];
  m.subscribe((_, source) => seen.push(source));

  await assert.rejects(m.move({ shape: "nope" }), (error: unknown) => {
    assert.ok(error instanceof SurfaceError);
    assert.equal(error.status, 400);
    assert.equal(error.body, "shape 'nope' is not in the vocabulary");
    assert.match(error.message, /400/);
    assert.match(error.message, /vocabulary/);
    return true;
  });
  assert.equal(m.surface?.shape, "shelf");
  assert.equal(m.surface?.version, 3);
  assert.deepEqual(seen, ["local", "local"]);
});

test("a move the network drops restores the previous surface too", async () => {
  const { m } = model([
    { status: 200, body: surface({ version: 3, shape: "shelf" }) },
    { status: 200, body: surface({ version: 3 }) },
    { status: 0, body: new TypeError("fetch failed") },
  ]);
  await m.connect();
  await assert.rejects(m.move({ shape: "item" }), TypeError);
  assert.equal(m.surface?.shape, "shelf");
});

test("say is not applied locally and returns the Said; not understood leaves the version unchanged", async () => {
  const said: Said = { understood: false, reason: "I do not know 'frob'", surface: surface({ version: 3 }) };
  const { m, calls } = model([
    { status: 200, body: surface({ version: 3 }) },
    { status: 200, body: surface({ version: 3 }) },
    { status: 200, body: said },
    { status: 200, body: { understood: true, reason: null, surface: surface({ version: 4, shape: "item" }) } },
  ]);
  await m.connect();
  const seen: string[] = [];
  m.subscribe((_, source) => seen.push(source));

  const first = m.say("frob");
  assert.deepEqual(seen, [], "nothing applied before the answer");
  assert.deepEqual(await first, said);
  assert.equal(m.surface?.version, 3);
  assert.deepEqual(seen, []);
  assert.deepEqual(calls[2], { url: `${base}/s1/say`, method: "POST", body: { text: "frob" } });

  const second = await m.say("show the item");
  assert.equal(second.understood, true);
  assert.equal(m.surface?.version, 4);
  assert.deepEqual(seen, ["answer"]);
});

test("ended closes the EventSource and reconnects: fetch, then a new stream, no show", async () => {
  const { m, calls } = model([
    { status: 200, body: surface({ version: 3 }) },
    { status: 200, body: surface({ version: 3 }) },
    { status: 200, body: surface({ version: 6 }) },
  ]);
  await m.connect();
  const first = FakeEventSource.last;

  first.emit("ended", { reason: "rebuilt" });
  assert.equal(first.closed, true);
  assert.equal(m.connected, false);

  await tick();
  await tick();
  assert.equal(calls.length, 3);
  assert.deepEqual(calls[2], { url: `${base}/s1`, method: "GET", body: undefined });
  assert.equal(m.surface?.version, 6);
  assert.equal(FakeEventSource.instances.length, 2);
  assert.notEqual(FakeEventSource.last, first);
  assert.equal(m.connected, true);
});

test("after an error, the stream's own reopening re-fetches so nothing is missed", async () => {
  const { m, calls } = model([
    { status: 200, body: surface({ version: 3 }) },
    { status: 200, body: surface({ version: 3 }) },
    { status: 200, body: surface({ version: 9 }) },
  ]);
  await m.connect();
  FakeEventSource.last.emit("open");
  assert.equal(calls.length, 2, "a first open fetches nothing");

  FakeEventSource.last.emit("error");
  FakeEventSource.last.emit("open");
  await tick();
  assert.equal(calls.length, 3);
  assert.equal(m.surface?.version, 9);
  assert.equal(FakeEventSource.instances.length, 1, "EventSource reconnected by itself");
});

test("removed nulls the surface and tells listeners", async () => {
  const { m } = model([{ status: 200, body: surface({ version: 3 }) }, { status: 200, body: surface({ version: 3 }) }]);
  await m.connect();
  const seen: Array<[Surface | null, string]> = [];
  m.subscribe((s, source) => seen.push([s, source]));

  FakeEventSource.last.emit("removed", { key: "s1" });
  assert.equal(m.surface, null);
  assert.deepEqual(seen, [[null, "removed"]]);
});

test("disconnect posts hide and closes the EventSource", async () => {
  const { m, calls } = model([
    { status: 200, body: surface({ version: 3 }) },
    { status: 200, body: surface({ version: 3 }) },
    { status: 200, body: surface({ version: 5 }) },
  ]);
  await m.connect();
  const stream = FakeEventSource.last;
  await m.disconnect();

  assert.deepEqual(calls[2], { url: `${base}/s1/hide`, method: "POST", body: { device: "d1" } });
  assert.equal(stream.closed, true);
  assert.equal(m.connected, false);
});

test("disconnect ignores a failed hide", async () => {
  const { m } = model([
    { status: 200, body: surface({ version: 3 }) },
    { status: 200, body: surface({ version: 3 }) },
    { status: 409, body: "not opened" },
  ]);
  await m.connect();
  await m.disconnect();
  assert.equal(m.connected, false);
});

test("open posts the owner and shape, then streams and shows", async () => {
  const { m, calls } = model([
    { status: 404, body: "" },
    { status: 200, body: surface({ version: 1, shape: "home" }) },
    { status: 200, body: surface({ version: 2 }) },
  ]);
  await m.connect();
  const s = await m.open("ada", "home");
  assert.equal(s.shape, "home");
  assert.deepEqual(calls[1], { url: `${base}/s1/open`, method: "POST", body: { owner: "ada", shape: "home" } });
  assert.deepEqual(calls[2]?.body, { device: "d1", deviceClass: "phone", room: false });
  assert.equal(m.connected, true);
  assert.equal(m.surface?.version, 2);
});

test("close deletes the surface and the subscribe function unsubscribes", async () => {
  const { m, calls } = model([
    { status: 200, body: surface({ version: 3 }) },
    { status: 200, body: surface({ version: 3 }) },
    { status: 204 },
  ]);
  await m.connect();
  let heard = 0;
  const stop = m.subscribe(() => heard++);
  stop();
  await m.close();
  assert.deepEqual(calls[2], { url: `${base}/s1`, method: "DELETE", body: undefined });
  assert.equal(m.surface, null);
  assert.equal(m.connected, false);
  assert.equal(heard, 0);
});
