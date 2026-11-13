import { test } from "node:test";
import assert from "node:assert/strict";
import { applyMove } from "../src/index.ts";
import type { Move } from "../src/index.ts";
import { surface } from "./fakes.ts";

const at = "2026-11-13T09:00:00Z";

test("applyMove applies every part in the contract's order and puts the request on the rail", () => {
  const before = surface({
    shape: "shelf",
    anchor: { kind: "theme", id: "t1" },
    modifiers: { order: "newest", time: "today", stance: "for" },
    pending: { kind: "bet", fields: { stake: "5" } },
    version: 7,
  });
  const move: Move = {
    shape: "item",
    anchor: { kind: "market", id: "m1" },
    modifiers: { time: "week", colour: "red" },
    clearModifiers: ["stance", "time"],
    pending: { kind: "note", fields: {} },
    request: "show me the market",
  };
  const after = applyMove(before, move, at);

  assert.equal(after.shape, "item");
  assert.deepEqual(after.anchor, { kind: "market", id: "m1" });
  // "time" is cleared and then set again: set wins because clears come first.
  assert.deepEqual(after.modifiers, { order: "newest", time: "week", colour: "red" });
  assert.deepEqual(after.pending, { kind: "note", fields: {} });
  assert.deepEqual(after.rail, [
    {
      text: "show me the market",
      shape: "item",
      anchor: { kind: "market", id: "m1" },
      modifiers: { order: "newest", time: "week", colour: "red" },
      at,
      utterance: null,
    },
  ]);
  assert.equal(after.version, 7, "version belongs to the server");
});

test("clearAnchor wins over anchor and clearPending over pending", () => {
  const before = surface({ anchor: { kind: "theme", id: "t1" }, pending: { kind: "bet", fields: {} } });
  const after = applyMove(before, { anchor: { kind: "market", id: "m1" }, clearAnchor: true, pending: { kind: "x", fields: {} }, clearPending: true }, at);
  assert.equal(after.anchor, null);
  assert.equal(after.pending, null);
});

test("the rail is newest first and trimmed to railLength", () => {
  let s = surface();
  for (const text of ["one", "two", "three", "four"]) s = applyMove(s, { request: text }, at);
  assert.deepEqual(s.rail.map((entry) => entry.text), ["four", "three", "two"]);

  const longer = applyMove(s, { request: "five" }, at, 5);
  assert.deepEqual(longer.rail.map((entry) => entry.text), ["five", "four", "three", "two"]);
});

test("applyMove does not mutate its input", () => {
  const before = surface({ modifiers: { order: "newest" }, rail: [] });
  const frozen = structuredClone(before);
  const after = applyMove(before, { shape: "item", modifiers: { time: "week" }, clearModifiers: ["order"], request: "r" }, at);
  assert.deepEqual(before, frozen);
  assert.notEqual(after, before);
  assert.notEqual(after.modifiers, before.modifiers);
  assert.notEqual(after.rail, before.rail);
});

test("applyMove throws on a move that names nothing", () => {
  assert.throws(() => applyMove(surface(), {}, at), Error);
  assert.throws(() => applyMove(surface(), { modifiers: {}, clearModifiers: [], clearAnchor: false, clearPending: false }, at), Error);
});

test("the moves of one utterance share one rail entry; a new utterance or none pushes", () => {
  const one = applyMove(surface(), { shape: "shelf", request: "new", utterance: "u-1" }, at);
  const two = applyMove(one, { shape: "shelf", request: "newcastle", utterance: "u-1" }, at);
  assert.deepEqual(two.rail.map((e) => e.text), ["newcastle"]);
  assert.equal(two.rail[0]?.utterance, "u-1");
  const three = applyMove(two, { shape: "item", request: "why", utterance: "u-2" }, at);
  assert.deepEqual(three.rail.map((e) => e.text), ["why", "newcastle"]);
  const four = applyMove(three, { shape: "item", request: "typed" }, at);
  assert.deepEqual(four.rail.map((e) => e.text), ["typed", "why", "newcastle"]);
  assert.equal(four.rail[0]?.utterance, null);
});
