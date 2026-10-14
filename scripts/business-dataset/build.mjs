#!/usr/bin/env node
// Builds the business mail dataset from its committed sources (world.json, storylines/, noise/) into mailbox/,
// expected/ and manifest.json, and runs the validators. Deterministic: same sources, same output.
//
//   node scripts/business-dataset/build.mjs           build and write (refuses to write when a validator fails)
//   node scripts/business-dataset/build.mjs --check   validate only
//   node scripts/business-dataset/build.mjs --force   write even with validator errors (to inspect them)

import { createHash } from "node:crypto";
import { mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { buildMailbox } from "./lib/merge.mjs";
import { validate } from "./lib/validate.mjs";
import { endState } from "./lib/endstate.mjs";

const GENERATOR_VERSION = "1";
const REPO = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");
const ROOT = join(REPO, "contor-api", "src", "test", "resources", "ai-eval", "business");
// Edge cases of the plan's catalogue (sections D and J): each should be tagged on at least one storyline or beat.
const EDGE_TAGS = [
  "context-needed", "terse-repeat-order", "rush-order", "contradicting-change", "cancel-after-shipment", "duplicate-account",
  "split-shipment", "customs-hold", "damage", "partial-delivery", "price-increase", "slot-booking", "time-zone", "date-format",
  "calendar-week", "shared-driver", "different-marek", "name-typo", "escalation", "churn-signal", "competitor-mentioned",
  "goodbye", "new-customer", "smooth", "several-topics", "ok-do-it", "forwarded", "cc-only", "duplicate-send", "wrong-recipient",
  "number-typo", "amount-format", "relative-date", "phishing", "very-short", "very-long", "rhetorical-question", "answered",
  "job-change", "thread-split",
];

const args = new Set(process.argv.slice(2));

function readJson(file) {
  try {
    return JSON.parse(readFileSync(file, "utf8"));
  } catch (e) {
    throw new Error(`${relative(REPO, file)}: ${e.message}`);
  }
}

function jsonFiles(dir) {
  try {
    return readdirSync(dir).filter((f) => f.endsWith(".json")).sort().map((f) => join(dir, f));
  } catch {
    return [];
  }
}

function optionalJson(file, pick) {
  try {
    return pick(JSON.parse(readFileSync(file, "utf8")));
  } catch {
    return null;
  }
}

function write(file, value) {
  mkdirSync(dirname(file), { recursive: true });
  writeFileSync(file, JSON.stringify(value, null, 2) + "\n", "utf8");
}

const hash = createHash("sha256");
const world = readJson(join(ROOT, "world.json"));
hash.update(JSON.stringify(world));
const storylines = [];
const sourceFiles = {};
for (const file of jsonFiles(join(ROOT, "storylines"))) {
  const content = readJson(file);
  hash.update(JSON.stringify(content));
  const list = Array.isArray(content) ? content : content.storylines;
  sourceFiles[relative(ROOT, file).replaceAll("\\", "/")] = { storylines: list.length, prompt: content.prompt ?? null, model: content.model ?? null };
  storylines.push(...list);
}
const noise = [];
for (const file of jsonFiles(join(ROOT, "noise"))) {
  const content = readJson(file);
  hash.update(JSON.stringify(content));
  const list = Array.isArray(content) ? content : content.mails;
  sourceFiles[relative(ROOT, file).replaceAll("\\", "/")] = { mails: list.length, prompt: content.prompt ?? null, model: content.model ?? null };
  noise.push(...list);
}

const { messages, errors: mergeErrors } = buildMailbox(world, storylines, noise);
const { errors: validationErrors } = validate(world, storylines, messages);
const errors = [...mergeErrors, ...validationErrors];

const ids = new Set(storylines.map((s) => s.id));
if (ids.size !== storylines.length) errors.push("schema: duplicate storyline ids");

// insights: storyline-level expectations with evidence beats
const idOf = new Map();
messages.forEach((m) => { const ref = `${m.storyline}#${m.beat}`; if (!idOf.has(ref)) idOf.set(ref, m.id); });
const insights = storylines.flatMap((story) => (story.insights ?? []).map((insight) => ({
  id: insight.id, storyline: story.id, title: insight.title, asOf: insight.asOf,
  evidence: insight.evidence.map((ref) => {
    const id = idOf.get(ref.startsWith("#") ? story.id + ref : ref);
    if (!id) errors.push(`merge: unresolved insight evidence ${ref} in ${story.id}`);
    return id;
  }),
})));

// coverage (warnings: a slice does not need every edge case yet)
const tags = new Set(messages.flatMap((m) => [...(m.tags ?? []), ...(m.storylineTags ?? [])]));
const missingTags = EDGE_TAGS.filter((t) => !tags.has(t));

const count = (fn) => messages.reduce((acc, m) => { const k = fn(m); acc[k] = (acc[k] ?? 0) + 1; return acc; }, {});
const inbound = messages.filter((m) => m.folder === "inbox" && m.storyline !== "noise");
const stats = {
  messages: messages.length,
  storylines: storylines.length,
  threads: new Set(messages.map((m) => m.conversationId)).size,
  byCategory: count((m) => (m.storyline === "noise" ? "I" : (m.category ?? "?").replace(/\d.*$/, ""))),
  byFolder: count((m) => m.folder),
  byAccount: count((m) => m.account),
  byLanguage: count((m) => m.lang ?? "?"),
  needsReply: inbound.filter((m) => m.needsReply === true).length,
  labelledRecords: inbound.reduce((n, m) => n + Object.values(m.expected.records).reduce((k, r) => k + r.length, 0), 0),
  period: messages.length ? [messages[0].email.receivedAt, messages.at(-1).email.receivedAt] : [],
};

console.log(JSON.stringify(stats, null, 2));
if (missingTags.length) console.log(`coverage: edge cases not tagged yet: ${missingTags.join(", ")}`);
if (errors.length) {
  console.log(`\n${errors.length} validator error(s):`);
  errors.forEach((e) => console.log("  " + e));
} else {
  console.log("\nvalidators: all green");
}

if (args.has("--check") || (errors.length && !args.has("--force"))) {
  process.exit(errors.length ? 1 : 0);
}

rmSync(join(ROOT, "mailbox"), { recursive: true, force: true });
for (const message of messages) write(join(ROOT, "mailbox", `${message.id}.json`), message);
const state = endState(messages);
write(join(ROOT, "expected", "end-state.json"), { asOf: stats.period[1] ?? null, ...state });
write(join(ROOT, "expected", "insights.json"), { insights });
write(join(ROOT, "manifest.json"), {
  generator: { version: GENERATOR_VERSION, script: "scripts/business-dataset/build.mjs" },
  sourcesSha256: hash.digest("hex"),
  sources: sourceFiles,
  stats,
  validators: { errors: errors.length, missingEdgeCases: missingTags },
  labelAudit: optionalJson(join(ROOT, "audit.json"), (audit) => ({ prompt: audit.prompt, model: audit.model, audited: audit.audited, rates: audit.ratesAfterFixes })),
  review: optionalJson(join(ROOT, "review.json"), (review) => review),
});
console.log(`wrote ${messages.length} messages to ${relative(REPO, join(ROOT, "mailbox"))}`);
process.exit(errors.length ? 1 : 0);
