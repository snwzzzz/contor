import { readFileSync } from "node:fs";
import { join } from "node:path";

export const OWNER = {
  username: "e2e_owner",
  email: "owner@outlook.com",
  password: "Correct-Horse-7-Battery",
};

/** The one-time setup token the backend printed to its log on startup. */
export function readSetupToken(): string {
  const log = readFileSync(join(__dirname, "..", ".tmp", "backend.log"), "utf8");
  const banner = log.lastIndexOf("first-run setup");
  const token = banner >= 0 ? /^\s+([A-Za-z0-9_-]{43})\s*$/m.exec(log.slice(banner))?.[1] : undefined;
  if (!token) throw new Error("No setup token in e2e/.tmp/backend.log");
  return token;
}

export const STORAGE_STATE = join(__dirname, "..", ".tmp", "owner.json");
