/** Shared API bodies for the home page tests. */

export const COLLECTIONS = [
  { id: 3, slug: "tasks", name: "To-dos", system: true, matchKeys: [], displayConfig: {}, schemaVersion: 1, createdBy: "USER", fields: [] },
  { id: 4, slug: "events", name: "Calendar", system: true, matchKeys: [], displayConfig: {}, schemaVersion: 1, createdBy: "USER", fields: [] },
];

export function page<T>(items: T[], totalElements = items.length) {
  return { items, page: 0, size: Math.max(items.length, 1), totalElements, totalPages: totalElements === 0 ? 0 : 1 };
}

export function email(id: number, subject: string, extra: Record<string, unknown> = {}) {
  return {
    id,
    accountId: 1,
    fromName: "Anna Blum",
    fromAddress: "anna@example.com",
    subject,
    snippet: `About ${subject}`,
    receivedAt: "2026-10-19T06:00:00Z",
    read: true,
    hasAttachments: false,
    processingState: "DONE",
    labels: [],
    ...extra,
  };
}

export function record(id: number, collectionId: number, title: string, values: Record<string, unknown>) {
  return { id, collectionId, title, values, extra: {}, createdAt: "2026-10-01T00:00:00Z", createdBy: "USER" };
}

/** "Now" in the tests: 19 Oct 2026, 09:00 local time. */
export const NOW = new Date(2026, 9, 19, 9, 0);

/** A local time today, as the API sends it. */
export function todayAt(hours: number, minutes = 0): string {
  return new Date(2026, 9, 19, hours, minutes).toISOString();
}
