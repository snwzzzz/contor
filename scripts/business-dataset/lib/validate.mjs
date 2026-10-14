// Deterministic validators of the business dataset (business-dataset-prompts.md, section 7). Every error starts with
// the validator's name: schema, fictional, number, time, fact, language.

import { COURTESY_KINDS, IMPORTANCE, INTENTS, RELATION_ENDS, RELATION_TYPES, SCHEMA, TIME_OF_DAY } from "./schema.mjs";
import { isWeekend } from "./time.mjs";

const MONTHS_EN = ["january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december"];
const MONTHS_DE = ["januar", "februar", "märz", "april", "mai", "juni", "juli", "august", "september", "oktober", "november", "dezember"];
const DEFAULT_NUMBER_PATTERNS = ["\\b(?:SO|PO|Q)-26-\\d{2,4}\\b", "\\bRE-2026-\\d{2,4}\\b", "\\bRMA-26-\\d{2,3}\\b"];
const LOOSE_TEXT_FIELDS = new Set(["customer", "supplier", "issuer", "carrier", "driver", "name", "full_name", "location", "role_title"]);
const SKIPPED_FIELDS = new Set(["country", "notes", "payment_terms", "website"]);
const NAME_NOISE = new Set(["gmbh", "co", "kg", "ag", "sp", "z", "o", "s", "l", "r", "a", "the", "and", "und", "b", "v", "as", "ab", "aps", "sarl", "sas", "srl", "sro", "d", "oo"]);
const STOP_DE = new Set("und der die das ich nicht mit wir sie ist bitte für auf ein eine zu den dem uns ihre ihr vielen dank grüße gruß freundlichen unserer noch da geht über bei wie haben sind wird können kann sich auch es hallo guten tag herr frau morgen heute schon bis".split(" "));
const STOP_EN = new Set("the and to of we you is please for on with our your thanks regards will can this that are be have it in at as from would could let me know best dear hi hello kind just".split(" "));

export function plain(text) {
  return String(text ?? "").normalize("NFD").replace(/\p{M}+/gu, "").replace(/[  ]/g, " ").replace(/\s+/g, " ").trim().toLowerCase();
}

function alnum(text) {
  return plain(text).replace(/[^a-z0-9]/g, "");
}

export function amountVariants(amount) {
  const fixed = Number(amount).toFixed(2);
  const [whole, cents] = fixed.split(".");
  const group = (sep) => whole.replace(/\B(?=(\d{3})+(?!\d))/g, sep);
  const variants = new Set([`${group(".")},${cents}`, `${group(",")}.${cents}`, `${whole},${cents}`, `${whole}.${cents}`,
    `${group(" ")},${cents}`, `${group("'")}.${cents}`]);
  if (cents === "00") [group("."), group(","), whole, group(" "), group("'"), `${group(".")},-`].forEach((v) => variants.add(v));
  return [...variants];
}

export function dateVariants(iso) {
  const [y, m, d] = iso.split("-").map(Number);
  const dd = String(d).padStart(2, "0");
  const mm = String(m).padStart(2, "0");
  const yy = String(y).slice(2);
  const en = MONTHS_EN[m - 1];
  const de = MONTHS_DE[m - 1];
  const suffix = d % 10 === 1 && d !== 11 ? "st" : d % 10 === 2 && d !== 12 ? "nd" : d % 10 === 3 && d !== 13 ? "rd" : "th";
  return [...new Set([
    iso, `${dd}.${mm}.${y}`, `${d}.${m}.${y}`, `${dd}.${mm}.${yy}`, `${dd}.${mm}.`, `${d}.${m}.`, `${d}.${mm}.`, `${dd}.${m}.`,
    `${dd}/${mm}/${y}`, `${mm}/${dd}/${y}`, `${dd}/${mm}`, `${mm}/${dd}`, `${d}/${m}`, `${m}/${d}`,
    `${d}. ${de}`, `${d}. ${de.slice(0, 3)}`, `${d} ${en}`, `${d} ${en.slice(0, 3)}`, `${en} ${d}`, `${en.slice(0, 3)} ${d}`,
    `${d}${suffix} ${en}`, `${d}${suffix} of ${en}`, `${en} ${d}${suffix}`, `${dd} ${en.slice(0, 3)}`, `${dd}. ${de}`,
  ])];
}

function contains(texts, variants) {
  return variants.some((variant) => texts.some((text) => text.includes(variant.toLowerCase())));
}

