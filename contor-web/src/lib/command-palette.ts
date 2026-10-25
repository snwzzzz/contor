/** Window event the command palette in the top bar listens for. */
export const OPEN_COMMAND_PALETTE_EVENT = "contor:open-command-palette";

/** Opens the command palette from anywhere in the app (e.g. the home page's "More" tile). */
export function openCommandPalette() {
  window.dispatchEvent(new Event(OPEN_COMMAND_PALETTE_EVENT));
}
