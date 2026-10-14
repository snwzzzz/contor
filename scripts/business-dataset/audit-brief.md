# Label audit brief (prompt L1)

You label business emails from scratch for an evaluation dataset. You get each email and the earlier emails it may refer
to (`dependsOn`, which may include mails the owner sent), never the intended labels. Label what an ideal assistant
should record from **this one email** (using the earlier emails only to resolve references).

Mailbox owner: Alex Morgan <alex.morgan@nordhafen.example>, Head of Operations of Nordhafen Industriebedarf GmbH
(Hamburg, industrial wholesale). Shared mailbox: orders@nordhafen.example. Colleagues have addresses @nordhafen.example.
Today's date for every email is its own `receivedAt`; times in Europe/Berlin unless the sender states another zone.

## Collections and fields

| Collection | Fields | Notes |
|---|---|---|
| people | full_name, email, phone, role_title | individual humans the owner deals with: every human sender of a business mail (colleagues too), plus people named with contact details. Never automated/no-reply senders. Values only when stated (email from the From header counts; phone/role only if in the mail). |
| organizations | name, domain | the sender's company (domain from the sender address), and companies introduced with details. Not Nordhafen itself. |
| events | starts_at (ISO with offset), ends_at, all_day, location | meetings, visits, unloading slots, calls; a single proposed date/time counts |
| tasks | state (open/in_progress/done), due_date, priority | things the owner (or his team) has to do beyond writing a reply: pay, send documents, call, reorder, change an address. A later mail saying it is done → same title with state done. |
| customers | name, customer_number, country, segment (loyal/growing/at_risk/lost/new), payment_terms, notes | only when the mail is about the customer relationship itself (new customer data, terms, threatening to leave, leaving) |
| suppliers | name, country, lead_time_days, notes | only when the mail is about the supplier relationship itself (price changes, terms) |
| sales_orders | order_number (SO-26-…), customer, total (money), state (received/confirmed/in_production/shipped/delivered/cancelled), requested_delivery, confirmed_delivery | orders customers place with Nordhafen; title "<customer> <number or the customer's own reference>" |
| purchase_orders | po_number (PO-26-…), supplier, total, state (ordered/confirmed/shipped/received/cancelled), expected_arrival | Nordhafen's orders at suppliers |
| shipments | tracking_number, carrier, direction (outbound/inbound), order_ref, state (booked/picked_up/in_transit/delayed/delivered/damaged), eta, driver | title "<carrier> <tracking>" |
| complaints | reference (RMA-26-…), customer, topic (delay/damage/wrong_item/quality/invoice/service), severity (low/medium/high), state (open/in_progress/resolved), opened_on | customer complaints only |
| invoices | invoice_number, issuer, amount (money), due_date, state (open/paid), direction (incoming/outgoing) | |

Money is `{"amount": 1234.5, "currency": "EUR"}`. Dates `YYYY-MM-DD`. Calendar weeks ("KW 31") resolve to the Monday.
A mail that changes an existing order/shipment labels that record with only the values it states or changes.

Relation types: works_at (people → organizations), attends (people → events), ships (shipments → sales_orders or
purchase_orders), drives_for (people → organizations, for drivers). Write ends as "<collection>:<title>".

## Reply labels

- needsReply: true only if the sender waits for a **written answer from Alex** (a question, a confirmation request, a
  scheduling question, a request to send information/documents). CC-only mails to Alex: false. A colleague asking Alex
  "can you …?": true. Automated mails: false.
- reply.intent: ANSWER_QUESTIONS, SCHEDULE, CONFIRM, DECLINE, PROVIDE_INFO, COURTESY (needsReply false, but a polite
  personal reply fits: a thank-you after a smooth delivery, a goodbye, good news) or NONE.
- reply.courtesyKind for COURTESY: REJECTION_THANKS, DECLINE_OFFER, THANK_YOU, CONGRATULATE, KEEP_IN_TOUCH.
- reply.questions: the questions as asked (≤ 5).

## Importance (of the main thing the assistant would propose)

URGENT: deadline within 2 days with business impact, angry escalation, customs hold. HIGH: customer chasing a delay, order
change, dunning level 2+, partial delivery, quality problem. NORMAL: orders, meetings, invoices, routine to-dos. LOW: smooth
confirmations, courtesy, status notices without action, automated notices.

## mustNotProduce

Things that would be wrong to record, e.g. a to-do in a mail that only says "done/thanks", a retracted request.

## Output

One JSON array for the whole input file, one object per email, nothing else:

```json
[{"id": "m-0002", "records": {"people": [{"title": "…", "values": {…}}]}, "relations": [], "needsReply": true,
  "reply": {"intent": "CONFIRM", "questions": [], "courtesyKind": null}, "importance": "NORMAL", "mustNotProduce": [],
  "uncertain": ["what you were unsure about and why"]}]
```
