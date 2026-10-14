import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { EmptyState } from "./EmptyState";

describe("EmptyState", () => {
  it("explains what is missing and offers an action", () => {
    render(<EmptyState title="No mailbox connected" description="Comes in a later phase." action={<a href="/inbox">Settings</a>} />);

    expect(screen.getByRole("heading", { name: "No mailbox connected" })).toBeInTheDocument();
    expect(screen.getByText("Comes in a later phase.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Settings" })).toBeInTheDocument();
  });
});
