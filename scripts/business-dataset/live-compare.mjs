#!/usr/bin/env node
// Compares a running contor (dumped with dump-live.sh) with the business dataset's labels and prints a summary;
// the full result goes to <dir>/comparison.json.
//
//   scripts/business-dataset/dump-live.sh <dir> && node scripts/business-dataset/live-compare.mjs <dir>

import { readdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { compareLive, detectShiftDays } from "./lib/live.mjs";

const DIR = process.argv[2];
if (!DIR) {
  console.error("usage: live-compare.mjs <dump-dir>");
  process.exit(2);
}
const ROOT = join(resolve(dirname(fileURLToPath(import.meta.url)), "..", ".."), "contor-api", "src", "test", "resources", "ai-eval", "business");
const read = (file) => JSON.parse(readFileSync(file, "utf8"));

const dataset = {
  messages: readdirSync(join(ROOT, "mailbox")).filter((f) => f.endsWith(".json")).map((f) => read(join(ROOT, "mailbox", f))),
  endState: read(join(ROOT, "expected", "end-state.json")),
  insights: read(join(ROOT, "expected", "insights.json")).insights,
};
const live = Object.fromEntries(["collections", "emails", "records", "proposals", "relations"].map((name) => [name, read(join(DIR, `${name}.json`))]));
const shiftDays = detectShiftDays(dataset.messages, live.emails);
const result = compareLive(dataset, live, { shiftDays });
writeFileSync(join(DIR, "comparison.json"), JSON.stringify(result, null, 2));

console.log(`dates shifted by ${shiftDays} days\n\ncollection          expected  actual  matched  duplicates`);
for (const [slug, c] of Object.entries(result.collections)) {
  if (!c.expected && !c.actual) continue;
  console.log(`${slug.padEnd(20)}${String(c.expected).padStart(8)}${String(c.actual).padStart(8)}${String(c.matched).padStart(9)}${String(result.duplicates[slug].length).padStart(12)}`);
}
console.log("\nfields of matched records");
for (const [field, s] of Object.entries(result.fields)) console.log(`  ${field.padEnd(34)} ${s.ok}/${s.total}${s.shifted ? ` (${s.shifted} via shifted date)` : ""}`);
const p = result.perMail;
console.log(`\nper mail: ${p.mailsWithMisses} labelled mails missed a key record (${p.mails.filter((m) => m.nothingProduced).length} produced nothing)`);
for (const [slug, n] of Object.entries(p.missedByCollection)) console.log(`  ${slug}: ${n} of ${p.expectedByCollection[slug]} missed`);
const r = result.replies;
console.log(`\nreplies: needed ${r.neededDrafted}/${r.needed} drafted, courtesy ${r.courtesyDrafted}/${r.courtesy}, unneeded drafts ${r.unneeded.length}, drafts on noise ${r.onNoise.length}`);
console.log(`insights: ${result.insights.filter((i) => i.found).length}/${result.insights.length} expected found; proposed: ${result.insightsProposed.join(" | ")}`);
console.log(`noise mails with records: ${result.noiseRecords.length}\n\nfull result: ${join(DIR, "comparison.json")}`);
