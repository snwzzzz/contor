import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import type { EmailSearchField } from "@/lib/api/generated/model/emailSearchField";
import { mockApi } from "@/test/api-mock";
import { renderWithProviders } from "@/test/render";
import { EmailSearchBar } from "./EmailSearchBar";

const SUGGESTIONS = [
  { kind: "PERSON", label: "Anna Berger", value: "anna@acme.com", emailCount: 4 },
  { kind: "COMPANY", label: "acme.com", value: "@acme.com", emailCount: 9 },
];

function Harness({ suggestions, onSearch }: { suggestions: boolean; onSearch: (text: string, field: EmailSearchField) => void }) {
  const [text, setText] = useState("");
  const [field, setField] = useState<EmailSearchField>("ALL");
  return (
    <EmailSearchBar
      text={text}
      field={field}
      suggestions={suggestions}
      onChange={(nextText, nextField) => {
        setText(nextText);
        setField(nextField);
        onSearch(nextText, nextField);
      }}
    />
  );
}

describe("EmailSearchBar", () => {
  it("chooses where to search", async () => {
    const onSearch = vi.fn();
    mockApi({});
    renderWithProviders(<Harness suggestions={false} onSearch={onSearch} />);

    await userEvent.click(screen.getByRole("button", { name: "Search in: All" }));
    await userEvent.click(await screen.findByRole("menuitemradio", { name: "Content" }));

    expect(onSearch).toHaveBeenLastCalledWith("", "CONTENT");
    expect(screen.getByRole("button", { name: "Search in: Content" })).toBeInTheDocument();
  });

  it("suggests people and companies and searches the picked one in people", async () => {
    const onSearch = vi.fn();
    const requested: string[] = [];
    mockApi({
      "GET /api/emails/suggestions": ({ url }) => {
        requested.push(url.search);
        return { body: SUGGESTIONS };
      },
    });
    renderWithProviders(<Harness suggestions onSearch={onSearch} />);

    await userEvent.type(screen.getByRole("combobox", { name: "Search emails" }), "acm");

    const listbox = await screen.findByRole("listbox", { name: "Suggestions" });
    expect(listbox).toHaveTextContent("Anna Berger");
    expect(requested.at(-1)).toContain("q=acm");
    await userEvent.click(screen.getByRole("option", { name: /acme\.com.*Company/ }));

    expect(onSearch).toHaveBeenLastCalledWith("@acme.com", "PEOPLE");
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Search in: People" })).toBeInTheDocument();
  });

  it("picks a suggestion with the keyboard and closes on Escape", async () => {
    const onSearch = vi.fn();
    mockApi({ "GET /api/emails/suggestions": { body: SUGGESTIONS } });
    renderWithProviders(<Harness suggestions onSearch={onSearch} />);
    const input = screen.getByRole("combobox", { name: "Search emails" });

    await userEvent.type(input, "an");
    await screen.findByRole("listbox", { name: "Suggestions" });
    await userEvent.keyboard("{Escape}");
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();

    await userEvent.type(input, "n");
    await screen.findByRole("listbox", { name: "Suggestions" });
    await userEvent.keyboard("{ArrowDown}");
    expect(input.getAttribute("aria-activedescendant")).toBeTruthy();
    await userEvent.keyboard("{Enter}");

    await waitFor(() => expect(onSearch).toHaveBeenLastCalledWith("anna@acme.com", "PEOPLE"));
  });

  it("does not ask the server without autocomplete", async () => {
    const calls = mockApi({ "GET /api/emails/suggestions": { body: SUGGESTIONS } });
    renderWithProviders(<Harness suggestions={false} onSearch={vi.fn()} />);

    await userEvent.type(screen.getByRole("combobox", { name: "Search emails" }), "acm");

    expect(calls.filter((call) => call.path === "/api/emails/suggestions")).toHaveLength(0);
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  });
});
