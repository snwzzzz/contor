import { test } from "node:test";
import assert from "node:assert/strict";
import { compareLive, detectShiftDays } from "../lib/live.mjs";

function message(id, records, extra = {}) {
  return {
    id, storyline: "A1-x", folder: "inbox", email: { subject: "S " + id, receivedAt: "2026-07-07T08:00:00Z" },
    expected: { records, relations: [] }, needsReply: false, reply: null, tags: [], ...extra,
  };
}

function dataset() {
  const order = { title: "Kessler SO-26-0155", values: { order_number: "SO-26-0155", state: "delivered", requested_delivery: "2026-07-17" } };
  return {
    messages: [
      message("m-0001", { sales_orders: [order] }, { needsReply: true, reply: { intent: "CONFIRM" } }),
      message("m-0002", { tasks: [{ title: "Rechnung bezahlen", values: {} }] }),
      { ...message("m-0003", {}), storyline: "noise" },
    ],
    endState: { records: { sales_orders: [{ ...order, sources: ["m-0001"] }], tasks: [{ title: "Rechnung bezahlen", values: {}, sources: ["m-0002"] }] }, relations: [] },
    insights: [{ id: "i", title: "Kessler at risk", evidence: ["m-0001", "m-0002"] }],
  };
}

function live() {
  return {
    collections: [{ id: 7, slug: "sales_orders" }, { id: 4, slug: "tasks" }, { id: 5, slug: "events" }],
    emails: [{ id: 101, email: "m-0001", receivedAt: "2026-09-15T08:00:00Z" }, { id: 102, email: "m-0002" }, { id: 103, email: "m-0003" }],
    records: {
      sales_orders: [
        { row: { id: 1, title: "Order SO-26-0155 Kessler", order_number: "SO-26-0155", state: "shipped", requested_delivery: "2026-09-25" }, sources: ["m-0001"] },
        { row: { id: 2, title: "Kessler order SO 26 0155 again", order_number: null, state: "received" }, sources: ["m-0001"] },
      ],
      tasks: [],
      events: [{ row: { id: 3, title: "Delivery of SO-26-0155", starts_at: "2026-07-17T00:00:00Z" }, sources: ["m-0001"] }],
    },
    proposals: [
      { kind: "CREATE_RECORD", email: "m-0001", payload: { collectionId: 7, title: "Order SO-26-0155 Kessler" } },
      { kind: "DRAFT_REPLY", email: "m-0003", payload: { intent: "COURTESY" } },
      { kind: "INSIGHT", email: "m-0001", payload: { title: "Kessler", evidenceEmailIds: [101, 102] } },
    ],
    relations: [],
  };
}

test("the date shift of a demo import is detected from the stored receive dates", () => {
  assert.equal(detectShiftDays(dataset().messages, live().emails), 70);
});

test("collections are compared by key, extras and duplicates are separated, fields allow the shifted date", () => {
  const result = compareLive(dataset(), live(), { shiftDays: 70 });

  assert.deepEqual(result.collections.sales_orders, { expected: 1, actual: 2, matched: 1 });
  assert.equal(result.duplicates.sales_orders.length, 1);
  assert.deepEqual(result.missed.tasks, ["Rechnung bezahlen  [m-0002]"]);
  assert.deepEqual(result.extra.events, ["Delivery of SO-26-0155  [m-0001]"]);
  assert.deepEqual(result.fields["sales_orders.state"], { ok: 0, shifted: 0, total: 1 });
  assert.deepEqual(result.fields["sales_orders.requested_delivery"], { ok: 1, shifted: 1, total: 1 });
});

test("per mail misses, drafts on noise and insights are reported", () => {
  const result = compareLive(dataset(), live(), { shiftDays: 70 });

  assert.deepEqual(result.perMail.missedByCollection, { tasks: 1 });
  assert.deepEqual(result.replies.onNoise, ["m-0003"]);
  assert.equal(result.replies.needed, 1);
  assert.equal(result.replies.neededDrafted, 0);
  assert.deepEqual(result.insights, [{ title: "Kessler at risk", found: true }]);
});
