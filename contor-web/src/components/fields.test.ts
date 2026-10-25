import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * Guard for the one field look (`components/ui/input.tsx`): text fields are `Input`, `Textarea` or `SearchField`.
 * Raw `<input>` is only allowed for checkboxes, radios and file pickers, raw `<textarea>` never (outside components/ui).
 */
const ROOT = join(__dirname, "..");
const ALLOWED_RAW_TYPES = /type="(checkbox|radio|file)"/;

function sources(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return name === "ui" || name === "generated" ? [] : sources(path);
    return path.endsWith(".tsx") && !path.endsWith(".test.tsx") ? [path] : [];
  });
}

describe("text fields", () => {
  it("all use the shared field components", () => {
    const offenders = sources(ROOT).flatMap((path) => {
      const text = readFileSync(path, "utf8");
      const raw = [...text.matchAll(/<(input|textarea)\b[^>]*>/g)].filter(
        (match) => match[1] === "textarea" || !ALLOWED_RAW_TYPES.test(match[0]),
      );
      return raw.map((match) => `${relative(ROOT, path)}: ${match[0].split("\n")[0]}`);
    });

    expect(offenders).toEqual([]);
  });
});
