/** Full page load (drops all in-memory state, e.g. cached API data after logout). Separate module so tests can mock it. */
export function hardNavigate(path: string) {
  window.location.assign(path);
}
