import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { PageHeader } from "./PageHeader";

describe("PageHeader", () => {
  it("renders the page title, description and actions", () => {
    render(<PageHeader title="Proposals" description="What the AI suggests" actions={<button>Refresh</button>} />);

    expect(screen.getByRole("heading", { level: 1, name: "Proposals" })).toBeInTheDocument();
    expect(screen.getByText("What the AI suggests")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Refresh" })).toBeInTheDocument();
  });
});
