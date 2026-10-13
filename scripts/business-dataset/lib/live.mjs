// Compares what a running contor holds (dumped by dump-live.sh) with the labels of the business dataset: the end state
// of every collection, the records each labelled mail should have produced, reply drafts and insights.

import { SCHEMA } from "./schema.mjs";
import { plain } from "./validate.mjs";

const KEY_COLLECTIONS = ["sales_orders", "purchase_orders", "shipments", "invoices", "complaints", "tasks", "events"];

const tokens = (text) => new Set(plain(text).split(/[^\p{L}\p{N}]+/u).filter(Boolean));
function similarity(a, b) {
  const x = tokens(a);
  const y = tokens(b);
  if (!x.size || !y.size) return 0;
  const common = [...x].filter((t) => y.has(t)).length;
  return common / new Set([...x, ...y]).size;
}
const keyNorm = (value) => plain(value).replace(/[^a-z0-9@]/g, "");
const day = (iso) => String(iso).slice(0, 10);
function shiftDate(iso, days) {
  const date = new Date(day(iso) + "T12:00:00Z");
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}
const isLabelled = (m) => m.folder === "inbox" && m.storyline !== "noise" && !m.duplicateOf;

/** Days the import moved the mail (0 for an unshifted replay). */
export function detectShiftDays(messages, emails) {
  const byId = new Map(messages.map((m) => [m.id, m]));
  const sample = emails.find((e) => e.receivedAt && byId.has(e.email));
  if (!sample) return 0;
  return Math.round((Date.parse(sample.receivedAt) - Date.parse(byId.get(sample.email).email.receivedAt)) / 864e5);
}

function valuesOf(slug, row) {
  const out = {};
  for (const [field, type] of Object.entries(SCHEMA[slug]?.fields ?? {})) {
    if (type === "MONEY") {
      if (row[`${field}_amount`] != null) out[field] = { amount: Number(row[`${field}_amount`]), currency: String(row[`${field}_currency`] ?? "").trim() };
    } else if (row[field] != null) {
      out[field] = row[field];
    }
  }
  return out;
}

/** true, "shifted" (equal after the import's date shift) or false. */
function fieldEqual(type, expected, actual, shiftDays) {
  if (actual == null) return false;
  switch (type) {
    case "MONEY":
      return Math.abs(Number(expected.amount) - Number(actual.amount)) < 0.005
        && String(expected.currency).toUpperCase() === String(actual.currency).toUpperCase();
    case "DATE":
      if (day(actual) === day(expected)) return true;
      return shiftDays && day(actual) === shiftDate(expected, shiftDays) ? "shifted" : false;
    case "DATETIME": {
      const e = Date.parse(expected);
      const a = Date.parse(actual);
      if (a === e) return true;
      return shiftDays && a === e + shiftDays * 864e5 ? "shifted" : false;
    }
    case "PHONE":
      return String(actual).replace(/[^0-9]/g, "") === String(expected).replace(/[^0-9]/g, "");
    case "NUMBER":
      return Number(actual) === Number(expected);
    case "ENUM": case "STATUS": case "EMAIL":
      return plain(actual) === plain(expected);
    default:
      if (/\d/.test(String(expected))) return keyNorm(actual).includes(keyNorm(expected)) || keyNorm(expected).includes(keyNorm(actual));
      return similarity(expected, actual) >= 0.5 || plain(actual).includes(plain(expected)) || plain(expected).includes(plain(actual));
  }
}

function compareCollection(slug, expected, actual, shiftDays, out) {
  const keys = [...(SCHEMA[slug]?.keys ?? []), ...(slug === "people" ? ["phone"] : [])];
  const score = (e, a) => {
    for (const key of keys) if (e.values[key] && a.values[key]) return keyNorm(e.values[key]) === keyNorm(a.values[key]) ? 3 : -1;
    const s = similarity(e.title, a.title);
    if (s >= 0.5) return 1 + s;
    const shared = e.sources.some((x) => a.sources.includes(x));
    return shared && s >= 0.2 ? 0.5 + s : 0;
  };
  const candidates = [];
  expected.forEach((e, i) => actual.forEach((a, j) => { const s = score(e, a); if (s > 0.5) candidates.push([s, i, j]); }));
  candidates.sort((x, y) => y[0] - x[0]);
  const pairs = [];
  const usedExpected = new Set();
  const unclaimed = new Set(actual.map((_, j) => j));
  for (const [, i, j] of candidates) {
    if (!usedExpected.has(i) && unclaimed.has(j)) {
      usedExpected.add(i);
      unclaimed.delete(j);
      pairs.push([i, j]);
    }
  }
  const label = (r) => `${r.title}  [${r.sources.join(",")}]`;
  out.collections[slug] = { expected: expected.length, actual: actual.length, matched: pairs.length };
  out.missed[slug] = expected.filter((_, i) => !usedExpected.has(i)).map(label);
  out.extra[slug] = [];
  out.duplicates[slug] = [];
  for (const j of unclaimed) {
    const twin = pairs.find(([i]) => score(expected[i], actual[j]) > 0.5);
    if (twin) out.duplicates[slug].push(`${label(actual[j])}  ≈ ${expected[twin[0]].title}`);
    else out.extra[slug].push(label(actual[j]));
  }
  for (const [i, j] of pairs) {
    for (const [field, value] of Object.entries(expected[i].values)) {
      const type = SCHEMA[slug]?.fields[field];
      if (!type || value == null || field === "notes") continue;
      const ok = fieldEqual(type, value, actual[j].values[field], shiftDays);
      const counts = (out.fields[`${slug}.${field}`] ??= { ok: 0, shifted: 0, total: 0 });
      counts.total++;
      if (ok) counts.ok++;
      if (ok === "shifted") counts.shifted++;
      if (!ok) out.wrong.push(`${slug}.${field} of "${expected[i].title}": expected ${JSON.stringify(value)}, got ${JSON.stringify(actual[j].values[field] ?? null)}`);
    }
  }
}

