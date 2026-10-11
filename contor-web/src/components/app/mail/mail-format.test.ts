import { describe, expect, it } from "vitest";
import { AVATAR_TONES, avatarTone, domainOf, initialOf, matchesEmail, splitQuoted } from "./mail-format";

describe("mail-format", () => {
  it("splits the quoted history off a plain-text reply", () => {
    const english = "Sounds good.\n\nOn Mon, 2 Mar 2026 at 10:00, Alice <a@x.com> wrote:\n> Here is our offer.\n> Regards";
    expect(splitQuoted(english)).toEqual({ fresh: "Sounds good.", quoted: "On Mon, 2 Mar 2026 at 10:00, Alice <a@x.com> wrote:\n> Here is our offer.\n> Regards" });

    const german = "Passt.\n\nAm 02.03.2026 um 10:00 schrieb Alice <a@x.com>:\n> Angebot";
    expect(splitQuoted(german).fresh).toBe("Passt.");

    const outlook = "Thanks!\n\n-----Original Message-----\nFrom: Alice\nSent: Monday\nHello";
    expect(splitQuoted(outlook).fresh).toBe("Thanks!");
    expect(splitQuoted("Thanks!\n> quoted line\n> more").fresh).toBe("Thanks!");
  });

  it("keeps a text without quotes, or that only consists of quotes, as it is", () => {
    expect(splitQuoted("Just a message.\nSecond line.")).toEqual({ fresh: "Just a message.\nSecond line.", quoted: null });
    expect(splitQuoted("> only\n> quotes")).toEqual({ fresh: "> only\n> quotes", quoted: null });
  });

  it("matches an email summary in the chosen field, case-insensitively", () => {
    const mail = { subject: "Invoice 42", fromName: "Anna Berger", fromAddress: "anna@acme.com", snippet: "Please pay soon" };
    expect(matchesEmail(mail, "", "ALL")).toBe(true);
    expect(matchesEmail(mail, "INVOICE", "ALL")).toBe(true);
    expect(matchesEmail(mail, "invoice", "SUBJECT")).toBe(true);
    expect(matchesEmail(mail, "invoice", "PEOPLE")).toBe(false);
    expect(matchesEmail(mail, "@acme.com", "PEOPLE")).toBe(true);
    expect(matchesEmail(mail, "berger", "PEOPLE")).toBe(true);
    expect(matchesEmail(mail, "pay", "CONTENT")).toBe(true);
    expect(matchesEmail(mail, "pay", "SUBJECT")).toBe(false);
  });

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
