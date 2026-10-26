import { describe, expect, it } from "vitest";
import { providerLabel } from "./mail-providers";

describe("providerLabel", () => {
  it("names each provider by its product", () => {
    expect(providerLabel("MICROSOFT_GRAPH")).toBe("Outlook");
    expect(providerLabel("GMAIL")).toBe("Gmail");
    expect(providerLabel("DEMO")).toBe("Demo");
  });
});
