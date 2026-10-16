import type { NewsItem } from "./NewsFeed";

/** Stand-ins until the home page gets real news (new emails, results of the analysis). */
export const PLACEHOLDER_NEWS: readonly NewsItem[] = [
  {
    id: "1",
    subject: "Invoice October",
    preview: "Your invoice for October is ready. The amount will be charged on the 20th.",
    people: ["Billing"],
    received: "09:12",
    unread: true,
  },
  {
    id: "2",
    subject: "Thursday's meeting",
    preview: "Could we move Thursday's meeting to 3 pm? I have a conflict in the morning.",
    people: ["Anna Blum", "Chris Ehring"],
    received: "08:40",
    unread: true,
  },
  {
    id: "3",
    subject: "Documents received",
    preview: "Thanks for the quick reply, the documents arrived and look complete.",
    people: ["Nathalia Dwoa"],
    received: "Yesterday",
    unread: false,
  },
  {
    id: "4",
    subject: "Contract renewal",
    preview: "Reminder: the contract renewal is due at the end of the month.",
    people: ["Gavin", "Legal"],
    received: "Mon",
    unread: false,
  },
];
