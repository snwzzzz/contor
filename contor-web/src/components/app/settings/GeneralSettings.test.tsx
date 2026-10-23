import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { mockApi } from "@/test/api-mock";
import { renderWithProviders } from "@/test/render";
import { GeneralSettings } from "./GeneralSettings";

const setTheme = vi.fn();
let theme = "system";
vi.mock("next-themes", () => ({ useTheme: () => ({ theme, setTheme }) }));

describe("GeneralSettings", () => {
  let calls: ReturnType<typeof mockApi>;
  const aiSettings = {
    mailAccess: true, historyDays: 90, categories: "SUGGEST", records: "SUGGEST", calendar: "SUGGEST", tasks: "SUGGEST",
    replies: "OFF", courtesyReplies: false, readHiddenMailboxes: true,
  };

  beforeEach(() => {
    setTheme.mockReset();
    theme = "system";
    calls = mockApi({
      "GET /api/ai/settings": { body: aiSettings },
      "PUT /api/ai/settings": { body: { ...aiSettings, readHiddenMailboxes: false } },
    });
  });

  it("lets the AI read hidden mailboxes by default, and turns that off", async () => {
    renderWithProviders(<GeneralSettings />);

    const toggle = await screen.findByRole("checkbox", { name: "AI reads hidden mailboxes" });
    await waitFor(() => expect(toggle).toBeChecked());
    expect(toggle.closest("[data-slot=settings-row]")).toHaveTextContent(/Hidden mailboxes .* are not shown in contor/);

    await userEvent.click(toggle);

    await waitFor(() => expect(calls.find((call) => call.method === "PUT")?.body).toEqual({ readHiddenMailboxes: false }));
    await waitFor(() => expect(screen.getByRole("checkbox", { name: "AI reads hidden mailboxes" })).not.toBeChecked());
  });

  it("picks the theme: light, dark or system, the current one checked", async () => {
    renderWithProviders(<GeneralSettings />);

    const group = screen.getByRole("radiogroup", { name: "Theme" });
    expect(group).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "System" })).toBeChecked();
    expect(screen.getByRole("radio", { name: "Light" })).not.toBeChecked();

    await userEvent.click(screen.getByRole("radio", { name: "Dark" }));

    expect(setTheme).toHaveBeenCalledWith("dark");
  });

  it("checks the saved theme", () => {
    theme = "light";
    renderWithProviders(<GeneralSettings />);

    expect(screen.getByRole("radio", { name: "Light" })).toBeChecked();
  });

  it("picks the time format: 24-hour by default, or 12-hour with AM/PM, remembered", async () => {
    renderWithProviders(<GeneralSettings />);

    expect(screen.getByRole("radiogroup", { name: "Time format" })).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "24-hour" })).toBeChecked();

    await userEvent.click(screen.getByRole("radio", { name: "12-hour (AM/PM)" }));

    expect(screen.getByRole("radio", { name: "12-hour (AM/PM)" })).toBeChecked();
    expect(window.localStorage.getItem("contor.timeFormat")).toBe("\"12h\"");
    window.localStorage.clear();
  });

  it("shows the language: English only for now, not changeable yet", () => {
    renderWithProviders(<GeneralSettings />);

    const language = screen.getByRole("combobox", { name: "Language" });
    expect(language).toHaveValue("en");
    expect(language).toBeDisabled();
    expect(screen.getByText("More languages will follow.")).toBeInTheDocument();
  });

  it("lays the settings out as plain rows (label left, control right), no boxes", () => {
    renderWithProviders(<GeneralSettings />);

    const row = screen.getByRole("radiogroup", { name: "Theme" }).closest("[data-slot=settings-row]");
    expect(row).not.toBeNull();
    expect(row?.className).not.toMatch(/\bborder\b(?!-)|rounded|shadow/);
  });
});
