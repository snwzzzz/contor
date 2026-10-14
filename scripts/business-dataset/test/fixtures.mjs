// A minimal world and storylines for the generator tests (shape of ai-eval/business/world.json).

export function world() {
  return {
    company: { id: "nordhafen", name: "Nordhafen Industriebedarf GmbH", domain: "nordhafen.example", zone: "Europe/Berlin" },
    accounts: { alex: "alex.morgan@nordhafen.example", orders: "orders@nordhafen.example" },
    owner: "p-alex",
    companies: [
      { id: "nordhafen", name: "Nordhafen Industriebedarf GmbH", type: "internal", country: "DE", zone: "Europe/Berlin", domain: "nordhafen.example" },
      { id: "c-kessler", name: "Kessler Maschinenbau GmbH", type: "customer", country: "DE", zone: "Europe/Berlin", domain: "kessler-maschinenbau.example" },
      { id: "s-anadolu", name: "Anadolu Dövme Sanayi A.Ş.", type: "supplier", country: "TR", zone: "Europe/Istanbul", domain: "anadolu-dovme.example" },
      { id: "n-rundschau", name: "Werkzeug-Rundschau", type: "noise", country: "DE", zone: "Europe/Berlin", domain: "werkzeug-rundschau.example", automated: true },
    ],
    people: [
      { id: "p-alex", name: "Alex Morgan", company: "nordhafen", email: "alex.morgan@nordhafen.example", phone: "+49 40 5550 1001", role: "Head of Operations" },
      { id: "p-jana", name: "Jana Petersen", company: "nordhafen", email: "jana.petersen@nordhafen.example", phone: "+49 40 5550 1002", role: "Lagerleitung" },
      { id: "p-wendt", name: "Sabine Wendt", company: "c-kessler", email: "s.wendt@kessler-maschinenbau.example", phone: "+49 521 5550 2201", role: "Einkauf" },
      { id: "p-arslan", name: "Mehmet Arslan", company: "s-anadolu", email: "m.arslan@anadolu-dovme.example", phone: "+90 224 5550 3301", role: "Export Manager" },
      { id: "p-marek", name: "Marek Wiśniewski", company: null, email: "m.wisniewski@wislatrans.example", privateEmail: "marek.w84@pocztamail.example", phone: "+48 5550 4171", role: "Fahrer" },
      { id: "p-rundschau", name: "Werkzeug-Rundschau", company: "n-rundschau", email: "newsletter@werkzeug-rundschau.example", automated: true },
    ],
    denyList: ["Siemens", "DHL"],
  };
}

export function storyline(overrides = {}) {
  return {
    id: "E2-kessler-price",
    category: "E2",
    summary: "Kessler asks for a better price.",
    tags: ["churn-signal"],
    numbers: ["SO-26-0142"],
    beats: [
      {
        key: "ask", day: "2026-07-07", time: "09:15", from: "p-wendt", conversation: "kessler-price",
        subject: "Preise SO-26-0142", body: "Guten Tag Herr Morgan,\nzu unserer Bestellung SO-26-0142 über 1.234,50 €: geht da preislich noch etwas?\nMit freundlichen Grüßen\nSabine Wendt\nEinkauf\nTel. +49 521 5550 2201",
        style: { lang: "de" },
        labels: {
          records: { sales_orders: [{ title: "Kessler SO-26-0142", values: { order_number: "SO-26-0142", total: { amount: 1234.5, currency: "EUR" } } }] },
          needsReply: true, reply: { intent: "ANSWER_QUESTIONS", questions: ["Geht preislich noch etwas?"] }, importance: "NORMAL",
        },
      },
      {
        key: "answer", day: "2026-07-07", time: "14:00", direction: "sent", to: ["p-wendt"], inReplyTo: "#ask",
        subject: "AW: Preise SO-26-0142", body: "Hallo Frau Wendt,\nich prüfe das bis Donnerstag.\nGruß Alex Morgan", style: { lang: "de" },
      },
    ],
    ...overrides,
  };
}
