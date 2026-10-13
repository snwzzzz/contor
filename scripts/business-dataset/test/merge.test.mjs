import { test } from "node:test";
import assert from "node:assert/strict";
import { toInstant } from "../lib/time.mjs";
import { buildMailbox } from "../lib/merge.mjs";
import { SCHEMA } from "../lib/schema.mjs";
import { storyline, world } from "./fixtures.mjs";

test("local times convert to UTC with the zone's summer offset", () => {
  assert.equal(toInstant("2026-07-07", "09:15", "Europe/Berlin"), "2026-07-07T07:15:00Z");
  assert.equal(toInstant("2026-07-07", "09:15", "Europe/Istanbul"), "2026-07-07T06:15:00Z");
  assert.equal(toInstant("2026-11-02", "09:15", "Europe/Berlin"), "2026-11-02T08:15:00Z");
});

test("messages are numbered in time order and threads resolve to message ids", () => {
  const { messages } = buildMailbox(world(), [storyline()], []);

  assert.deepEqual(messages.map((m) => m.id), ["m-0001", "m-0002"]);
  const [ask, answer] = messages;
  assert.equal(ask.email.receivedAt, "2026-07-07T07:15:00Z");
  assert.equal(ask.email.mailbox, "alex.morgan@nordhafen.example");
  assert.equal(ask.email.fromAddress, "s.wendt@kessler-maschinenbau.example");
  assert.deepEqual(ask.email.to, ["alex.morgan@nordhafen.example"]);
  assert.equal(ask.folder, "inbox");
  assert.equal(ask.conversationId, "c-kessler-price");
  assert.equal(answer.folder, "sent");
  assert.equal(answer.email.fromAddress, "alex.morgan@nordhafen.example");
  assert.deepEqual(answer.email.to, ["s.wendt@kessler-maschinenbau.example"]);
  assert.deepEqual(ask.cc, [], "cc is a top-level field (EmailContent has none)");
  assert.equal(answer.inReplyTo, "m-0001");
  assert.equal(answer.conversationId, "c-kessler-price", "a reply stays in its parent's conversation");
});

test("an inbound mail from a business contact gets the sender, the company and works_at as expected records", () => {
  const { messages } = buildMailbox(world(), [storyline()], []);
  const expected = messages[0].expected;

  assert.deepEqual(expected.records.people, [
    { title: "Sabine Wendt", values: { email: "s.wendt@kessler-maschinenbau.example", phone: "+49 521 5550 2201", role_title: "Einkauf" } },
  ]);
  assert.deepEqual(expected.records.organizations, [{ title: "Kessler Maschinenbau GmbH", values: { domain: "kessler-maschinenbau.example" } }]);
  assert.deepEqual(expected.relations, [{ type: "works_at", from: "people:Sabine Wendt", to: "organizations:Kessler Maschinenbau GmbH" }]);
  assert.equal(expected.records.sales_orders[0].values.order_number, "SO-26-0142");
});

test("sender details only count when the body states them, and contacts: false turns the automatic contacts off", () => {
  const s = storyline();
  s.beats[0].body = "Geht preislich noch etwas?\nS. Wendt";
  const plain = buildMailbox(world(), [s], []).messages[0].expected.records.people[0];
  assert.deepEqual(plain.values, { email: "s.wendt@kessler-maschinenbau.example" });

  s.beats[0].labels.contacts = false;
  assert.equal(buildMailbox(world(), [s], []).messages[0].expected.records.people, undefined);
});

test("sent mail carries no extraction labels and no reply label", () => {
  const { messages } = buildMailbox(world(), [storyline()], []);
  assert.deepEqual(messages[1].expected, { records: {}, relations: [] });
  assert.equal(messages[1].needsReply, null);
});

test("answeredBy and dependsOn resolve across storylines", () => {
  const first = storyline();
  first.beats[0].labels.reply.answeredBy = "#answer";
  const second = storyline({
    id: "E2-kessler-followup", numbers: [],
    beats: [{
      key: "f", day: "2026-07-09", time: "10:00", from: "p-wendt", subject: "Nachfrage", body: "Und?", style: { lang: "de" },
      labels: { needsReply: true, dependsOn: ["E2-kessler-price#ask"], importance: "NORMAL" },
    }],
  });
  const { messages } = buildMailbox(world(), [first, second], []);

  assert.equal(messages[0].reply.answeredBy, "m-0002");
  assert.deepEqual(messages[2].dependsOn, ["m-0001"]);
  assert.equal(messages[2].conversationId, "c-E2-kessler-followup", "a new thread defaults to the storyline id");
});

