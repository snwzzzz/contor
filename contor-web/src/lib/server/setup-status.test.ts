// @vitest-environment node
import { afterEach, describe, expect, it, vi } from "vitest";
import { isSetupRequired } from "./setup-status";

describe("isSetupRequired", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("asks the backend directly and reports a pending setup", async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => Response.json({ setupRequired: true }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(isSetupRequired()).resolves.toBe(true);
    expect(String(fetchMock.mock.calls[0][0])).toBe("http://127.0.0.1:8080/api/setup");
  });

  it("treats 401 (an owner exists) as not required", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(null, { status: 401 })));

    await expect(isSetupRequired()).resolves.toBe(false);
  });

  it("treats an unreachable backend as not required", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => Promise.reject(new TypeError("fetch failed"))));

    await expect(isSetupRequired()).resolves.toBe(false);
  });
});
