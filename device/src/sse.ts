/** Reads a server-sent event's data as JSON. The stream's events all carry a JSON object. */
export function eventData<T>(event: Event): T {
  const data = (event as MessageEvent<string>).data;
  return JSON.parse(data) as T;
}
