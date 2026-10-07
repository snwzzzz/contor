/**
 * Hand-written fetch wrapper used by the generated client (orval `mutator`).
 * Same-origin only: Next.js rewrites /api/* to Spring, so there is no CORS and the
 * session cookie travels automatically. Mutating requests carry the CSRF token
 * Spring Security puts into the readable XSRF-TOKEN cookie.
 */

const CSRF_COOKIE = "XSRF-TOKEN";
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

export async function apiFetch<T>(url: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);
  if (!SAFE_METHODS.has(method)) {
    const token = readCookie(CSRF_COOKIE);
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
