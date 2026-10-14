import { vi } from "vitest";

export interface MockResponse {
  status?: number;
  body?: unknown;
  headers?: Record<string, string>;
}

/** A function handler may answer later (a promise that never settles keeps the request pending). */
type Handler = MockResponse | ((request: { url: URL; init: RequestInit }) => MockResponse | Promise<MockResponse>);

export interface RecordedCall {
  method: string;
  path: string;
  headers: Headers;
  body: unknown;
}

/**
 * Stubs global fetch with handlers keyed by "METHOD /path" (query string ignored).
 * Unmatched requests answer 404 with an ErrorResponse body. Returns the recorded calls.
 */
export function mockApi(handlers: Record<string, Handler>): RecordedCall[] {
  const calls: RecordedCall[] = [];
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = new URL(String(input), "http://localhost");
    const method = (init.method ?? "GET").toUpperCase();
    const body = typeof init.body === "string" && init.body ? JSON.parse(init.body) : undefined;
    calls.push({ method, path: url.pathname, headers: new Headers(init.headers), body });

    const handler = handlers[`${method} ${url.pathname}`];
    const response: MockResponse = handler
      ? typeof handler === "function" ? await handler({ url, init }) : handler
      : { status: 404, body: { message: "Not found", status: 404 } };
    const status = response.status ?? 200;
    const hasBody = response.body !== undefined && status !== 204;
    return new Response(hasBody ? JSON.stringify(response.body) : null, {
      status,
      headers: { ...(hasBody ? { "content-type": "application/json" } : {}), ...response.headers },
    });
  });
  vi.stubGlobal("fetch", fetchMock);
  return calls;
}

export function apiError(status: number, message: string): MockResponse {
  return { status, body: { message, status } };
}
