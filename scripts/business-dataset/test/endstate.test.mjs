import { test } from "node:test";
import assert from "node:assert/strict";
import { endState } from "../lib/endstate.mjs";

function message(id, records, relations = []) {
  return { id, folder: "inbox", expected: { records, relations } };
}

test("later mails about the same record update its values; the end state names every source message", () => {
  const state = endState([
    message("m-0001", { sales_orders: [{ title: "Kessler SO-26-0142", values: { order_number: "SO-26-0142", state: "received" } }] }),
    message("m-0002", { sales_orders: [{ title: "Kessler SO 26 0142", values: { order_number: "SO-26-0142", state: "confirmed", confirmed_delivery: "2026-08-03" } }] }),
  ]);

  assert.deepEqual(state.records.sales_orders, [{
    title: "Kessler SO-26-0142",
    values: { order_number: "SO-26-0142", state: "confirmed", confirmed_delivery: "2026-08-03" },
    sources: ["m-0001", "m-0002"],
  }]);
});

test("people sharing a phone number or an email are one person, whatever the spelling of the name", () => {
  const state = endState([
    message("m-0001", { people: [{ title: "Marek W.", values: { phone: "+48 5550 4171" } }] }),
    message("m-0002", { people: [{ title: "Marek Wiśniewski", values: { phone: "+48 5550 4171", email: "marek.w84@pocztamail.example" } }] }),
    message("m-0003", { people: [{ title: "Marek Wisniewski", values: { email: "marek.w84@pocztamail.example" } }] }),
    message("m-0004", { people: [{ title: "Marek Kowalczyk", values: { email: "m.kowalczyk@ostrowski.example" } }] }),
  ]);

  assert.deepEqual(state.records.people.map((p) => [p.title, p.sources]), [
    ["Marek Wiśniewski", ["m-0001", "m-0002", "m-0003"]],
    ["Marek Kowalczyk", ["m-0004"]],
  ]);
});

test("records without a key merge by title; sent mail and duplicate copies are ignored", () => {
  const state = endState([
    message("m-0001", { tasks: [{ title: "Zolltarifnummern schicken", values: { due_date: "2026-07-16" } }] }),
    { ...message("m-0002", { tasks: [{ title: "Zolltarifnummern schicken", values: { state: "done" } }] }), duplicateOf: "m-0001" },
    { ...message("m-0003", { tasks: [{ title: "Zolltarifnummern schicken", values: { state: "done" } }] }), folder: "sent" },
    message("m-0004", { tasks: [{ title: "zolltarifnummern  schicken", values: { state: "done" } }] }),
  ]);

  assert.deepEqual(state.records.tasks, [{ title: "Zolltarifnummern schicken", values: { due_date: "2026-07-16", state: "done" }, sources: ["m-0001", "m-0004"] }]);
});

test("an explicit entity id joins records the keys cannot join", () => {
  const state = endState([
    message("m-0001", { people: [{ title: "Marek W.", entity: "driver", values: {} }] }),
    message("m-0002", { people: [{ title: "Marek Wiśniewski", entity: "driver", values: { phone: "+48 5550 4171" } }] }),
  ]);
  assert.equal(state.records.people.length, 1);
  assert.equal(state.records.people[0].title, "Marek Wiśniewski");
});

test("a later mail can retract a record (e.g. 'sorry, wrong recipient'), which removes it from the end state", () => {
  const state = endState([
    message("m-0001", { tasks: [{ title: "Leuchten für den Messestand liefern", values: { due_date: "2026-07-24" } }] }),
    { ...message("m-0002", {}), retracts: ["tasks:Leuchten für den Messestand liefern"] },
  ]);
  assert.deepEqual(state.records.tasks, []);
  assert.deepEqual(state.retracted, [{ record: "tasks:Leuchten für den Messestand liefern", by: "m-0002" }]);
});

test("relations are deduplicated and point at the records' final titles", () => {
  const state = endState([
    message("m-0001", { people: [{ title: "Marek W.", values: { phone: "+48 5550 4171" } }], organizations: [{ title: "Wisła-Trans", values: { domain: "wislatrans.example" } }] },
      [{ type: "drives_for", from: "people:Marek W.", to: "organizations:Wisła-Trans" }]),
    message("m-0002", { people: [{ title: "Marek Wiśniewski", values: { phone: "+48 5550 4171" } }], organizations: [{ title: "Wisła-Trans Spedycja", values: { domain: "wislatrans.example" } }] },
      [{ type: "drives_for", from: "people:Marek Wiśniewski", to: "organizations:Wisła-Trans Spedycja" }]),
  ]);

  assert.deepEqual(state.relations, [{ type: "drives_for", from: "people:Marek Wiśniewski", to: "organizations:Wisła-Trans Spedycja", sources: ["m-0001", "m-0002"] }]);
});
