// Fakes for the two seams the model takes from its options: fetch and EventSource.
import type { Surface } from "../src/index.ts";

export type Call = { url: string; method: string; body: unknown };

/** An answer scripted for the fake fetch: a status and a JSON body (or text when a string). */
export type Answer = { status: number; body?: unknown };

/** A fetch that records every call and answers from a script, in order. Throws when the script runs out. */
export function fakeFetch(script: Answer[]): { fetch: typeof fetch; calls: Call[] } {
  const calls: Call[] = [];
  const fetch = async (input: string | URL | Request, init?: RequestInit): Promise<Response> => {
    const url = typeof input === "string" ? input : input instanceof URL ? input.href : input.url;
    const body = typeof init?.body === "string" ? JSON.parse(init.body) : undefined;
    calls.push({ url, method: init?.method ?? "GET", body });
    const answer = script.shift();
    if (answer === undefined) throw new Error(`no answer scripted for ${init?.method ?? "GET"} ${url}`);
    if (answer.body instanceof Error) throw answer.body;
    const text = typeof answer.body === "string" ? answer.body : JSON.stringify(answer.body ?? null);
    return new Response(answer.status === 204 ? null : text, { status: answer.status });
  };
  return { fetch: fetch as typeof fetch, calls };
}

/** An EventSource a test drives by hand. Every instance made is kept so a test can find it. */
export class FakeEventSource {
  static instances: FakeEventSource[] = [];

  readonly url: string;
  closed = false;
  #listeners = new Map<string, Set<(event: Event) => void>>();

  constructor(url: string) {
    this.url = url;
    FakeEventSource.instances.push(this);
  }

  static reset(): void {
    FakeEventSource.instances = [];
  }

  static get last(): FakeEventSource {
    const last = FakeEventSource.instances.at(-1);
    if (last === undefined) throw new Error("no EventSource was opened");
    return last;
  }

  addEventListener(name: string, fn: (event: Event) => void): void {
    let set = this.#listeners.get(name);
    if (set === undefined) {
      set = new Set();
      this.#listeners.set(name, set);
    }
    set.add(fn);
  }

  close(): void {
    this.closed = true;
  }

  /** Emits a named event with JSON data, as the server would. */
  emit(name: string, data?: unknown): void {
    const event = data === undefined ? new Event(name) : new MessageEvent(name, { data: JSON.stringify(data) });
    for (const fn of this.#listeners.get(name) ?? []) fn(event);
  }
}

export const asEventSource = FakeEventSource as unknown as typeof EventSource;

export function surface(overrides: Partial<Surface> = {}): Surface {
  return {
    id: "s1",
    owner: "ada",
    opened: true,
    shape: "shelf",
    anchor: null,
    modifiers: {},
    pending: null,
    rail: [],
    showing: [],
    version: 1,
    ...overrides,
  };
}

export const tick = (): Promise<void> => new Promise((resolve) => setTimeout(resolve, 0));
