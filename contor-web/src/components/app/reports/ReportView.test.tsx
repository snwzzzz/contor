import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { mockApi } from "@/test/api-mock";
import { resetNavigation, router } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { ReportView } from "./ReportView";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

const report = {
  id: 5,
  collectionId: 1,
  recordId: 4711,
  prompt: "What went wrong?",
  title: "Order 4711 review",
  state: "READY",
  records: [{ collectionId: 1, recordId: 4711 }, { collectionId: 2, recordId: 88 }],
  emailCount: 1,
  claimsTotal: 4,
  claimsVerified: 3,
  claimsRemoved: 1,
  model: "qwen3.5:9b",
  promptVersion: "report-v1",
  createdAt: "2026-10-14T10:00:00Z",
  generatedAt: "2026-10-14T10:01:00Z",
  sections: [
    {
      id: 1, type: "SUMMARY", title: "Executive summary", findings: [], blocks: [],
      claims: [{ id: 11, text: "The order was due on 2026-09-02 and arrived on 2026-09-07.", check: "SUPPORTED", markers: [1, 2] }],
    },
    {
      id: 2, type: "FINDINGS", title: "Key findings", claims: [], blocks: [],
      findings: [{
        id: 21, title: "Customs hold", level: "PROBLEM",
        claims: [{ id: 13, text: "The shipment was held at customs.", check: "REPAIRED", note: "Was: \"It was lost.\"", markers: [3] }],
        quotes: [{ marker: 3, text: "held at customs" }],
      }],
    },
  ],
  sources: [
    { marker: 1, kind: "FIELD", label: "Expected delivery", collectionId: 1, recordId: 4711, recordTitle: "Order 4711",
      fieldSlug: "expected_delivery", fieldName: "Expected delivery", value: "2026-09-02", sourceState: "CHANGED" },
    { marker: 2, kind: "FIELD", label: "Delivered at", collectionId: 2, recordId: 88, recordTitle: "Delivery 88",
      fieldSlug: "delivered_at", fieldName: "Delivered at", value: "2026-09-07", sourceState: "CURRENT" },
    { marker: 3, kind: "EMAIL_PARAGRAPH", label: "Delay notice", emailId: 7, emailSubject: "Delay notice", emailFrom: "Carrier Support",
      emailDate: "2026-09-04T08:00:00Z", paragraphIndex: 1, quote: "Your shipment is held at customs.", sourceState: "CURRENT" },
  ],
  removedClaims: [{ id: 12, text: "The carrier apologised.", check: "REMOVED", note: "not supported by its sources", markers: [] }],
  followUps: [],
};

const paragraphs = [
  { index: 0, text: "Hello Tom,", hash: "a" },
  { index: 1, text: "Your shipment is held at customs.", hash: "b" },
];

