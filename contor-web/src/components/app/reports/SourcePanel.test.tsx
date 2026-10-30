import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { ReportSourceDto } from "@/lib/api/generated/model/reportSourceDto";
import { mockApi } from "@/test/api-mock";
import { renderWithProviders } from "@/test/render";
import { SourcePanel } from "./SourcePanel";

const appItem: ReportSourceDto = {
  marker: 1,
  kind: "APP_ITEM",
  label: "Shop · Orders · SO-155",
  sourceState: "CURRENT",
  apiId: 7,
  apiItemId: 9,
  quote: '{"id":"SO-155","state":"shipped"}',
};

describe("SourcePanel", () => {
  it("shows an item of your apps with the data it was cited with and a link to the app", async () => {
    mockApi({});
    const onSelect = vi.fn();
    const { rerender } = renderWithProviders(<SourcePanel sources={[appItem]} selected={undefined} quotes={new Map()} onSelect={onSelect} />);

    await userEvent.click(screen.getByRole("button", { name: /Shop · Orders · SO-155/ }));
    expect(onSelect).toHaveBeenCalledWith(1);
    rerender(<SourcePanel sources={[appItem]} selected={1} quotes={new Map()} onSelect={onSelect} />);

    expect(screen.getByText('{"id":"SO-155","state":"shipped"}')).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Open app/ })).toHaveAttribute("href", "/apps/7");
  });

  it("keeps the data of an item whose app is gone", () => {
    mockApi({});
    renderWithProviders(
      <SourcePanel sources={[{ ...appItem, label: "Removed app item", apiId: undefined, apiItemId: undefined, sourceState: "DELETED" }]} selected={1}
                   quotes={new Map()} onSelect={vi.fn()} />,
    );

    expect(screen.getByText("No longer exists")).toBeInTheDocument();
    expect(screen.getByText('{"id":"SO-155","state":"shipped"}')).toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
});
