import { describe, expect, it } from "vitest";
import { AVATAR_TONES, avatarTone, domainOf, initialOf } from "./mail-format";

describe("mail-format", () => {
  it("uses the first letter or digit as avatar initial", () => {
    expect(initialOf("binford Ltd.")).toBe("B");
    expect(initialOf("\"Quoted\" Name")).toBe("Q");
    expect(initialOf("")).toBe("?");
  });

  it("gives every name a stable avatar tint", () => {
    expect(avatarTone("Binford Ltd.")).toBe(avatarTone("Binford Ltd."));
    expect(AVATAR_TONES).toContain(avatarTone("Binford Ltd."));
    expect(new Set(["Alice", "Bank of America", "Marvin", "MasterCard", "Siemens"].map(avatarTone)).size).toBeGreaterThan(1);
  });

  it("takes the lower-cased domain of an address", () => {
    expect(domainOf("Talent@Siemens.COM")).toBe("siemens.com");
    expect(domainOf("no-at-sign")).toBeUndefined();
    expect(domainOf(undefined)).toBeUndefined();
  });
});
