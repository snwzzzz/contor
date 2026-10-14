import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, apiFetch } from "./fetcher";

const fetchMock = vi.fn<typeof fetch>();

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

describe("apiFetch", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
  });

  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
  });

  it("returns the parsed JSON body and sends same-origin credentials", async () => {
    fetchMock.mockResolvedValue(jsonResponse({ name: "contor", version: "1" }));

    const result = await apiFetch<{ name: string }>("/api/system/version", { method: "GET" });

    expect(result).toEqual({ name: "contor", version: "1" });
    const [, init] = fetchMock.mock.calls[0];
    expect(init?.credentials).toBe("same-origin");
  });

  it("sends the CSRF token from the XSRF-TOKEN cookie on mutating requests", async () => {
    document.cookie = "XSRF-TOKEN=abc123; path=/";
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await apiFetch("/api/things", { method: "POST", body: "{}" });

    const headers = new Headers(fetchMock.mock.calls[0][1]?.headers);
    expect(headers.get("X-XSRF-TOKEN")).toBe("abc123");
  });

  it("fetches a CSRF token first when the cookie is missing", async () => {
    fetchMock.mockImplementation(async (input) => {
      if (String(input) === "/api/auth/csrf") {
        document.cookie = "XSRF-TOKEN=fresh; path=/";
        return new Response(null, { status: 204 });
      }
      return new Response(null, { status: 204 });
    });

    await apiFetch("/api/things", { method: "POST", body: "{}" });

    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual(["/api/auth/csrf", "/api/things"]);
    expect(new Headers(fetchMock.mock.calls[1][1]?.headers).get("X-XSRF-TOKEN")).toBe("fresh");
  });

  it("does not send the CSRF header on safe requests", async () => {
    document.cookie = "XSRF-TOKEN=abc123; path=/";
    fetchMock.mockResolvedValue(jsonResponse({}));

    await apiFetch("/api/things", { method: "GET" });

    const headers = new Headers(fetchMock.mock.calls[0][1]?.headers);
    expect(headers.has("X-XSRF-TOKEN")).toBe(false);
  });

  it("returns undefined for empty responses", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await expect(apiFetch("/api/things/1", { method: "DELETE" })).resolves.toBeUndefined();
  });

  it("throws an ApiError carrying the ErrorResponse message and status", async () => {
    fetchMock.mockResolvedValue(jsonResponse({ message: "Tag not found with id: 9", status: 404 }, 404));

    const error = await apiFetch("/api/tags/9", { method: "GET" }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 404, message: "Tag not found with id: 9" });
  });

  it("falls back to a generic message when the error body is not an ErrorResponse", async () => {
    fetchMock.mockResolvedValue(new Response("<html>bad gateway</html>", { status: 502 }));

    const error = await apiFetch("/api/x", { method: "GET" }).catch((e: unknown) => e);

    expect(error).toMatchObject({ status: 502, message: "Request failed (502)" });
  });
});
