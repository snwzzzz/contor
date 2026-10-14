// Phase E: the expected content of every collection after the whole mailbox, folded from the per-mail labels in time
// order. Records are the same entity when they share a match-key value, a normalized title or an explicit `entity`;
// later values win, the longest title wins (ties: the first). Sent mail and duplicate copies are never extracted.

import { SCHEMA } from "./schema.mjs";
import { plain } from "./validate.mjs";

function keyValue(type, value) {
  if (value == null || value === "") return null;
  if (type === "PHONE") return String(value).replace(/[^0-9]/g, "");
  return plain(value).replace(/[^\p{L}\p{N}@.]+/gu, "");
}

export function endState(messages) {
  const records = {};
  const relations = [];
  const titleOf = new Map(); // `${collection}:${plain(title)}` of a label -> its entity
  const retracted = [];

  for (const message of messages) {
    if (message.folder === "sent" || message.duplicateOf) continue;
    for (const [collection, labelled] of Object.entries(message.expected?.records ?? {})) {
      const schema = SCHEMA[collection] ?? { fields: {}, keys: [] };
      const entities = (records[collection] ??= []);
      for (const label of labelled) {
        const keys = new Set([`title:${keyValue("TEXT", label.title)}`]);
        if (label.entity) keys.add(`entity:${label.entity}`);
        for (const key of schema.keys) {
          const value = keyValue(schema.fields[key], label.values?.[key]);
          if (value) keys.add(`${key}:${value}`);
        }
        const matching = entities.filter((entity) => [...keys].some((key) => entity.keys.has(key)));
        let target = matching[0];
        if (!target) {
          target = { title: label.title, values: {}, sources: [], keys: new Set() };
          entities.push(target);
        }
        for (const other of matching.slice(1)) { // a label joining two entities merges them
          other.keys.forEach((key) => target.keys.add(key));
          target.values = { ...target.values, ...other.values };
          target.sources = [...new Set([...target.sources, ...other.sources])].sort();
          if (plain(other.title).length > plain(target.title).length) target.title = other.title;
          entities.splice(entities.indexOf(other), 1);
          for (const [title, entity] of titleOf) if (entity === other) titleOf.set(title, target);
        }
        keys.forEach((key) => target.keys.add(key));
        Object.assign(target.values, label.values ?? {});
        if (!target.sources.includes(message.id)) target.sources.push(message.id);
        if (plain(label.title).length > plain(target.title).length) target.title = label.title;
        titleOf.set(`${collection}:${plain(label.title)}`, target);
      }
    }
    for (const relation of message.expected?.relations ?? []) {
      relations.push({ ...relation, source: message.id });
    }
    for (const ref of message.retracts ?? []) { // "sorry, wrong recipient": the record should not exist after all
      const [collection, ...rest] = ref.split(":");
      const entity = titleOf.get(`${collection}:${plain(rest.join(":"))}`);
      const entities = records[collection] ?? [];
      if (entity && entities.includes(entity)) entities.splice(entities.indexOf(entity), 1);
      retracted.push({ record: ref, by: message.id });
    }
  }

  const finalTitle = (end) => {
    const [collection, ...rest] = String(end).split(":");
    const entity = titleOf.get(`${collection}:${plain(rest.join(":"))}`);
    return entity ? `${collection}:${entity.title}` : end;
  };
  const folded = [];
  for (const relation of relations) {
    const from = finalTitle(relation.from);
    const to = finalTitle(relation.to);
    const existing = folded.find((r) => r.type === relation.type && r.from === from && r.to === to);
    if (existing) {
      if (!existing.sources.includes(relation.source)) existing.sources.push(relation.source);
    } else {
      folded.push({ type: relation.type, from, to, sources: [relation.source] });
    }
  }

  const out = {};
  for (const [collection, entities] of Object.entries(records)) {
    out[collection] = entities.map(({ title, values, sources }) => ({ title, values, sources }));
  }
  return { records: out, relations: folded, retracted };
}
