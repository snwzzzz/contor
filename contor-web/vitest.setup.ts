import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach, vi } from "vitest";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  if (typeof document === "undefined") return;
  document.cookie.split("; ").forEach((cookie) => {
    const name = cookie.split("=")[0];
    if (name) document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
  });
});

// jsdom gaps used by Radix, cmdk and the shadcn sidebar.
if (typeof window !== "undefined") {
  window.matchMedia ??= (query: string) =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      dispatchEvent: () => false,
    }) as MediaQueryList;
  window.ResizeObserver ??= class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
  Element.prototype.scrollIntoView ??= () => {};
  Element.prototype.hasPointerCapture ??= () => false;
  Element.prototype.releasePointerCapture ??= () => {};
  // No DragEvent in jsdom: fireEvent.drag* would build a plain Event without pointer coordinates (the kanban board
  // places a dropped card at clientY). Testing Library sets `dataTransfer` itself.
  window.DragEvent ??= class DragEvent extends MouseEvent {
    readonly dataTransfer: DataTransfer | null = null;
  } as typeof DragEvent;
}