describe("ReportView", () => {
  beforeEach(() => resetNavigation("/collections/1/records/4711/reports/5"));

  it("shows the answer, the findings with their quotes and how many sentences have a source", async () => {
    mockApi({ "GET /api/reports/5": { body: report } });

    renderWithProviders(<ReportView collectionId={1} recordId={4711} reportId={5} />);

    expect(await screen.findByRole("heading", { level: 1, name: "Order 4711 review" })).toBeInTheDocument();
    expect(screen.getByText("What went wrong?")).toBeInTheDocument();
    expect(screen.getByText("3 of 4 sentences with a source")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Executive summary" })).toBeInTheDocument();
    expect(screen.getByText("The order was due on 2026-09-02 and arrived on 2026-09-07.")).toBeInTheDocument();
    const finding = screen.getByRole("article", { name: "Customs hold" });
    expect(within(finding).getByText("Problem")).toBeInTheDocument();
    expect(within(finding).getByText(/held at customs/, { selector: "blockquote *" })).toBeInTheDocument();
    expect(screen.getByLabelText("Rewritten by the fact check")).toBeInTheDocument();
    expect(screen.queryByText(/Fact check/)).not.toBeInTheDocument();
    expect(screen.queryByText("The carrier apologised.")).not.toBeInTheDocument();
  });

  it("renders the document: lists, tables, quotes and emphasis with their markers", async () => {
    const claim = (id: number, text: string, markers: number[] = []) => ({ id, text, check: "SUPPORTED", markers });
    const document = {
      id: 7, type: "CUSTOM", title: "Possible causes", claims: [], findings: [],
      blocks: [
        { id: 71, type: "NUMBERED", columns: [], rows: [], items: [claim(711, "**Packaging:** the box was too weak.", [3]), claim(712, "Rough handling.")] },
        { id: 72, type: "TABLE", columns: ["Customer", "Orders"], items: [],
          rows: [[claim(721, "Kessler"), claim(722, "12", [1])], [claim(723, "Moreau"), claim(724, "")]] },
        { id: 73, type: "QUOTE", columns: [], rows: [], items: [claim(731, "„held at customs“", [3])] },
        { id: 74, type: "BULLETS", columns: [], rows: [], items: [claim(741, "Check the packing station.")] },
      ],
    };
    mockApi({ "GET /api/reports/5": { body: { ...report, sections: [...report.sections, document] } } });

    renderWithProviders(<ReportView collectionId={1} recordId={4711} reportId={5} />);

    const section = await screen.findByRole("region", { name: "Possible causes" });
    const [numbered, bullets] = within(section).getAllByRole("list");
    expect(numbered?.tagName).toBe("OL");
    expect(within(numbered!).getAllByRole("listitem")).toHaveLength(2);
    expect(within(numbered!).getByText("Packaging:", { selector: "strong" })).toBeInTheDocument();
    expect(within(numbered!).getByRole("button", { name: "Source 3: Delay notice" })).toBeInTheDocument();
    expect(bullets?.tagName).toBe("UL");
    const table = within(section).getByRole("table");
    expect(within(table).getAllByRole("columnheader").map((header) => header.textContent)).toEqual(["Customer", "Orders"]);
    expect(within(table).getAllByRole("row")).toHaveLength(3);
    const orders = within(table).getByRole("cell", { name: /12/ });
    expect(within(orders).getByRole("button", { name: "Source 1: Expected delivery" })).toBeInTheDocument();
    expect(within(section).getByText("„held at customs“", { selector: "blockquote *" })).toBeInTheDocument();
  });

  it("opens a source with its exact place when its marker is clicked", async () => {
    mockApi({ "GET /api/reports/5": { body: report }, "GET /api/emails/7/paragraphs": { body: paragraphs } });
    const user = userEvent.setup();
    renderWithProviders(<ReportView collectionId={1} recordId={4711} reportId={5} />);

    await user.click(await screen.findByRole("button", { name: "Source 3: Delay notice" }));

    const panel = screen.getByRole("complementary", { name: "Sources" });
    expect(within(panel).getByText("Carrier Support", { exact: false })).toBeInTheDocument();
    const cited = await within(panel).findByTestId("cited-paragraph");
    expect(within(cited).getByText("held at customs", { selector: "mark" })).toBeInTheDocument();
    expect(within(panel).getByRole("link", { name: "Open in inbox" })).toHaveAttribute("href", "/inbox?email=7&paragraph=1");
  });

  it("marks a field that changed since the report and links to it", async () => {
    mockApi({ "GET /api/reports/5": { body: report } });
    const user = userEvent.setup();
    renderWithProviders(<ReportView collectionId={1} recordId={4711} reportId={5} />);

    await user.click(await screen.findByRole("button", { name: "Source 1: Expected delivery" }));

    const panel = screen.getByRole("complementary", { name: "Sources" });
    expect(within(panel).getByText("Changed since the report")).toBeInTheDocument();
    expect(within(panel).getByRole("link", { name: "Open record" }))
      .toHaveAttribute("href", "/collections/1/records/4711?tab=details&field=expected_delivery");
  });

  it("asks a follow-up question", async () => {
    const calls = mockApi({
      "GET /api/reports/5": { body: report },
      "POST /api/reports/5/questions": { status: 202, body: { ...report, id: 6, parentReportId: 5, state: "QUEUED" } },
    });
    const user = userEvent.setup();
    renderWithProviders(<ReportView collectionId={1} recordId={4711} reportId={5} />);

    await user.type(await screen.findByRole("textbox", { name: "Ask a follow-up" }), "Who caused the delay?{Enter}");

    await waitFor(() => expect(calls.find((call) => call.method === "POST")?.body).toEqual({ question: "Who caused the delay?" }));
  });

  it("shows the follow-up answers below the report", async () => {
    const followUp = { ...report, id: 6, parentReportId: 5, prompt: "Who caused the delay?", title: "Delay", removedClaims: [],
      sections: [{ id: 9, type: "SUMMARY", title: "Executive summary", findings: [], blocks: [],
        claims: [{ id: 91, text: "The customs office held the shipment.", check: "SUPPORTED", markers: [1] }] }],
      sources: [report.sources[2] && { ...report.sources[2], marker: 1 }] };
    mockApi({ "GET /api/reports/5": { body: { ...report, followUps: [followUp] } } });

    renderWithProviders(<ReportView collectionId={1} recordId={4711} reportId={5} />);

    expect(await screen.findByRole("heading", { name: "Who caused the delay?" })).toBeInTheDocument();
    expect(screen.getByText("The customs office held the shipment.")).toBeInTheDocument();
  });

  it("marks recommendations as the assistant's suggestions", async () => {
    const advice = { id: 4, type: "NEXT_STEPS", title: "Recommendations and next steps", findings: [], blocks: [],
      claims: [{ id: 41, text: "Agree on a backup carrier for urgent deliveries.", check: "SUPPORTED", markers: [3] }] };
    mockApi({ "GET /api/reports/5": { body: { ...report, sections: [...report.sections, advice] } } });

    renderWithProviders(<ReportView collectionId={1} recordId={4711} reportId={5} />);

    const section = await screen.findByRole("region", { name: "Recommendations and next steps" });
    expect(within(section).getByText("Suggestions from the assistant.")).toBeInTheDocument();
    expect(within(section).getByText("Agree on a backup carrier for urgent deliveries.")).toBeInTheDocument();
  });

  it("shows the progress while the report is generated", async () => {
    mockApi({ "GET /api/reports/5": { body: { ...report, state: "DRAFTING", sections: [], sources: [], removedClaims: [] } } });

    renderWithProviders(<ReportView collectionId={1} recordId={4711} reportId={5} />);

    expect(await screen.findByText("Writing the report…")).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Ask a follow-up" })).toBeDisabled();
  });

  it("explains why a report failed", async () => {
    mockApi({ "GET /api/reports/5": { body: { ...report, state: "FAILED", failureReason: "The AI model did not answer. Is Ollama running?",
      sections: [], sources: [], removedClaims: [] } } });

    renderWithProviders(<ReportView collectionId={1} recordId={4711} reportId={5} />);

    expect(await screen.findByText("The AI model did not answer. Is Ollama running?")).toBeInTheDocument();
  });

  it("deletes the report after confirming and returns to the record", async () => {
    const calls = mockApi({ "GET /api/reports/5": { body: report }, "DELETE /api/reports/5": { status: 204 } });
    const user = userEvent.setup();
    renderWithProviders(<ReportView collectionId={1} recordId={4711} reportId={5} />);

    await user.click(await screen.findByRole("button", { name: "Delete" }));
    await user.click(screen.getByRole("button", { name: "Delete report" }));

    await waitFor(() => expect(router.push).toHaveBeenCalledWith("/collections/1/records/4711"));
    expect(calls.some((call) => call.method === "DELETE")).toBe(true);
  });

  it("says when the report does not exist", async () => {
    mockApi({});

    renderWithProviders(<ReportView collectionId={1} recordId={4711} reportId={5} />);

    expect(await screen.findByText("This report does not exist (anymore).")).toBeInTheDocument();
  });
});
