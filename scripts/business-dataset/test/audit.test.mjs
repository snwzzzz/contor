import { test } from "node:test";
import assert from "node:assert/strict";
import { auditInput, compareLabels } from "../lib/audit.mjs";

const message = (overrides = {}) => ({
  id: "m-0002", storyline: "A1-x", folder: "inbox",
  email: { mailbox: "alex.morgan@nordhafen.example", fromName: "S", fromAddress: "s@x.example", to: ["alex.morgan@nordhafen.example"], subject: "Hi", receivedAt: "2026-07-07T07:15:00Z", body: "Body" },
  expected: { records: { sales_orders: [{ title: "Kessler SO-26-0142", values: { order_number: "SO-26-0142" } }] }, relations: [] },
  needsReply: true, reply: { intent: "CONFIRM" }, importance: "NORMAL", dependsOn: ["m-0001"], facts: ["secret"], tags: ["x"],
  ...overrides,
});

test("the audit input carries the mail and its dependencies, never labels, facts or notes", () => {
  const earlier = message({ id: "m-0001", email: { ...message().email, subject: "Earlier" } });
  const input = auditInput(message(), new Map([["m-0001", earlier]]));

  assert.deepEqual(Object.keys(input).sort(), ["dependsOn", "email", "id"]);
  assert.equal(input.dependsOn[0].subject, "Earlier");
  assert.equal(JSON.stringify(input).includes("secret"), false);
  assert.equal(JSON.stringify(input).includes("SO-26-0142"), false);
});

test("labels agree when records match by key or similar title; reply fields are compared one by one", () => {
  const ours = message();
  const theirs = {
    id: "m-0002", needsReply: true, reply: { intent: "ANSWER_QUESTIONS" }, importance: "NORMAL",
    records: { sales_orders: [{ title: "Order SO-26-0142 Kessler", values: { order_number: "SO-26-0142" } }], tasks: [{ title: "Call back", values: {} }] },
  };
  const result = compareLabels([ours], [theirs]);

  assert.deepEqual(result.rates, { needsReply: 1, importance: 1, intent: 0, records: 2 / 3 });
  assert.deepEqual(result.disagreements[0].fields, ["intent", "records"]);
  assert.deepEqual(result.disagreements[0].records, { onlyOurs: [], onlyTheirs: ["tasks: Call back"] });
});

test("no reply label and intent NONE mean the same", () => {
  const ours = message({ needsReply: false, reply: null, expected: { records: {}, relations: [] } });
  const theirs = { id: "m-0002", needsReply: false, reply: { intent: "NONE" }, importance: "NORMAL", records: {} };
  assert.deepEqual(compareLabels([ours], [theirs]).disagreements, []);
});
