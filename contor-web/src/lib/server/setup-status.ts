import "server-only";

const apiUrl = process.env.CONTOR_API_URL ?? "http://127.0.0.1:8080";

/**
 * Whether first-run setup is pending. Asked server-side, straight from the backend, before the login/setup page
 * renders (no client round trip). The backend only answers anonymously while no user exists (401 afterwards).
 */
export async function isSetupRequired(): Promise<boolean> {
  try {
    const response = await fetch(`${apiUrl}/api/setup`, { cache: "no-store", headers: { accept: "application/json" } });
    if (!response.ok) return false;
    const body = (await response.json()) as { setupRequired?: boolean };
    return body.setupRequired === true;
  } catch {
    return false;
  }
}
