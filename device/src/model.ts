import { applyMove } from "./fold.ts";
import { eventData } from "./sse.ts";
import type { Hide, Move, Open, Said, Say, Show, Surface } from "./types.ts";

/** A refusal from the server: any answer that is not 2xx. */
export class SurfaceError extends Error {
  readonly status: number;
  readonly body: string;

  constructor(status: number, body: string, what: string) {
    super(`${what} answered ${status}: ${body}`);
    this.name = "SurfaceError";
    this.status = status;
    this.body = body;
  }
}

/** Where a change to the held surface came from. */
export type Source = "fetch" | "local" | "answer" | "stream" | "removed";

export type Listener = (surface: Surface | null, source: Source) => void;

export type SurfaceModelOptions = {
  /** The prefix the consumer mounted the endpoint under, e.g. `/api/surfaces`. */
  baseUrl: string;
  /** The surface's id. */
  id: string;
  /** This device's own id. */
  device: string;
  /** How this device renders: "phone", "laptop", "tv" or whatever the vocabulary names. */
  deviceClass: string;
  /** True when the device shows the surface where others can see. */
  room?: boolean;
  /** The vocabulary's rail length, for the local fold. 3 unless the vocabulary says. */
  railLength?: number;
  /** Injectable for tests; the globals by default. */
  fetch?: typeof fetch;
  EventSource?: typeof EventSource;
  /** How long to wait after the server ends the stream before fetching and streaming again. */
  reconnectDelayMs?: number;
  /** The clock for rail entries made locally. */
  now?: () => string;
};

/**
 * The device's half of a surface: holds one surface and its version, keeps it current from the
 * stream, and makes the calls a steering device makes. A renderer subscribes and draws.
 */
export class SurfaceModel {
  readonly #baseUrl: string;
  readonly #id: string;
  readonly #device: string;
  readonly #deviceClass: string;
  readonly #room: boolean;
  readonly #railLength: number;
  readonly #fetch: typeof fetch;
  readonly #EventSource: typeof EventSource;
  readonly #reconnectDelayMs: number;
  readonly #now: () => string;

  #surface: Surface | null = null;
  #stream: EventSource | null = null;
  // Set by an `error` event so the next `open` re-fetches, in case a change was missed.
  #erred = false;
  #listeners = new Set<Listener>();

  constructor(options: SurfaceModelOptions) {
    this.#baseUrl = options.baseUrl.replace(/\/+$/, "");
    this.#id = options.id;
    this.#device = options.device;
    this.#deviceClass = options.deviceClass;
    this.#room = options.room ?? false;
    this.#railLength = options.railLength ?? 3;
    this.#fetch = options.fetch ?? globalThis.fetch;
    this.#EventSource = options.EventSource ?? globalThis.EventSource;
    this.#reconnectDelayMs = options.reconnectDelayMs ?? 1000;
    this.#now = options.now ?? (() => new Date().toISOString());
  }

  /** The surface held, or null when there is none (never opened, or closed). */
  get surface(): Surface | null {
    return this.#surface;
  }

  /** True while the stream is open. */
  get connected(): boolean {
    return this.#stream !== null;
  }

  /** Hears every change to the held surface. Returns the way to stop hearing. */
  subscribe(listener: Listener): () => void {
    this.#listeners.add(listener);
    return () => {
      this.#listeners.delete(listener);
    };
  }

  /**
   * Fetches the surface, opens the stream, and shows this device on the surface. A surface never
   * opened (404) leaves `surface` null and opens nothing: `open` it first.
   */
  async connect(): Promise<void> {
    const found = await this.#refetch();
    if (!found) return;
    this.#openStream();
    await this.show();
  }

  /** Hides this device from the surface (a failure is ignored) and closes the stream. */
  async disconnect(): Promise<void> {
    try {
      await this.hide();
    } catch {
      // The surface may be gone or the server unreachable; the device is leaving either way.
    }
    this.#closeStream();
  }

  /** Opens the surface with its owner and home shape, then streams and shows if not already. */
  async open(owner: string, shape?: string): Promise<Surface> {
    const body: Open = shape === undefined ? { owner } : { owner, shape };
    const surface = await this.#post<Surface>("open", body);
    this.#set(surface, "answer");
    if (!this.connected) {
      this.#openStream();
      await this.show();
    }
    return surface;
  }