function valueFound(type, field, value, texts, plainTexts, alnumTexts) {
  switch (type) {
    case "MONEY":
      return value && typeof value === "object" ? contains(texts, amountVariants(value.amount)) : true;
    case "DATE":
      return contains(texts, dateVariants(String(value)));
    case "DATETIME":
      return contains(texts, dateVariants(String(value).slice(0, 10)));
    case "NUMBER":
      return contains(texts, [String(value), String(value).replace(".", ",")]);
    case "EMAIL":
      return contains(texts, [String(value)]);
    case "PHONE": {
      const digits = String(value).replace(/[^0-9]/g, "");
      return texts.some((text) => text.replace(/[^0-9]/g, "").includes(digits));
    }
    case "TEXT": {
      if (/,\s/.test(String(value)) && /\d/.test(String(value))) { // a list of references, e.g. "SO-26-0158, SO-26-0161"
        return String(value).split(/,\s*/).every((part) => valueFound(type, field, part, texts, plainTexts, alnumTexts));
      }
      if (/\d/.test(String(value))) return alnumTexts.some((text) => text.includes(alnum(value)));
      if (plainTexts.some((text) => text.includes(plain(value)))) return true;
      if (!LOOSE_TEXT_FIELDS.has(field)) return false;
      const tokens = plain(value).split(/[^\p{L}\p{N}]+/u).filter((t) => t.length >= 3 && !NAME_NOISE.has(t));
      return tokens.some((token) => plainTexts.some((text) => text.includes(token)));
    }
    default:
      return true;
  }
}

function newText(body) {
  const cut = body.search(/^(?:Von:|From:|-----\s*Original|Am .* schrieb|On .* wrote|>)/m);
  return cut >= 0 ? body.slice(0, cut) : body;
}

function detectLanguage(text) {
  const words = plain(text).split(/[^\p{L}]+/u).filter(Boolean);
  const de = words.filter((w) => STOP_DE.has(w)).length;
  const en = words.filter((w) => STOP_EN.has(w)).length;
  if (de + en < 3 || de === en) return null;
  return de > en ? "de" : "en";
}

