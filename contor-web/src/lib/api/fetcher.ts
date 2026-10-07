/**
 * Hand-written fetch wrapper used by the generated client (orval `mutator`).
 * Same-origin only: Next.js rewrites /api/* to Spring, so there is no CORS and the
 * session cookie travels automatically. Mutating requests carry the CSRF token
 * Spring Security puts into the readable XSRF-TOKEN cookie.
 */

const CSRF_COOKIE = "XSRF-TOKEN";
const CSRF_ENDPOINT = "/api/auth/csrf";
const CSRF_HEADER = "X-XSRF-TOKEN";
const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS", "TRACE"]);

export class ApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

function readCookie(name: string): string | undefined {
  if (typeof document === "undefined") return undefined;
  const prefix = `${name}=`;
  for (const part of document.cookie.split("; ")) {
    if (part.startsWith(prefix)) {
      const value = part.slice(prefix.length);
      return value ? decodeURIComponent(value) : undefined;
    }
  }
  return undefined;
}

function isErrorResponse(body: unknown): body is { message: string; status: number } {
  return (
    typeof body === "object" &&
    body !== null &&
    typeof (body as { message?: unknown }).message === "string"
  );
}

async function parseBody(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) return undefined;
  const contentType = response.headers.get("content-type") ?? "";
  if (!contentType.includes("json")) return text;
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

/** The CSRF cookie, fetching it first when missing (first request, or after login/logout rotated it). */
async function csrfToken(): Promise<string | undefined> {
  const existing = readCookie(CSRF_COOKIE);
  if (existing) return existing;
  await fetch(CSRF_ENDPOINT, { credentials: "same-origin" });
  return readCookie(CSRF_COOKIE);
}

export async function apiFetch<T>(url: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);
  if (!SAFE_METHODS.has(method)) {
    const token = await csrfToken();
    if (token) headers.set(CSRF_HEADER, token);
  }

  const response = await fetch(url, { ...init, method, headers, credentials: "same-origin" });
  const body = await parseBody(response);

  if (!response.ok) {
    const message = isErrorResponse(body) ? body.message : `Request failed (${response.status})`;
    throw new ApiError(message, response.status);
  }
  return body as T;
}

export default apiFetch;

/** Error type of every generated query/mutation (picked up by orval from this mutator file). */
// eslint-disable-next-line @typescript-eslint/no-unused-vars
export type ErrorType<_ErrorBody> = ApiError;
