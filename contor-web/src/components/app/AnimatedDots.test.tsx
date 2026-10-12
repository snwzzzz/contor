import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AnimatedDots } from "./AnimatedDots";

describe("AnimatedDots", () => {
  it("renders three staggered dots hidden from assistive technology", () => {
    const { container } = render(<AnimatedDots />);

    const wrapper = container.firstElementChild;
    expect(wrapper).toHaveAttribute("aria-hidden", "true");
    const dots = wrapper?.querySelectorAll("span") ?? [];
    expect(dots).toHaveLength(3);
    expect([...dots].map((dot) => (dot as HTMLElement).style.animationDelay)).toEqual(["0ms", "200ms", "400ms"]);
  });
});
