import { applyMove } from "./fold.ts";
import { eventData } from "./sse.ts";
import type { Focus, Heard, Hide, Move, Open, Said, Say, SetFocus, Show, Surface } from "./types.ts";

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

export type FocusListener = (focus: Focus, source: Source) => void;

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
  /** Open the focus lane beside the surface: stream it and let this device set it. Off by default. */
  focus?: boolean;
  /** How often at most this device sends its focus; the last value in a window always goes. */
  focusIntervalMs?: number;
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

  readonly #withFocus: boolean;
  readonly #focusIntervalMs: number;

  #surface: Surface | null = null;
  #stream: EventSource | null = null;
  // The latest-wins sequence for `say`: an answer to an older say than the newest sent is not held.
  #saySequence = 0;
  #focus: Focus | null = null;
  #focusStream: EventSource | null = null;
  #focusListeners = new Set<FocusListener>();
  #focusPending: { [key: string]: string } | null = null;
  #focusTimer: ReturnType<typeof setTimeout> | null = null;
  #focusInFlight = false;
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
    // A browser's fetch must be called on the window: a detached reference throws "Illegal invocation".
    this.#fetch = options.fetch ?? ((input, init) => globalThis.fetch(input, init));
    this.#EventSource = options.EventSource ?? globalThis.EventSource;
    this.#reconnectDelayMs = options.reconnectDelayMs ?? 1000;
    this.#now = options.now ?? (() => new Date().toISOString());
    this.#withFocus = options.focus ?? false;
    this.#focusIntervalMs = options.focusIntervalMs ?? 120;
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

  /** The focus beside the surface, or null when the lane is off or nothing has been read yet. */
  get focused(): Focus | null {
    return this.#focus;
  }

  /** Hears every change to the focus. Returns the way to stop hearing. */
  subscribeFocus(listener: FocusListener): () => void {
    this.#focusListeners.add(listener);
    return () => {
      this.#focusListeners.delete(listener);
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
    if (this.#withFocus) await this.#openFocus();
  }

  /** Hides this device from the surface (a failure is ignored) and closes the stream. */
  async disconnect(): Promise<void> {
    try {
      await this.hide();
    } catch {
      // The surface may be gone or the server unreachable; the device is leaving either way.
    }
    this.#closeStream();
    this.#closeFocus();
  }

  /** Opens the surface with its owner and home shape, then streams and shows if not already. */
  async open(owner: string, shape?: string): Promise<Surface> {
    const body: Open = shape === undefined ? { owner } : { owner, shape };
    const surface = await this.#post<Surface>("open", body);
    this.#set(surface, "answer");
    if (!this.connected) {
      this.#openStream();
      await this.show();
      if (this.#withFocus) await this.#openFocus();
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
   * interpreter will make of it. Not understood, the surface comes back unchanged. The newest say
   * wins: the answer to an older one is returned but not held. `utterance` names the utterance the
   * words belong to while it is still being heard, so its moves share one rail entry.
   */
  async say(text: string, utterance?: string): Promise<Said> {
    const body: Say = utterance === undefined ? { text } : { text, utterance };
    const sequence = ++this.#saySequence;
    const said = await this.#post<Said>("say", body);
    if (sequence !== this.#saySequence) return said;
    if (this.#surface === null || said.surface.version > this.#surface.version) {
      this.#set(said.surface, "answer");
    }
    return said;
  }

  /** The interpreter alone: what it would make of the words, with nothing persisted or held. */
  async hear(text: string): Promise<Heard> {
    const body: Say = { text };
    return this.#post<Heard>("hear", body);
  }

  /**
   * Sets where this device's attention is, throttled: at most one send per `focusIntervalMs`, and
   * the last value in a window always goes. Applied locally at once. Empty fields clear it.
   */
  focus(fields: { [key: string]: string }): void {
    if (!this.#withFocus) throw new Error("the focus lane is off: pass focus: true");
    const local: Focus = {
      id: this.#id,
      device: this.#device,
      fields: { ...fields },
      at: this.#now(),
      version: this.#focus?.version ?? 0,
    };
    this.#setFocus(local, "local");
    this.#focusPending = { ...fields };
    if (this.#focusTimer === null && !this.#focusInFlight) void this.#sendFocus();
  }

  async #sendFocus(): Promise<void> {
    const fields = this.#focusPending;
    this.#focusPending = null;
    if (fields === null) return;
    this.#focusInFlight = true;
    try {
      const body: SetFocus = { device: this.#device, fields };
      const response = await this.#fetch(`${this.#baseUrl}/${this.#id}/focus`, {
        method: "PUT",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(body),
      });
      if (response.ok) this.#acceptFocus((await response.json()) as Focus, "answer");
    } catch {
      // The next value goes with the next window; focus is never worth a retry.
    } finally {
      this.#focusInFlight = false;
      this.#focusTimer = setTimeout(() => {
        this.#focusTimer = null;
        if (this.#focusPending !== null) void this.#sendFocus();
      }, this.#focusIntervalMs);
    }
  }

  async #openFocus(): Promise<void> {
    const response = await this.#fetch(`${this.#baseUrl}/${this.#id}/focus`, { method: "GET" });
    if (response.ok) this.#acceptFocus((await response.json()) as Focus, "fetch");
    this.#closeFocus();
    const stream = new this.#EventSource(`${this.#baseUrl}/${this.#id}/focus/stream`);
    this.#focusStream = stream;
    stream.addEventListener("row", (event) => {
      const { row } = eventData<{ key: string; row: Focus }>(event);
      this.#acceptFocus(row, "stream");
    });
    stream.addEventListener("ended", () => {
      this.#closeFocus();
      setTimeout(() => {
        if (this.connected) void this.#openFocus().catch(() => undefined);
      }, this.#reconnectDelayMs);
    });
  }

  #closeFocus(): void {
    this.#focusStream?.close();
    this.#focusStream = null;
    if (this.#focusTimer !== null) clearTimeout(this.#focusTimer);
    this.#focusTimer = null;
    this.#focusPending = null;
  }

  #acceptFocus(focus: Focus, source: Source): void {
    if (this.#focus !== null && focus.version <= this.#focus.version) return;
    this.#setFocus(focus, source);
  }

  #setFocus(focus: Focus, source: Source): void {
    this.#focus = focus;
    for (const listener of this.#focusListeners) listener(focus, source);
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
    this.#closeFocus();
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
