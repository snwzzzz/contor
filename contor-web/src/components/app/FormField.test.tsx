import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { FormField } from "./FormField";

describe("FormField", () => {
  it("labels the input", () => {
    render(<FormField id="username" label="Username" />);

    expect(screen.getByLabelText("Username")).toHaveAttribute("id", "username");
  });

  it("marks the input invalid and announces the error", () => {
    render(<FormField id="email" label="Email" error="Email is not valid" />);

    const input = screen.getByLabelText("Email");
    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(input).toHaveAccessibleDescription("Email is not valid");
  });

  it("shows a description when there is no error", () => {
    render(<FormField id="token" label="Setup token" description="Printed in the backend log" />);

    expect(screen.getByLabelText("Setup token")).toHaveAccessibleDescription("Printed in the backend log");
  });
});
