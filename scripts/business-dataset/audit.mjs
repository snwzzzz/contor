#!/usr/bin/env node
// Phase L, the independent label audit (prompt L1 in docs/business-dataset-prompts.md).
//
//   node scripts/business-dataset/audit.mjs prepare <dir> [chunks]   write the auditor inputs (no labels) in chunks
//   node scripts/business-dataset/audit.mjs compare <dir>             diff the auditor's answers (<dir>/answers-*.json)
//                                                                     against the labels and write audit.json
// The auditor (a separate model call per chunk, without the storylines) answers one JSON array per chunk:
// [{id, records, relations, needsReply, reply: {intent, questions, courtesyKind}, importance, mustNotProduce, uncertain}].

import { mkdirSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { auditInput, compareLabels } from "./lib/audit.mjs";

const REPO = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");
const ROOT = join(REPO, "contor-api", "src", "test", "resources", "ai-eval", "business");
const [command, dir, chunkArg] = process.argv.slice(2);
if (!command || !dir) {
  console.error("usage: audit.mjs prepare|compare <dir> [chunks]");
  process.exit(2);
}

const messages = readdirSync(join(ROOT, "mailbox")).filter((f) => f.endsWith(".json")).sort()
  .map((f) => JSON.parse(readFileSync(join(ROOT, "mailbox", f), "utf8")));
const byId = new Map(messages.map((m) => [m.id, m]));
const audited = messages.filter((m) => m.folder === "inbox" && m.storyline !== "noise" && !m.duplicateOf);

if (command === "prepare") {
  const chunks = Number(chunkArg ?? 3);
  mkdirSync(dir, { recursive: true });
  const size = Math.ceil(audited.length / chunks);
  for (let i = 0; i < chunks; i++) {
    const part = audited.slice(i * size, (i + 1) * size).map((m) => auditInput(m, byId));
    writeFileSync(join(dir, `input-${i + 1}.json`), JSON.stringify(part, null, 1), "utf8");
    console.log(`input-${i + 1}.json: ${part.length} mails`);
  }
} else if (command === "compare") {
  const answers = readdirSync(dir).filter((f) => /^answers-.*\.json$/.test(f)).sort()
    .flatMap((f) => JSON.parse(readFileSync(join(dir, f), "utf8")));
  const result = compareLabels(audited, answers);
  const report = {
    prompt: "L1", audited: answers.length, of: audited.length, rates: result.rates,
    disagreements: result.disagreements.length, details: result.disagreements,
  };
  writeFileSync(join(dir, "comparison.json"), JSON.stringify(report, null, 2), "utf8");
  console.log(JSON.stringify({ audited: report.audited, of: report.of, rates: report.rates, disagreements: report.disagreements }, null, 2));
} else {
  console.error(`unknown command ${command}`);
  process.exit(2);
}
