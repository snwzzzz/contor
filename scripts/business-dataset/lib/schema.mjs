// The business eval schema, mirrored from EvalCollections.business() (BusinessCorpusTest guards that labels fit the Java
// side): collection slug -> field slug -> field type, plus the match keys used to join records in the end state.

export const SCHEMA = {
  people: { fields: { full_name: "TEXT", email: "EMAIL", phone: "PHONE", role_title: "TEXT", notes: "LONG_TEXT" }, keys: ["email", "phone"] },
  organizations: { fields: { name: "TEXT", domain: "TEXT", website: "URL", notes: "LONG_TEXT" }, keys: ["domain"] },
  events: { fields: { starts_at: "DATETIME", ends_at: "DATETIME", all_day: "BOOLEAN", location: "TEXT", notes: "LONG_TEXT" }, keys: [] },
  tasks: { fields: { state: "STATUS", due_date: "DATE", priority: "ENUM", notes: "LONG_TEXT" }, keys: [] },
  customers: {
    fields: { name: "TEXT", customer_number: "TEXT", country: "TEXT", segment: "ENUM", payment_terms: "TEXT", notes: "LONG_TEXT" },
    keys: ["customer_number"],
  },
  suppliers: { fields: { name: "TEXT", country: "TEXT", lead_time_days: "NUMBER", notes: "LONG_TEXT" }, keys: [] },
  sales_orders: {
    fields: { order_number: "TEXT", customer: "TEXT", total: "MONEY", state: "STATUS", requested_delivery: "DATE", confirmed_delivery: "DATE" },
    keys: ["order_number"],
  },
  purchase_orders: {
    fields: { po_number: "TEXT", supplier: "TEXT", total: "MONEY", state: "STATUS", expected_arrival: "DATE" },
    keys: ["po_number"],
  },
  shipments: {
    fields: { tracking_number: "TEXT", carrier: "TEXT", direction: "ENUM", order_ref: "TEXT", state: "STATUS", eta: "DATE", driver: "TEXT" },
    keys: ["tracking_number"],
  },
  complaints: {
    fields: { reference: "TEXT", customer: "TEXT", topic: "ENUM", severity: "ENUM", state: "STATUS", opened_on: "DATE" },
    keys: ["reference"],
  },
  invoices: {
    fields: { invoice_number: "TEXT", issuer: "TEXT", amount: "MONEY", due_date: "DATE", state: "STATUS", direction: "ENUM" },
    keys: ["invoice_number"],
  },
};

// relation type -> [from collection, to collection], as in EvalCollections.businessRelationTypes()
export const RELATION_ENDS = {
  works_at: ["people", "organizations"], knows: ["people", "people"], attends: ["people", "events"],
  supplies: ["suppliers", "purchase_orders"], ships: ["shipments", "sales_orders"], delivers: ["shipments", "purchase_orders"],
  drives_for: ["people", "organizations"],
};
export const RELATION_TYPES = Object.keys(RELATION_ENDS);
export const INTENTS = ["ANSWER_QUESTIONS", "SCHEDULE", "CONFIRM", "DECLINE", "PROVIDE_INFO", "COURTESY", "NONE"];
export const COURTESY_KINDS = ["REJECTION_THANKS", "DECLINE_OFFER", "THANK_YOU", "CONGRATULATE", "KEEP_IN_TOUCH"];
export const IMPORTANCE = ["LOW", "NORMAL", "HIGH", "URGENT"];
export const TIME_OF_DAY = ["ANY", "MORNING", "AFTERNOON", "EVENING"];