test("a beat delivered to two accounts becomes two messages, the second marked as duplicate", () => {
  const s = storyline();
  s.beats[0].accounts = ["orders", "alex"];
  const { messages } = buildMailbox(world(), [s], []);

  assert.equal(messages.length, 3);
  assert.equal(messages[0].account, "orders");
  assert.equal(messages[0].email.mailbox, "orders@nordhafen.example");
  assert.equal(messages[1].account, "alex");
  assert.equal(messages[1].duplicateOf, messages[0].id);
  assert.equal(messages[2].inReplyTo, messages[0].id);
});

test("the driver writing from his private address is a person without a company", () => {
  const s = storyline({
    id: "D-driver", numbers: [],
    beats: [{ key: "late", day: "2026-07-08", time: "11:00", from: "p-marek", fromAddress: "private", subject: "Verspätung",
      body: "Stehe auf der A2, komme 14:30.\nMarek\n+48 5550 4171", style: { lang: "de" }, labels: { needsReply: false, importance: "HIGH" } }],
  });
  const message = buildMailbox(world(), [s], []).messages[0];

  assert.equal(message.email.fromAddress, "marek.w84@pocztamail.example");
  assert.deepEqual(message.expected.records.people, [
    { title: "Marek Wiśniewski", values: { email: "marek.w84@pocztamail.example", phone: "+48 5550 4171" } },
  ]);
  assert.equal(message.expected.records.organizations, undefined);
});

test("noise mails expand their repeats, produce nothing and need no reply", () => {
  const noise = [{
    key: "erp-night", from: "p-rundschau", kind: "newsletter", lang: "de",
    repeat: [{ day: "2026-07-06", time: "06:00", vars: { n: "27" } }, { day: "2026-07-13", time: "06:00", vars: { n: "28" } }],
    subject: "Werkzeug-Rundschau KW {{n}}", body: "Die Themen der Woche {{n}}.",
  }];
  const { messages } = buildMailbox(world(), [], noise);

  assert.deepEqual(messages.map((m) => m.email.subject), ["Werkzeug-Rundschau KW 27", "Werkzeug-Rundschau KW 28"]);
  assert.equal(messages[0].storyline, "noise");
  assert.equal(messages[0].kind, "newsletter");
  assert.equal(messages[0].needsReply, false);
  assert.equal(messages[0].importance, "LOW");
  assert.deepEqual(messages[0].expected.records, {});
  assert.deepEqual(messages[0].mustNotProduce, Object.keys(SCHEMA).map((collection) => ({ collection, title: null })));
});

test("mail the owner sends to someone who is away gets an automatic out-of-office reply a minute later", () => {
  const w = world();
  w.absences = [{ person: "p-wendt", from: "2026-07-06", to: "2026-07-17", lang: "de", back: "20.07.2026" }];
  const { messages } = buildMailbox(w, [storyline()], []);

  assert.equal(messages.length, 3);
  const ooo = messages[2];
  assert.equal(ooo.kind, "out-of-office");
  assert.equal(ooo.email.fromAddress, "s.wendt@kessler-maschinenbau.example");
  assert.equal(ooo.email.receivedAt, "2026-07-07T12:01:00Z");
  assert.equal(ooo.inReplyTo, "m-0002");
  assert.equal(ooo.conversationId, "c-kessler-price");
  assert.match(ooo.email.subject, /^Automatische Antwort: AW: Preise SO-26-0142$/);
  assert.match(ooo.email.body, /20\.07\.2026/);
  assert.equal(ooo.needsReply, false);
});

test("unknown people and references are reported, not guessed", () => {
  const s = storyline();
  s.beats[0].from = "p-nobody";
  s.beats[1].inReplyTo = "#missing";
  const { errors } = buildMailbox(world(), [s], []);

  assert.ok(errors.some((e) => e.includes("p-nobody")));
  assert.ok(errors.some((e) => e.includes("#missing")));
});
