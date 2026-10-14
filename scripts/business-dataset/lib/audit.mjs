// Phase L: the independent label audit. auditInput() is what the auditor sees (the mail and the earlier mails it may
// depend on, no labels, facts or editor notes); compareLabels() diffs the auditor's labels against the storyline labels.

import { SCHEMA } from "./schema.mjs";
import { plain } from "./validate.mjs";

const SIMILAR = 0.5;

export function auditInput(message, byId) {
  const email = (m) => ({
    folder: m.folder, fromName: m.email.fromName, fromAddress: m.email.fromAddress, to: m.email.to, cc: m.cc ?? [],
    subject: m.email.subject, receivedAt: m.email.receivedAt, body: m.email.body,
  });
  return {
    id: message.id,
    email: { mailbox: message.email.mailbox, ...email(message) },
    dependsOn: (message.dependsOn ?? []).map((id) => byId.get(id)).filter(Boolean).map(email),
  };
}

function tokens(text) {
  return new Set(plain(text).split(/[^\p{L}\p{N}]+/u).filter(Boolean));
}

function similarity(a, b) {
  const left = tokens(a);
  const right = tokens(b);
  if (!left.size || !right.size) return 0;
  const common = [...left].filter((t) => right.has(t)).length;
  return common / new Set([...left, ...right]).size;
}

function sameRecord(collection, a, b) {
  for (const key of SCHEMA[collection]?.keys ?? []) {
    const x = a.values?.[key];
    const y = b.values?.[key];
    if (x && y) return plain(x).replace(/[^a-z0-9@.]/g, "") === plain(y).replace(/[^a-z0-9@.]/g, "");
  }
  return similarity(a.title, b.title) >= SIMILAR;
}

/** Pairs our and their records per collection (one-to-one, first fit); returns the unmatched ones of each side. */
function matchRecords(ours, theirs) {
  const onlyOurs = [];
  const onlyTheirs = [];
  let matched = 0;
  let total = 0;
  for (const collection of new Set([...Object.keys(ours), ...Object.keys(theirs)])) {
    const left = ours[collection] ?? [];
    const right = [...(theirs[collection] ?? [])];
    total += left.length + right.length;
    for (const record of left) {
      const index = right.findIndex((other) => sameRecord(collection, record, other));
      if (index >= 0) {
        right.splice(index, 1);
        matched++;
      } else {
        onlyOurs.push(`${collection}: ${record.title}`);
      }
    }
    right.forEach((record) => onlyTheirs.push(`${collection}: ${record.title}`));
  }
  return { matched, total, onlyOurs, onlyTheirs };
}

export function compareLabels(messages, audited) {
  const theirsById = new Map(audited.map((a) => [a.id, a]));
  const counts = { needsReply: [0, 0], importance: [0, 0], intent: [0, 0], records: [0, 0] };
  const disagreements = [];
  for (const ours of messages) {
    const theirs = theirsById.get(ours.id);
    if (!theirs) continue;
    const fields = [];
    const tally = (field, agree) => {
      counts[field][1]++;
      if (agree) counts[field][0]++;
      else fields.push(field);
    };
    tally("needsReply", ours.needsReply === theirs.needsReply);
    tally("importance", ours.importance === theirs.importance);
    const ourIntent = ours.reply?.intent ?? "NONE";
    const theirIntent = theirs.reply?.intent ?? "NONE";
    if (ourIntent !== "NONE" || theirIntent !== "NONE") tally("intent", ourIntent === theirIntent);
    const records = matchRecords(ours.expected?.records ?? {}, theirs.records ?? {});
    counts.records[0] += 2 * records.matched;
    counts.records[1] += records.total;
    if (records.onlyOurs.length || records.onlyTheirs.length) fields.push("records");
    if (fields.length) {
      disagreements.push({
        id: ours.id, storyline: ours.storyline, fields,
        ours: { needsReply: ours.needsReply, importance: ours.importance, intent: ourIntent },
        theirs: { needsReply: theirs.needsReply, importance: theirs.importance, intent: theirIntent, uncertain: theirs.uncertain ?? [] },
        records: { onlyOurs: records.onlyOurs, onlyTheirs: records.onlyTheirs },
      });
    }
  }
  const rates = Object.fromEntries(Object.entries(counts).map(([field, [agree, total]]) => [field, total ? agree / total : null]));
  return { rates, disagreements };
}
