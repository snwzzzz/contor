import { test } from "node:test";
import assert from "node:assert/strict";
import { buildMailbox } from "../lib/merge.mjs";
import { amountVariants, dateVariants, validate } from "../lib/validate.mjs";
import { storyline, world } from "./fixtures.mjs";

function check(storylines, noise = []) {
  const w = world();
  const { messages } = buildMailbox(w, storylines, noise);
  return validate(w, storylines, messages);
}

function only(result, validator) {
  return result.errors.filter((e) => e.startsWith(validator + ":"));
}

test("a consistent storyline passes every validator", () => {
  assert.deepEqual(check([storyline()]).errors, []);
});

test("amounts and dates are recognised in their usual German and English spellings", () => {
  assert.ok(amountVariants(1234.5).includes("1.234,50"));
  assert.ok(amountVariants(1234.5).includes("1,234.50"));
  assert.ok(amountVariants(860).includes("860"));
  assert.ok(dateVariants("2026-08-12").includes("12.08.2026"));
  assert.ok(dateVariants("2026-08-12").includes("12.8."));
  assert.ok(dateVariants("2026-08-12").includes("12. august"));
  assert.ok(dateVariants("2026-08-12").includes("aug 12"));
  assert.ok(dateVariants("2026-08-12").includes("12/08"));
});

test("fictional: real-looking domains, phone numbers, IBANs and denied names fail", () => {
  const s = storyline();
  s.beats[0].body += "\nwww.siemens.de, Tel. +49 521 123456, IBAN DE89 3704 0044 0532 0130 00";
  const errors = only(check([s]), "fictional");

  assert.ok(errors.some((e) => e.includes("siemens.de")));
  assert.ok(errors.some((e) => e.includes("+49 521 123456")));
  assert.ok(errors.some((e) => e.includes("IBAN")));
  assert.ok(errors.some((e) => e.includes("Siemens")));
});

test("fact check: a labelled value the body does not state fails unless it is marked derived", () => {
  const s = storyline();
  s.beats[0].labels.records.sales_orders[0].values.requested_delivery = "2026-08-03";
  assert.equal(only(check([s]), "fact").length, 1);

  s.beats[0].labels.derived = { "sales_orders.requested_delivery": "KW 32 → Monday" };
  assert.deepEqual(only(check([s]), "fact"), []);
});

test("fact check: values stated in a mail the beat depends on count", () => {
  const s = storyline();
  s.beats.push({
    key: "later", day: "2026-07-10", time: "08:00", from: "p-wendt", subject: "Erinnerung", body: "Wie besprochen – bitte um Rückmeldung.",
    style: { lang: "de" },
    labels: {
      records: { sales_orders: [{ title: "Kessler SO-26-0142", values: { order_number: "SO-26-0142", total: { amount: 1234.5, currency: "EUR" } } }] },
      needsReply: true, importance: "NORMAL", dependsOn: ["#ask"],
    },
  });
  assert.deepEqual(only(check([s]), "fact"), []);
});

test("extra facts: an order-shaped number the beat does not own fails", () => {
  const s = storyline();
  s.beats[0].body += "\nSiehe auch SO-26-0999.";
  assert.ok(only(check([s]), "number").some((e) => e.includes("SO-26-0999")));
});

test("numbers: two storylines declaring the same number fail", () => {
  const other = storyline({ id: "A1-other" });
  assert.ok(only(check([storyline(), other]), "number").some((e) => e.includes("SO-26-0142")));
});

test("time order: replying before the parent, and business mail at the weekend, fail", () => {
  const s = storyline();
  s.beats[1].day = "2026-07-06";
  assert.ok(only(check([s]), "time").some((e) => e.includes("before")));

  const weekend = storyline();
  weekend.beats[0].day = "2026-07-11";
  weekend.beats[1].day = "2026-07-11";
  assert.ok(only(check([weekend]), "time").some((e) => e.includes("weekend")));
  weekend.beats[0].weekend = true;
  weekend.beats[1].weekend = true;
  assert.deepEqual(only(check([weekend]), "time"), []);
});

test("schema: unknown collections or fields, records on sent mail and a missing reply label fail", () => {
  const s = storyline();
  s.beats[0].labels.records.sales_orders[0].values.colour = "red";
  s.beats[0].labels.records.widgets = [{ title: "x", values: {} }];
  s.beats[1].labels = { records: { tasks: [{ title: "Preis prüfen", values: {} }] } };
  delete s.beats[0].labels.needsReply;
  const errors = only(check([s]), "schema");

  assert.ok(errors.some((e) => e.includes("colour")));
  assert.ok(errors.some((e) => e.includes("widgets")));
  assert.ok(errors.some((e) => e.includes("sent")));
  assert.ok(errors.some((e) => e.includes("needsReply")));
});

test("schema: a relation must connect the collections its type connects", () => {
  const s = storyline();
  s.beats[0].labels.records.shipments = [{ title: "Kiebitz KP402611870093", values: {} }];
  s.beats[0].labels.records.purchase_orders = [{ title: "Anadolu PO-26-1088", values: {} }];
  s.beats[0].labels.relations = [{ type: "ships", from: "shipments:Kiebitz KP402611870093", to: "purchase_orders:Anadolu PO-26-1088" }];
  assert.ok(only(check([s]), "schema").some((e) => e.includes("ships connects shipments -> sales_orders")));

  s.beats[0].labels.relations[0].type = "delivers";
  assert.deepEqual(only(check([s]), "schema"), []);
});

test("language: a mail in the wrong language fails", () => {
  const s = storyline();
  s.beats[0].style.lang = "en";
  assert.equal(only(check([s]), "language").length, 1);
});
