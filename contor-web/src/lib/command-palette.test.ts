import { describe, expect, it, vi } from "vitest";
import { OPEN_COMMAND_PALETTE_EVENT, openCommandPalette } from "./command-palette";

describe("openCommandPalette", () => {
  it("asks the palette in the top bar to open", () => {
    const listener = vi.fn();
    window.addEventListener(OPEN_COMMAND_PALETTE_EVENT, listener);

    openCommandPalette();

    expect(listener).toHaveBeenCalledOnce();
    window.removeEventListener(OPEN_COMMAND_PALETTE_EVENT, listener);
  });
});
