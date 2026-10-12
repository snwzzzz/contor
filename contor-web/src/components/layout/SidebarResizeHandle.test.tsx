import { fireEvent, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { renderWithProviders } from "@/test/render";
import { ResizableSidebarProvider, SIDEBAR_WIDTH_KEY } from "./ResizableSidebarProvider";
import { SidebarResizeHandle } from "./SidebarResizeHandle";

function renderHandle() {
  return renderWithProviders(
    <ResizableSidebarProvider>
      <SidebarResizeHandle />
    </ResizableSidebarProvider>,
  );
}

function wrapperWidth(container: HTMLElement) {
  return (container.querySelector("[data-slot=sidebar-wrapper]") as HTMLElement).style.getPropertyValue("--sidebar-width");
}

describe("SidebarResizeHandle", () => {
  afterEach(() => window.localStorage.clear());

  it("resizes with the keyboard within limits and remembers the width", () => {
    const { container } = renderHandle();
    const handle = screen.getByRole("separator", { name: "Resize sidebar" });
    expect(handle).toHaveAttribute("aria-valuenow", "256");

    fireEvent.keyDown(handle, { key: "ArrowRight" });
    expect(handle).toHaveAttribute("aria-valuenow", "272");
    expect(wrapperWidth(container)).toBe("272px");
    expect(window.localStorage.getItem(SIDEBAR_WIDTH_KEY)).toBe("272");

    fireEvent.keyDown(handle, { key: "End" });
    expect(handle).toHaveAttribute("aria-valuenow", "400");
    fireEvent.keyDown(handle, { key: "Home" });
    expect(handle).toHaveAttribute("aria-valuenow", "208");
  });

  it("resizes by dragging", () => {
    const { container } = renderHandle();
    const handle = screen.getByRole("separator", { name: "Resize sidebar" });

    fireEvent.pointerDown(handle, { clientX: 256, pointerId: 1 });
    fireEvent.pointerMove(handle, { clientX: 320, pointerId: 1 });
    expect(wrapperWidth(container)).toBe("320px");
    fireEvent.pointerUp(handle, { clientX: 320, pointerId: 1 });

    expect(window.localStorage.getItem(SIDEBAR_WIDTH_KEY)).toBe("320");
  });

  it("starts from the saved width", () => {
    window.localStorage.setItem(SIDEBAR_WIDTH_KEY, "300");
    renderHandle();

    expect(screen.getByRole("separator", { name: "Resize sidebar" })).toHaveAttribute("aria-valuenow", "300");
  });
});
