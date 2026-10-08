import "server-only";

const apiUrl = process.env.CONTOR_API_URL ?? "http://127.0.0.1:8080";

/**
 * The dev account the backend signs in automatically, if it offers that: only the `dev` profile with a configured
 * DEV_ACCOUNT_* has the endpoint, and it answers loopback callers only (this Next.js server).
 */
export async function devLoginUsername(): Promise<string | undefined> {
  try {
    const response = await fetch(`${apiUrl}/api/auth/dev-login`, { cache: "no-store", headers: { accept: "application/json" } });
    if (!response.ok) return undefined;
    const body = (await response.json()) as { username?: string };
    return body.username || undefined;
  } catch {
    return undefined;
  }
}
