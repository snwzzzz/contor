import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { PictureAvatar } from "./PictureAvatar";

describe("PictureAvatar", () => {
  it("keeps the picture a fixed square that a flex row cannot stretch", () => {
    const { container } = render(<PictureAvatar label="Microsoft" src="/api/senders/logos/microsoft.com" size={32} />);

    const img = container.querySelector("img");
    expect(img).toHaveStyle({ width: "32px", height: "32px" });
    expect(img).toHaveClass("shrink-0");
  });

  it("does not pin itself to the top of its row, so it stays centered inside buttons", () => {
    const { container } = render(<PictureAvatar label="Microsoft" src="/api/senders/logos/microsoft.com" />);

    expect(container.querySelector("img")).not.toHaveClass("self-start");
    expect(render(<PictureAvatar label="Microsoft" />).container.firstElementChild).not.toHaveClass("self-start");
  });

  it("falls back to the tinted initial when the picture fails to load", () => {
    const { container } = render(<PictureAvatar label="Microsoft" src="/api/senders/logos/microsoft.com" size={32} />);

    fireEvent.error(container.querySelector("img")!);

    expect(container.querySelector("img")).toBeNull();
    expect(container.firstElementChild).toHaveStyle({ width: "32px", height: "32px" });
    expect(container.firstElementChild).toHaveTextContent("M");
  });

  it("trims the initial to its cap height so the letter itself sits in the middle of the circle", () => {
    render(<PictureAvatar label="Nordhafen" />);

    expect(screen.getByText("N")).toHaveClass("leading-none", "[text-box:trim-both_cap_alphabetic]");
  });
});
