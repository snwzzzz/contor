// Phase T: storylines (beats with labels and rendered text) + noise -> one mailbox in time order.
// Deterministic: the same inputs always give the same message ids, conversations and expected records.

import { toInstant } from "./time.mjs";
import { SCHEMA } from "./schema.mjs";

const OOO_TEXT = {
  de: (person, back) => `Vielen Dank für Ihre Nachricht. Ich bin bis einschließlich ${back} nicht im Büro und habe nur eingeschränkt Zugriff auf meine E-Mails. `
    + `Ihre Nachricht wird nicht weitergeleitet.\n\nMit freundlichen Grüßen\n${person.name}`,
  en: (person, back) => `Thank you for your email. I am out of the office until ${back} with limited access to email. `
    + `Your message will not be forwarded.\n\nBest regards\n${person.name}`,
};
const OOO_SUBJECT = { de: "Automatische Antwort: ", en: "Automatic reply: " };

function index(list) {
  return new Map((list ?? []).map((item) => [item.id, item]));
}

function substitute(text, vars) {
  return (text ?? "").replace(/\{\{(\w+)\}\}/g, (_, name) => (vars && name in vars ? String(vars[name]) : `{{${name}}}`));
}

function deepCopy(value) {
  return value === undefined ? undefined : JSON.parse(JSON.stringify(value));
}

function digits(text) {
  return (text ?? "").replace(/[^0-9]/g, "");
}

function plain(text) {
  return String(text ?? "").normalize("NFD").replace(/\p{M}+/gu, "").replace(/\s+/g, " ").trim().toLowerCase();
}

