import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { EmailHtmlBody } from "./EmailHtmlBody";

/** ResizeObserver whose callbacks the test triggers itself. */
function captureResizeObservers() {
  const callbacks: ResizeObserverCallback[] = [];
  vi.stubGlobal(
    "ResizeObserver",
    class {
      constructor(callback: ResizeObserverCallback) {
        callbacks.push(callback);
      }
      observe() {}
      unobserve() {}
      disconnect() {}
    },
  );
  return () => act(() => callbacks.forEach((callback) => callback([], {} as ResizeObserver)));
}

function setContentHeight(frame: HTMLIFrameElement, height: number) {
  const body = frame.contentDocument?.body as HTMLElement;
  Object.defineProperty(body, "scrollHeight", { configurable: true, value: height });
}

describe("EmailHtmlBody", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("is as tall as the mail, also for short mails", () => {
    render(<EmailHtmlBody html="<p>Hi</p>" />);
    const frame = screen.getByTitle("Email content") as HTMLIFrameElement;

    setContentHeight(frame, 120);
    fireEvent.load(frame);

    expect(frame.style.height).toBe("120px");
  });

  it("measures again when its width changes (e.g. a scrollbar appears) and the mail reflows", () => {
    const resize = captureResizeObservers();
    render(<EmailHtmlBody html="<p>Hi</p>" />);
    const frame = screen.getByTitle("Email content") as HTMLIFrameElement;
    setContentHeight(frame, 400);
    fireEvent.load(frame);

    setContentHeight(frame, 520);
    resize();

    expect(frame.style.height).toBe("520px");
  });

  it("does not measure while the frame's document is still empty (between loads)", () => {
    const resize = captureResizeObservers();
    render(<EmailHtmlBody html="<p>Hi</p>" />);
    const frame = screen.getByTitle("Email content") as HTMLIFrameElement;
    setContentHeight(frame, 300);
    fireEvent.load(frame);
    const empty = document.implementation.createHTMLDocument("");
    empty.removeChild(empty.documentElement);
    Object.defineProperty(frame, "contentDocument", { configurable: true, get: () => empty });

    expect(() => resize()).not.toThrow();
    expect(frame.style.height).toBe("300px");
  });
});