/**
 * @param dataset {messages, endState, insights} of ai-eval/business
 * @param live    {collections, emails, records, proposals, relations} as dumped
 */
export function compareLive(dataset, live, { shiftDays = 0 } = {}) {
  const out = { shiftDays, collections: {}, fields: {}, missed: {}, extra: {}, duplicates: {}, wrong: [] };
  const slugById = new Map(live.collections.map((c) => [c.id, c.slug]));
  const actual = {};
  for (const [slug, rows] of Object.entries(live.records)) {
    actual[slug] = rows.map(({ row, sources }) => ({ title: row.title ?? "", values: valuesOf(slug, row), sources: sources ?? [] }));
  }
  for (const slug of new Set([...Object.keys(dataset.endState.records), ...Object.keys(actual)])) {
    compareCollection(slug, dataset.endState.records[slug] ?? [], actual[slug] ?? [], shiftDays, out);
  }

  // per mail: labelled key records without any proposal of that collection from the mail
  const recordProposals = live.proposals.filter((p) => p.kind === "CREATE_RECORD" || p.kind === "UPDATE_RECORD");
  const missedByCollection = {};
  const expectedByCollection = {};
  const mails = [];
  for (const m of dataset.messages.filter(isLabelled)) {
    const mine = recordProposals.filter((p) => p.email === m.id);
    const misses = [];
    for (const slug of KEY_COLLECTIONS) {
      const expected = m.expected.records[slug] ?? [];
      if (!expected.length) continue;
      expectedByCollection[slug] = (expectedByCollection[slug] ?? 0) + expected.length;
      const got = mine.filter((p) => slugById.get(p.payload.collectionId) === slug).length;
      if (got < expected.length) {
        missedByCollection[slug] = (missedByCollection[slug] ?? 0) + expected.length - got;
        misses.push(`${slug}: ${expected.map((e) => e.title).join(" / ")} (got ${got})`);
      }
    }
    if (misses.length) mails.push({ id: m.id, storyline: m.storyline, subject: m.email.subject, nothingProduced: mine.length === 0, misses });
  }
  out.perMail = { mailsWithMisses: mails.length, missedByCollection, expectedByCollection, mails };

  // replies
  const noise = new Set(dataset.messages.filter((m) => m.storyline === "noise").map((m) => m.id));
  const drafts = new Map(live.proposals.filter((p) => p.kind === "DRAFT_REPLY").map((p) => [p.email, p]));
  const replies = { needed: 0, neededDrafted: 0, answered: 0, answeredDrafted: 0, courtesy: 0, courtesyDrafted: 0, missedNeeded: [], unneeded: [],
    onNoise: [...drafts.keys()].filter((id) => noise.has(id)).sort() };
  for (const m of dataset.messages.filter(isLabelled)) {
    const draft = drafts.get(m.id);
    if (m.needsReply && m.reply?.answeredBy) {
      replies.answered++;
      if (draft) replies.answeredDrafted++;
    } else if (m.needsReply) {
      replies.needed++;
      if (draft) replies.neededDrafted++;
      else replies.missedNeeded.push(`${m.id} ${m.email.subject}`);
    } else if (m.reply?.intent === "COURTESY") {
      replies.courtesy++;
      if (draft) replies.courtesyDrafted++;
    } else if (draft) {
      replies.unneeded.push(`${m.id} ${m.email.subject} (${draft.payload.intent})`);
    }
  }
  out.replies = replies;

  // insights: found when one proposed insight cites all evidence mails
  const messageByDbId = new Map(live.emails.map((e) => [e.id, e.email]));
  const proposed = live.proposals.filter((p) => p.kind === "INSIGHT")
    .map((p) => ({ title: p.payload.title, evidence: (p.payload.evidenceEmailIds ?? []).map((id) => messageByDbId.get(id)) }));
  out.insights = dataset.insights.map((e) => ({ title: e.title, found: proposed.some((p) => e.evidence.every((x) => p.evidence.includes(x))) }));
  out.insightsProposed = proposed.map((p) => p.title);
  out.noiseRecords = recordProposals.filter((p) => noise.has(p.email)).map((p) => `${p.email} ${slugById.get(p.payload.collectionId)}: ${p.payload.title}`);
  return out;
}
