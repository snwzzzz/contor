import { redirect } from "next/navigation";
import { isSettingsSection, SETTINGS_PARAM } from "@/lib/navigation";

/** Kept so /settings URLs (bookmarks, the Microsoft OAuth return) open the settings modal over the inbox. */
const FORWARDED_PARAMS = ["connected", "mailError"];

export default async function SettingsRedirect({ params, searchParams }: PageProps<"/settings/[[...section]]">) {
  const [{ section }, query] = await Promise.all([params, searchParams]);
  const requested = section?.[0];
  const target = new URLSearchParams({ [SETTINGS_PARAM]: isSettingsSection(requested) ? requested : "accounts" });
  for (const name of FORWARDED_PARAMS) {
    const value = query[name];
    if (typeof value === "string") target.set(name, value);
  }
  redirect(`/inbox?${target}`);
}
