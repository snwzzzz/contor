import { describe, expect, it } from "vitest";
import { draftAsText, MAILTO_MAX_LENGTH, mailtoLink, placeholdersIn, splitPlaceholders } from "./reply-draft";

const draft = {
  to: [{ name: "Anna Berger", address: "anna@acme.com" }],
  cc: [{ address: "bob@acme.com" }, { name: "Eve", address: "eve+news@acme.com" }],
  subject: "Re: Workshop & Preise?",
  body: "Hallo Anna,\n\nam [Tag] passt es – 100 % sicher.\n\nMax",
};

describe("placeholders", () => {
  it("finds each placeholder once, in order", () => {
    expect(placeholdersIn("Am [Tag] um [Uhrzeit], also [Tag].")).toEqual(["[Tag]", "[Uhrzeit]"]);
    expect(placeholdersIn("No brackets here")).toEqual([]);
  });

  it("splits a text into plain parts and placeholders", () => {
    expect(splitPlaceholders("Am [Tag] gerne")).toEqual([
      { text: "Am ", placeholder: false },
      { text: "[Tag]", placeholder: true },
      { text: " gerne", placeholder: false },
    ]);
  });
});

describe("mailtoLink", () => {
  it("encodes recipients, subject and body with CRLF line breaks", () => {
    const link = mailtoLink(draft);

    expect(link).toBe(
      "mailto:anna@acme.com?cc=bob@acme.com,eve%2Bnews@acme.com&subject=Re%3A%20Workshop%20%26%20Preise%3F" +
        "&body=Hallo%20Anna%2C%0D%0A%0D%0Aam%20%5BTag%5D%20passt%20es%20%E2%80%93%20100%20%25%20sicher.%0D%0A%0D%0AMax",
    );
  });

  it("leaves out an empty cc", () => {
    expect(mailtoLink({ ...draft, cc: [] })).toMatch(/^mailto:anna@acme\.com\?subject=/);
  });

  it("gives no link when the draft is too long for one", () => {
    expect(mailtoLink({ ...draft, body: "x".repeat(MAILTO_MAX_LENGTH) })).toBeNull();
  });
});

describe("draftAsText", () => {
  it("puts recipients and subject above the body", () => {
    expect(draftAsText(draft)).toBe(
      "To: Anna Berger <anna@acme.com>\nCc: bob@acme.com, Eve <eve+news@acme.com>\nSubject: Re: Workshop & Preise?\n\n" + draft.body,
    );
  });
});