export function validate(world, storylines, messages) {
  const errors = [];
  const warnings = [];
  const byId = new Map(messages.map((m) => [m.id, m]));
  const patterns = [...DEFAULT_NUMBER_PATTERNS, ...(world.numberPatterns ?? [])].map((p) => new RegExp(p, "g"));

  // schema (storyline level)
  for (const story of storylines) {
    for (const [i, beat] of (story.beats ?? []).entries()) {
      const ref = `${story.id}#${beat.key ?? `b${i + 1}`}`;
      if (beat.direction === "sent" && Object.keys(beat.labels?.records ?? {}).length > 0) {
        errors.push(`schema: sent beat ${ref} carries records (sent mail is never extracted)`);
      }
      if (isWeekend(beat.day) && !beat.weekend) errors.push(`time: ${ref} is business mail on a weekend (${beat.day}) without weekend: true`);
    }
  }

  // numbers: each declared by one storyline only
  const owner = new Map();
  for (const story of storylines) {
    for (const number of story.numbers ?? []) {
      if (owner.has(number) && owner.get(number) !== story.id) {
        errors.push(`number: ${number} is declared by ${owner.get(number)} and ${story.id}`);
      }
      owner.set(number, story.id);
    }
  }
  for (const number of world.ledger?.reserved ?? []) owner.set(number, "world");

  for (const message of messages) {
    const where = `${message.id} (${message.storyline}#${message.beat})`;
    const text = `${message.email.subject ?? ""}\n${message.email.body ?? ""}`;
    const inboundStory = message.folder === "inbox" && message.storyline !== "noise";

    // fictional
    const addresses = [message.email.fromAddress, ...(message.email.to ?? []), ...(message.cc ?? []),
      ...(text.match(/[\w.+-]+@[\w-]+(?:\.[\w-]+)+/g) ?? [])];
    for (const addr of addresses) {
      if (addr && !/\.example$/i.test(addr.replace(/[.,;:)]+$/, ""))) errors.push(`fictional: address ${addr} in ${where} is not *.example`);
    }
    for (const url of text.match(/\b(?:https?:\/\/|www\.)[^\s<>")]+/gi) ?? []) {
      const host = url.replace(/^https?:\/\//i, "").split(/[/?#]/)[0].replace(/[.,;:]+$/, "");
      if (!/\.example$/i.test(host)) errors.push(`fictional: link ${url} in ${where} is not *.example`);
    }
    for (const phone of text.match(/\+\d{2,3}[ \d/()-]{6,}\d/g) ?? []) {
      if (!/^\+\d{2,3}(?: \d{1,4})? 5550 \d{3,4}$/.test(phone.trim())) errors.push(`fictional: phone ${phone.trim()} in ${where} is not in the 5550 pattern`);
    }
    if (/\b[A-Z]{2}\d{2}(?: ?[A-Z0-9]{4}){3,}/.test(text)) errors.push(`fictional: IBAN-like number in ${where}`);
    for (const name of world.denyList ?? []) {
      if (new RegExp(`\\b${name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}\\b`, "i").test(`${text}\n${message.email.fromName}`)) {
        errors.push(`fictional: denied name "${name}" in ${where}`);
      }
    }

    // numbers in the text must be declared
    for (const pattern of patterns) {
      for (const number of text.match(pattern) ?? []) {
        if (!owner.has(number)) errors.push(`number: ${number} in ${where} is not declared by any storyline`);
      }
    }

    // time order
    for (const ref of [message.inReplyTo, ...(message.dependsOn ?? [])].filter(Boolean)) {
      const parent = byId.get(ref);
      if (parent && parent.email.receivedAt >= message.email.receivedAt) {
        errors.push(`time: ${where} is dated before the message it refers to, ${ref}`);
      }
    }

    if (!inboundStory) continue;

    // schema (message level)
    if (typeof message.needsReply !== "boolean") errors.push(`schema: needsReply missing in ${where}`);
    if (!IMPORTANCE.includes(message.importance)) errors.push(`schema: importance missing or invalid in ${where}`);
    if (message.reply) {
      if (message.reply.intent && !INTENTS.includes(message.reply.intent)) errors.push(`schema: intent ${message.reply.intent} in ${where}`);
      if (message.reply.courtesyKind && !COURTESY_KINDS.includes(message.reply.courtesyKind)) errors.push(`schema: courtesyKind in ${where}`);
      if (message.reply.timeOfDay && !TIME_OF_DAY.includes(message.reply.timeOfDay)) errors.push(`schema: timeOfDay in ${where}`);
    }
    for (const [collection, records] of Object.entries(message.expected.records)) {
      if (!SCHEMA[collection]) {
        errors.push(`schema: unknown collection ${collection} in ${where}`);
        continue;
      }
      for (const record of records) {
        if (!record.title?.trim()) errors.push(`schema: record without title in ${collection} of ${where}`);
        for (const field of Object.keys(record.values ?? {})) {
          if (!SCHEMA[collection].fields[field]) errors.push(`schema: unknown field ${collection}.${field} in ${where}`);
        }
      }
    }
    for (const relation of message.expected.relations) {
      if (!RELATION_TYPES.includes(relation.type)) errors.push(`schema: relation type ${relation.type} in ${where}`);
      const ends = [relation.from, relation.to].map((end) => String(end).split(":")[0]);
      const allowed = RELATION_ENDS[relation.type];
      if (allowed && (ends[0] !== allowed[0] || ends[1] !== allowed[1])) {
        errors.push(`schema: ${relation.type} connects ${allowed[0]} -> ${allowed[1]}, not ${ends[0]} -> ${ends[1]} in ${where}`);
      }
      for (const end of [relation.from, relation.to]) {
        const [collection, ...title] = String(end).split(":");
        const titles = (message.expected.records[collection] ?? []).map((r) => plain(r.title));
        if (!titles.includes(plain(title.join(":")))) errors.push(`schema: relation end ${end} is not a labelled record of ${where}`);
      }
    }
    for (const forbidden of message.mustNotProduce) {
      if (!SCHEMA[forbidden.collection]) errors.push(`schema: mustNotProduce collection ${forbidden.collection} in ${where}`);
    }

    // facts: every labelled value is stated in this mail or a mail it depends on, or marked derived
    const sources = [message, ...(message.dependsOn ?? []).map((id) => byId.get(id)).filter(Boolean)];
    const texts = sources.flatMap((m) => [m.email.subject ?? "", m.email.body ?? "", m.email.fromAddress ?? "", ...(m.email.to ?? []), ...(m.cc ?? [])])
      .map((t) => t.toLowerCase().replace(/[  ]/g, " "));
    const plainTexts = texts.map(plain);
    const alnumTexts = texts.map(alnum);
    const derived = message.derived ?? {};
    for (const [collection, records] of Object.entries(message.expected.records)) {
      if (!SCHEMA[collection]) continue;
      for (const record of records) {
        for (const [field, value] of Object.entries(record.values ?? {})) {
          if (value == null || SKIPPED_FIELDS.has(field) || `${collection}.${field}` in derived) continue;
          const type = SCHEMA[collection].fields[field];
          if (type && !valueFound(type, field, value, texts, plainTexts, alnumTexts)) {
            errors.push(`fact: ${collection}.${field} = ${JSON.stringify(value)} of "${record.title}" is not stated in ${where} or its dependencies`);
          }
        }
      }
    }

    // language of the new text
    const requested = message.lang;
    const detected = detectLanguage(newText(message.email.body ?? ""));
    if ((requested === "de" || requested === "en") && detected && detected !== requested) {
      errors.push(`language: ${where} should be ${requested} but reads as ${detected}`);
    }
  }
  return { errors, warnings };
}
