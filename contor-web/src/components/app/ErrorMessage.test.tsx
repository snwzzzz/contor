import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ErrorMessage } from "./ErrorMessage";

describe("ErrorMessage", () => {
  it("renders nothing without a message", () => {
    const { container } = render(<ErrorMessage message={undefined} />);

    expect(container).toBeEmptyDOMElement();
  });

  it("shows a single message as an alert", () => {
    render(<ErrorMessage message="Invalid username or password" />);

    expect(screen.getByRole("alert")).toHaveTextContent("Invalid username or password");
  });

  it("splits combined server messages into a list", () => {
    render(<ErrorMessage message="Password must contain a digit; Password is too common" />);

    expect(screen.getAllByRole("listitem").map((item) => item.textContent)).toEqual([
      "Password must contain a digit",
      "Password is too common",
    ]);
  });
});