export function buildMailbox(world, storylines, noise) {
  const errors = [];
  const people = index(world.people);
  const companies = index(world.companies);
  const owner = people.get(world.owner);
  const items = [];

  const personFor = (id, where) => {
    const person = people.get(id);
    if (!person) errors.push(`merge: unknown person ${id} in ${where}`);
    return person;
  };
  const companyOf = (person) => (person?.company ? companies.get(person.company) : null);
  const zoneOf = (person) => person?.zone ?? companyOf(person)?.zone ?? world.company.zone;
  const address = (entry, where) => {
    if (typeof entry === "string" && entry.includes("@")) return entry;
    return personFor(entry, where)?.email;
  };

  // 1. Beats -> items (one per receiving account)
  storylines.forEach((story, storyIndex) => {
    (story.beats ?? []).forEach((beat, beatIndex) => {
      const key = beat.key ?? `b${beatIndex + 1}`;
      const ref = `${story.id}#${key}`;
      const sent = beat.direction === "sent";
      const accounts = beat.accounts ?? [beat.account ?? "alex"];
      const sender = sent ? owner : personFor(beat.from, ref);
      if (!sender) return;
      const fromAddress = beat.fromAddress === "private" ? sender.privateEmail : beat.fromAddress ?? sender.email;
      const zone = beat.zone ?? (sent ? world.company.zone : zoneOf(sender));
      if (sent && !beat.to) errors.push(`merge: sent beat ${ref} has no recipients`);
      accounts.forEach((account, copy) => {
        items.push({
          ref, story, beat, key, beatIndex, storyIndex, copy, sent, account, sender, fromAddress,
          receivedAt: toInstant(beat.day, beat.time, zone),
          to: (beat.to ?? [world.accounts[account]]).map((r) => address(r, ref)),
          cc: (beat.cc ?? []).map((r) => address(r, ref)),
          subject: beat.subject, body: beat.body,
        });
      });
    });
  });

  // 2. Noise -> items
  (noise ?? []).forEach((entry, entryIndex) => {
    const instances = entry.repeat ?? [{ day: entry.day, time: entry.time, vars: {} }];
    instances.forEach((instance, i) => {
      const sender = entry.from ? personFor(entry.from, `noise ${entry.key}`) : { name: entry.fromName, email: entry.fromAddress };
      if (!sender) return;
      const account = entry.account ?? "alex";
      items.push({
        ref: instances.length > 1 ? `noise#${entry.key}-${i + 1}` : `noise#${entry.key}`, noise: entry, storyIndex: storylines.length + entryIndex,
        beatIndex: i, copy: 0, sent: false, account, sender, fromAddress: sender.email,
        receivedAt: toInstant(instance.day, instance.time, entry.zone ?? zoneOf(sender)),
        to: (entry.to ?? [world.accounts[account]]).map((r) => address(r, entry.key)), cc: [],
        subject: substitute(entry.subject, instance.vars), body: substitute(entry.body, instance.vars),
      });
    });
  });

  // 3. Out-of-office replies to the owner's mail to absent people
  for (const absence of world.absences ?? []) {
    const person = personFor(absence.person, "absences");
    if (!person) continue;
    for (const item of items.filter((it) => it.sent && it.copy === 0 && it.to.includes(person.email))) {
      const day = item.receivedAt.slice(0, 10);
      if (day < absence.from || day > absence.to) continue;
      const at = new Date(Date.parse(item.receivedAt) + 60000).toISOString().replace(".000Z", "Z");
      const lang = absence.lang ?? "de";
      items.push({
        ref: `noise#ooo-${item.ref}`, noise: { kind: "out-of-office", lang, inReplyTo: item.ref }, storyIndex: item.storyIndex, beatIndex: item.beatIndex + 0.5,
        copy: 0, sent: false, account: item.account, sender: person, fromAddress: person.email, receivedAt: at,
        to: [world.accounts[item.account]], cc: [], subject: OOO_SUBJECT[lang] + item.subject, body: OOO_TEXT[lang](person, absence.back),
      });
    }
  }

  // 4. Time order and ids
  items.sort((a, b) => a.receivedAt.localeCompare(b.receivedAt) || a.storyIndex - b.storyIndex || a.beatIndex - b.beatIndex || a.copy - b.copy);
  const idOf = new Map();
  items.forEach((item, i) => {
    item.id = `m-${String(i + 1).padStart(4, "0")}`;
    if (!idOf.has(item.ref)) idOf.set(item.ref, item.id);
  });
  const byRef = new Map();
  items.forEach((item) => { if (!byRef.has(item.ref)) byRef.set(item.ref, item); });

  const qualify = (ref, story) => (ref.startsWith("#") ? `${story?.id ?? "noise"}${ref}` : ref);
  const resolve = (ref, story, where) => {
    if (ref == null) return null;
    const id = idOf.get(qualify(ref, story));
    if (!id) errors.push(`merge: unresolved reference ${ref} in ${where}`);
    return id ?? null;
  };

  const conversations = new Map();
  const conversationOf = (item) => {
    if (conversations.has(item.ref)) return conversations.get(item.ref);
    conversations.set(item.ref, null); // cycle guard
    let conversation;
    const parentRef = item.noise ? item.noise.inReplyTo : item.beat.inReplyTo;
    const parent = parentRef ? byRef.get(qualify(parentRef, item.story)) : null;
    if (item.beat?.conversation) conversation = `c-${item.beat.conversation}`;
    else if (parent) conversation = conversationOf(parent);
    else conversation = item.noise ? `c-noise-${item.ref.slice(6)}` : `c-${item.story.id}`;
    conversations.set(item.ref, conversation);
    return conversation;
  };

  // 5. Messages
  const messages = items.map((item) => {
    const email = {
      mailbox: world.accounts[item.account], fromName: item.sender.name, fromAddress: item.fromAddress, to: item.to,
      subject: item.subject, receivedAt: item.receivedAt, body: item.body,
    };
    const base = {
      id: item.id, account: item.account, folder: item.sent ? "sent" : "inbox", conversationId: conversationOf(item), cc: item.cc,
    };
    if (item.copy > 0) base.duplicateOf = idOf.get(item.ref);

    if (item.noise) {
      return {
        ...base, storyline: "noise", beat: item.ref.slice(6), kind: item.noise.kind, lang: item.noise.lang,
        inReplyTo: resolve(item.noise.inReplyTo, null, item.ref), email,
        expected: { records: {}, relations: [] },
        mustNotProduce: Object.keys(SCHEMA).map((collection) => ({ collection, title: null })),
        needsReply: false, reply: null, importance: "LOW", dependsOn: [], tags: ["noise", item.noise.kind], facts: [],
      };
    }

    const labels = item.beat.labels ?? {};
    const where = item.ref;
    const reply = labels.reply ? { ...deepCopy(labels.reply) } : null;
    if (reply && "answeredBy" in reply) reply.answeredBy = resolve(reply.answeredBy, item.story, where);
    return {
      ...base, storyline: item.story.id, beat: item.key, category: item.story.category, lang: item.beat.style?.lang,
      inReplyTo: resolve(item.beat.inReplyTo, item.story, where), email,
      expected: item.sent ? { records: {}, relations: [] } : expected(item, labels),
      mustNotProduce: item.sent ? [] : deepCopy(labels.mustNotProduce ?? []),
      needsReply: item.sent ? null : labels.needsReply ?? null,
      reply: item.sent ? null : reply,
      importance: item.sent ? null : labels.importance ?? null,
      dependsOn: (labels.dependsOn ?? []).map((ref) => resolve(ref, item.story, where)).filter(Boolean),
      tags: labels.tags ?? [], storylineTags: item.story.tags ?? [],
      facts: item.beat.facts ?? [],
      ...(labels.derived ? { derived: labels.derived } : {}),
      ...(labels.retracts ? { retracts: labels.retracts } : {}),
    };
  });

  function expected(item, labels) {
    const records = deepCopy(labels.records ?? {});
    const relations = deepCopy(labels.relations ?? []);
    if (labels.contacts !== false) addContacts(item, records, relations);
    return { records, relations };
  }

  function addContacts(item, records, relations) {
    const person = item.sender;
    const company = companyOf(person);
    if (person.automated || company?.automated) return;
    const values = { email: item.fromAddress };
    const bodyDigits = digits(item.body);
    if (person.phone && bodyDigits.includes(digits(person.phone))) values.phone = person.phone;
    if (person.role && plain(item.body).includes(plain(person.role))) values.role_title = person.role;
    upsert(records, "people", person.name, values);

    const viaCompany = company && company.type !== "internal" && item.fromAddress?.endsWith("@" + company.domain);
    if (!viaCompany) return;
    upsert(records, "organizations", company.name, { domain: company.domain });
    const relation = { type: "works_at", from: `people:${person.name}`, to: `organizations:${company.name}` };
    if (!relations.some((r) => r.type === relation.type && plain(r.from) === plain(relation.from) && plain(r.to) === plain(relation.to))) {
      relations.unshift(relation);
    }
  }

  return { messages, errors };
}

/** Adds an automatic record unless the labels already have one with that title (then the labelled values win). */
function upsert(records, collection, title, values) {
  const list = records[collection] ?? [];
  const existing = list.find((r) => plain(r.title) === plain(title));
  if (existing) {
    existing.values = { ...values, ...existing.values };
  } else {
    list.unshift({ title, values });
  }
  records[collection] = list;
}
