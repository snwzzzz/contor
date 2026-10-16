/** Session cookie set by contor-api (see server.servlet.session.cookie.name). */
export const SESSION_COOKIE = "CONTOR_SESSION";

const PUBLIC_PATHS = new Set(["/login", "/setup"]);
const DEFAULT_TARGET = "/";

/** Pages that work without a session. */
export function isPublicPath(pathname: string): boolean {
  return PUBLIC_PATHS.has(pathname);
}

/**
 * Where to go after login. Only same-origin absolute paths are accepted (no open redirects such as
 * "//evil.example" or "https://..."), and never back to a public page.
 */
export function safeNextPath(next: string | null | undefined): string {
  if (!next || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/\\")) {
    return DEFAULT_TARGET;
  }
  const pathname = next.split(/[?#]/)[0];
  return isPublicPath(pathname) ? DEFAULT_TARGET : next;
}
