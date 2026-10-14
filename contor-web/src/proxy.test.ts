// @vitest-environment node
import { NextRequest } from "next/server";
import { describe, expect, it } from "vitest";
import { proxy } from "./proxy";

function request(path: string, cookie?: string) {
  return new NextRequest(new URL(path, "http://localhost:3000"), {
    headers: cookie ? { cookie } : {},
  });
}

describe("proxy", () => {
  it("redirects to the login without a session cookie and remembers the target", () => {
    const response = proxy(request("/collections/3?page=2"));

    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe("http://localhost:3000/login?next=%2Fcollections%2F3%3Fpage%3D2");
  });

  it("lets public pages through without a session", () => {
    expect(proxy(request("/login")).headers.get("location")).toBeNull();
    expect(proxy(request("/setup")).headers.get("location")).toBeNull();
  });

  it("lets requests with a session cookie through", () => {
    expect(proxy(request("/inbox", "CONTOR_SESSION=abc")).headers.get("location")).toBeNull();
  });

  it("sets a strict nonce-based content security policy", () => {
    const csp = proxy(request("/login")).headers.get("content-security-policy") ?? "";

    const nonce = /'nonce-([^']+)'/.exec(csp)?.[1];
    expect(nonce).toBeTruthy();
    expect(csp).toContain(`script-src 'self' 'nonce-${nonce}' 'strict-dynamic'`);
    expect(csp).toContain("frame-ancestors 'none'");
    expect(csp).toContain("object-src 'none'");
    expect(proxy(request("/login")).headers.get("content-security-policy")).not.toContain(nonce);
  });
});