  /**
   * Applies the move locally at once, by the same fold the server applies, then sends it; the
   * server's answer replaces the local result. A refusal restores the surface as it was.
   */
  async move(move: Move): Promise<Surface> {
    const before = this.#surface;
    if (before !== null) this.#set(applyMove(before, move, this.#now(), this.#railLength), "local");
    try {
      const surface = await this.#post<Surface>("moves", move);
      this.#set(surface, "answer");
      return surface;
    } catch (error) {
      // Put back what was held; the server did not take the move.
      if (before !== null) this.#set(before, "local");
      throw error;
    }
  }

  /**
   * Hands the text to the interpreter. Not applied locally: the device cannot know what the
   * interpreter will make of it. Not understood, the surface comes back unchanged.
   */
  async say(text: string): Promise<Said> {
    const body: Say = { text };
    const said = await this.#post<Said>("say", body);
    if (this.#surface === null || said.surface.version > this.#surface.version) {
      this.#set(said.surface, "answer");
    }
    return said;
  }

  /** Records this device as showing the surface. */
  async show(): Promise<Surface> {
    const body: Show = { device: this.#device, deviceClass: this.#deviceClass, room: this.#room };
    const surface = await this.#post<Surface>("show", body);
    this.#accept(surface, "answer");
    return surface;
  }

  /** Removes this device from the surface's showing. */
  async hide(): Promise<Surface> {
    const body: Hide = { device: this.#device };
    const surface = await this.#post<Surface>("hide", body);
    this.#accept(surface, "answer");
    return surface;
  }

  /** Closes the surface, freeing its id. The stream's `removed` event clears what is held. */
  async close(): Promise<void> {
    const response = await this.#fetch(`${this.#baseUrl}/${this.#id}`, { method: "DELETE" });
    if (!response.ok) throw new SurfaceError(response.status, await response.text(), "DELETE");
    this.#closeStream();
    this.#set(null, "removed");
  }

  // GET the surface now. False when it was never opened.
  async #refetch(): Promise<boolean> {
    const response = await this.#fetch(`${this.#baseUrl}/${this.#id}`, { method: "GET" });
    if (response.status === 404) {
      this.#set(null, "fetch");
      return false;
    }
    if (!response.ok) throw new SurfaceError(response.status, await response.text(), "GET");
    this.#set((await response.json()) as Surface, "fetch");
    return true;
  }

  #openStream(): void {
    this.#closeStream();
    const stream = new this.#EventSource(`${this.#baseUrl}/${this.#id}/stream`);
    this.#stream = stream;
    this.#erred = false;

    stream.addEventListener("row", (event) => {
      const { row } = eventData<{ key: string; row: Surface }>(event);
      this.#accept(row, "stream");
    });
    // `caught-up` says the first row is the surface now; nothing to do with it.
    stream.addEventListener("removed", () => {
      this.#set(null, "removed");
    });
    stream.addEventListener("ended", () => {
      // The server ended the stream; fetch the surface now, then stream again.
      this.#closeStream();
      setTimeout(() => {
        void this.#reconnect();
      }, this.#reconnectDelayMs);
    });
    stream.addEventListener("error", () => {
      // EventSource reconnects by itself; remember so the next open re-fetches.
      this.#erred = true;
    });
    stream.addEventListener("open", () => {
      if (!this.#erred) return;
      this.#erred = false;
      void this.#refetch().catch(() => undefined);
    });
  }

  async #reconnect(): Promise<void> {
    try {
      if (await this.#refetch()) this.#openStream();
    } catch {
      // Unreachable for now; EventSource would not have helped either. Try again after the delay.
      setTimeout(() => {
        void this.#reconnect();
      }, this.#reconnectDelayMs);
    }
  }

  #closeStream(): void {
    this.#stream?.close();
    this.#stream = null;
  }

  async #post<T>(route: string, body: unknown): Promise<T> {
    const response = await this.#fetch(`${this.#baseUrl}/${this.#id}/${route}`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(body),
    });
    if (!response.ok) throw new SurfaceError(response.status, await response.text(), `POST /${route}`);
    return (await response.json()) as T;
  }

  // Takes the surface only when its version is higher than the one held.
  #accept(surface: Surface, source: Source): void {
    if (this.#surface !== null && surface.version <= this.#surface.version) return;
    this.#set(surface, source);
  }

  #set(surface: Surface | null, source: Source): void {
    this.#surface = surface;
    for (const listener of this.#listeners) listener(surface, source);
  }
}
