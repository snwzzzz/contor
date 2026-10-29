import { describe, expect, it } from "vitest";
import type { ExternalApiDto } from "@/lib/api/generated/model/externalApiDto";
import type { ExternalApiEndpointDto } from "@/lib/api/generated/model/externalApiEndpointDto";
import {
  apiHost,
  detectItems,
  discoveredDraft,
  draftOf,
  emptyDraft,
  emptyEndpoint,
  endpointDraftOf,
  intervalLabel,
  prettyBody,
  statusMark,
  toNewCommand,
  toNewEndpointCommand,
  toTestCommand,
  toUpdateCommand,
  toUpdateEndpointCommand,
  validateAuth,
  validateBasics,
  validateEndpoint,
  type AppDraft,
  type HeaderRow,
} from "./external-apis";

const saved: ExternalApiDto = {
  id: 7,
  name: "Shop",
  slug: "app_shop",
  description: "Orders",
  baseUrl: "https://api.shop.example/v1",
  docsUrl: "https://docs.shop.example",
  authKind: "API_KEY_HEADER",
  authConfig: { headerName: "X-Api-Key" },
  hasSecret: true,
  headers: [{ name: "Accept-Language", value: "de" }],
  secretHeaderNames: ["X-Tenant"],
  pollIntervalMinutes: 360,
  enabled: true,
  aiAnalysis: true,
  status: "ACTIVE",
  failureCount: 0,
  dailyRequestLimit: 200,
  endpointCount: 1,
  createdAt: "2026-10-29T08:00:00Z",
};

function row(name: string, value: string, overrides: Partial<HeaderRow> = {}): HeaderRow {
  return { id: name, name, value, secret: false, stored: false, ...overrides };
}

function draft(overrides: Partial<AppDraft> = {}): AppDraft {
  return { ...emptyDraft(), name: "Shop", baseUrl: "https://api.shop.example/v1", ...overrides };
}

describe("apiHost", () => {
  it("is the host of the base URL, or the input when it is no URL", () => {
    expect(apiHost("https://api.shop.example/v1")).toBe("api.shop.example");
    expect(apiHost("not a url")).toBe("not a url");
  });
});

describe("intervalLabel", () => {
  it("names the presets and spells out other intervals", () => {
    expect(intervalLabel(15)).toBe("Every 15 minutes");
    expect(intervalLabel(60)).toBe("Every hour");
    expect(intervalLabel(360)).toBe("Every 6 hours");
    expect(intervalLabel(1440)).toBe("Daily");
    expect(intervalLabel(120)).toBe("Every 2 hours");
    expect(intervalLabel(45)).toBe("Every 45 minutes");
  });
});

describe("statusMark", () => {
  it("marks only states that need attention", () => {
    expect(statusMark("ACTIVE")).toBeNull();
    expect(statusMark("FAILING")).toEqual({ label: "Failing", dot: "bg-destructive" });
    expect(statusMark("NEEDS_SETUP")).toEqual({ label: "Needs setup", dot: "bg-warning" });
    expect(statusMark("PAUSED")).toEqual({ label: "Paused", dot: "bg-muted-foreground" });
  });
});

describe("validateBasics", () => {
  it("accepts a name and an HTTPS base URL", () => {
    expect(validateBasics(draft())).toEqual({});
  });

  it("needs a name and an HTTPS base URL without a query", () => {
    expect(validateBasics(draft({ name: " ", baseUrl: "" }))).toEqual({ name: "Enter a name.", baseUrl: "Enter the base URL." });
    expect(validateBasics(draft({ baseUrl: "http://api.shop.example" })).baseUrl).toBe("The base URL must start with https://.");
    expect(validateBasics(draft({ baseUrl: "https://api.shop.example?x=1" })).baseUrl).toBe(
      "The base URL has no query; add query parameters to an endpoint.",
    );
    expect(validateBasics(draft({ baseUrl: "https://" })).baseUrl).toBe("This is not a valid URL.");
  });

  it("checks the documentation link only when given", () => {
    expect(validateBasics(draft({ docsUrl: "ftp://docs" })).docsUrl).toBe("The documentation link must start with https://.");
    expect(validateBasics(draft({ docsUrl: "https://docs.shop.example" }))).toEqual({});
  });

  it("limits the name to 80 characters", () => {
    expect(validateBasics(draft({ name: "x".repeat(81) })).name).toBe("At most 80 characters.");
  });
});

describe("validateAuth", () => {
  it("asks for the settings and the secret of the auth kind", () => {
    expect(validateAuth(draft({ authKind: "API_KEY_HEADER" }))).toEqual({
      headerName: "Enter the header name.",
      secret: "Enter the API key.",
    });
    expect(validateAuth(draft({ authKind: "API_KEY_QUERY" }))).toEqual({
      queryParam: "Enter the parameter name.",
      secret: "Enter the API key.",
    });
    expect(validateAuth(draft({ authKind: "BEARER" }))).toEqual({ secret: "Enter the token." });
    expect(validateAuth(draft({ authKind: "BASIC" }))).toEqual({ username: "Enter the user name.", secret: "Enter the password." });
    expect(validateAuth(draft({ authKind: "OAUTH2_CLIENT_CREDENTIALS" }))).toEqual({
      tokenUrl: "Enter the token URL.",
      clientId: "Enter the client ID.",
      secret: "Enter the client secret.",
    });
    expect(validateAuth(draft({ authKind: "NONE" }))).toEqual({});
  });

  it("does not ask for a secret that is stored and kept", () => {
    expect(validateAuth(draft({ authKind: "BEARER", secret: null }))).toEqual({});
  });

  it("wants an HTTPS token URL", () => {
    const errors = validateAuth(
      draft({ authKind: "OAUTH2_CLIENT_CREDENTIALS", authConfig: { ...emptyDraft().authConfig, tokenUrl: "http://x", clientId: "c" }, secret: "s" }),
    );
    expect(errors).toEqual({ tokenUrl: "The token URL must start with https://." });
  });

  it("needs header names, each once in any case, plain or secret", () => {
    const errors = validateAuth(
      draft({
        headers: [row("a", "1", { id: "1", name: "" }), row("X-One", "1", { id: "2" }), row("x-one", "2", { id: "3", secret: true })],
      }),
    );
    expect(errors).toEqual({ "header.1": "Enter the header name.", "header.3": "X-One is given twice." });
  });

  it("asks for secret header values, and again for stored ones once the secret headers change", () => {
    const stored = row("X-Tenant", "", { secret: true, stored: true });
    expect(validateAuth(draft({ headers: [stored] }))).toEqual({});
    expect(validateAuth(draft({ headers: [stored, row("X-New", "", { secret: true })] }))).toEqual({
      "header.X-New": "Enter the value.",
      "header.X-Tenant": "Enter the value again: secret headers are saved together.",
    });
  });
});

describe("draftOf", () => {
  it("keeps stored secrets by name only", () => {
    const result = draftOf(saved);

    expect(result.secret).toBeNull();
    expect(result.authConfig.headerName).toBe("X-Api-Key");
    expect(result.authConfig.tokenUrl).toBe("");
    expect(result.headers.map(({ name, value, secret, stored }) => ({ name, value, secret, stored }))).toEqual([
      { name: "Accept-Language", value: "de", secret: false, stored: false },
      { name: "X-Tenant", value: "", secret: true, stored: true },
    ]);
  });

  it("starts with an empty secret when none is stored", () => {
    expect(draftOf({ ...saved, hasSecret: false }).secret).toBe("");
  });
});

describe("toNewCommand", () => {
  it("trims, drops empty optional values and splits plain from secret headers", () => {
    const command = toNewCommand(
      draft({
        name: " Shop ",
        description: "",
        authKind: "BEARER",
        secret: "tok",
        headers: [row("Accept-Language", "de"), row("X-Tenant", "t1", { secret: true })],
        pollIntervalMinutes: 60,
        aiAnalysis: false,
      }),
    );

    expect(command).toEqual({
      name: "Shop",
      baseUrl: "https://api.shop.example/v1",
      authKind: "BEARER",
      authConfig: { headerName: null, queryParam: null, tokenUrl: null, scope: null, username: null, clientId: null },
      secret: "tok",
      headers: [{ name: "Accept-Language", value: "de" }],
      secretHeaders: [{ name: "X-Tenant", value: "t1" }],
      pollIntervalMinutes: 60,
      enabled: true,
      aiAnalysis: false,
    });
  });

  it("sends no secret without an auth kind that uses one", () => {
    expect(toNewCommand(draft({ authKind: "NONE", secret: "left over" })).secret).toBeUndefined();
  });
});

describe("toUpdateCommand", () => {
  it("is empty when nothing changed", () => {
    expect(toUpdateCommand(draftOf(saved), saved)).toEqual({});
  });

  it("sends only what changed, and removes cleared text with an empty string", () => {
    const changed = { ...draftOf(saved), name: "Shop AT", docsUrl: "", pollIntervalMinutes: 60 };

    expect(toUpdateCommand(changed, saved)).toEqual({ name: "Shop AT", docsUrl: "", pollIntervalMinutes: 60 });
  });

  it("sends a new secret, and removes the stored one when the auth kind no longer uses one", () => {
    expect(toUpdateCommand({ ...draftOf(saved), secret: "new key" }, saved)).toEqual({ secret: "new key" });
    expect(toUpdateCommand({ ...draftOf(saved), authKind: "NONE" }, saved)).toEqual({
      authKind: "NONE",
      authConfig: { headerName: "X-Api-Key", queryParam: null, tokenUrl: null, scope: null, username: null, clientId: null },
      secret: "",
    });
  });

  it("sends the auth settings with the kind when they change", () => {
    const changed = draftOf(saved);
    changed.authConfig = { ...changed.authConfig, headerName: "Api-Key" };

    expect(toUpdateCommand(changed, saved)).toEqual({
      authKind: "API_KEY_HEADER",
      authConfig: { headerName: "Api-Key", queryParam: null, tokenUrl: null, scope: null, username: null, clientId: null },
    });
  });

  it("sends the plain headers when they change and all secret headers when one changes", () => {
    const base = draftOf(saved);
    const plain = { ...base, headers: base.headers.filter((header) => header.secret) };
    expect(toUpdateCommand(plain, saved)).toEqual({ headers: [] });

    const secret = { ...base, headers: [...base.headers.slice(0, 1), row("X-Tenant", "t2", { secret: true })] };
    expect(toUpdateCommand(secret, saved)).toEqual({ secretHeaders: [{ name: "X-Tenant", value: "t2" }] });

    const removed = { ...base, headers: base.headers.slice(0, 1) };
    expect(toUpdateCommand(removed, saved)).toEqual({ secretHeaders: [] });
  });
});

describe("toTestCommand", () => {
  it("calls the endpoint with the unsaved settings, filling stored secrets from the saved app", () => {
    const endpoint = { ...emptyEndpoint(), method: "POST" as const, path: "/orders/search", query: [row("limit", "5")], body: '{"status":"open"}' };

    expect(toTestCommand(draftOf(saved), endpoint, saved.id)).toEqual({
      apiId: 7,
      baseUrl: "https://api.shop.example/v1",
      authKind: "API_KEY_HEADER",
      authConfig: { headerName: "X-Api-Key", queryParam: null, tokenUrl: null, scope: null, username: null, clientId: null },
      headers: [{ name: "Accept-Language", value: "de" }],
      method: "POST",
      path: "/orders/search",
      query: { limit: "5" },
      body: { status: "open" },
    });
  });

  it("sends no body for GET", () => {
    const command = toTestCommand(draft(), { ...emptyEndpoint(), path: "/orders", body: '{"a":1}' });

    expect(command.body).toBeUndefined();
    expect(command.apiId).toBeUndefined();
  });
});

describe("endpoints", () => {
  const dto: ExternalApiEndpointDto = {
    id: 3,
    apiId: 7,
    name: "Orders",
    method: "POST",
    path: "/orders/search",
    query: { limit: "5" },
    body: { status: "open" },
    itemsPath: "$.data[*]",
    source: "USER",
    enabled: true,
    createdAt: "2026-10-29T08:00:00Z",
  };

  it("validates name, path, query and body", () => {
    expect(validateEndpoint({ ...emptyEndpoint(), name: "Orders", path: "/orders" })).toEqual({});
    expect(validateEndpoint(emptyEndpoint())).toEqual({ name: "Enter a name.", path: "Enter the path." });
    expect(validateEndpoint({ ...emptyEndpoint(), name: "x", path: "orders" }).path).toBe("The path starts with /.");
    expect(validateEndpoint({ ...emptyEndpoint(), name: "x", path: "/orders?x=1" }).path).toBe("Add query parameters below, not in the path.");
    expect(validateEndpoint({ ...emptyEndpoint(), name: "x", path: "/o", query: [row("", "1", { id: "q" })] })).toEqual({
      "query.q": "Enter the parameter name.",
    });
    expect(validateEndpoint({ ...emptyEndpoint(), name: "x", path: "/o", method: "POST", body: "[1]" }).body).toBe(
      "The body is a JSON object, e.g. {}.",
    );
    expect(validateEndpoint({ ...emptyEndpoint(), name: "x", path: "/o", method: "POST", body: "{" }).body).toBe(
      "The body is a JSON object, e.g. {}.",
    );
  });

  it("round-trips a saved endpoint into commands", () => {
    const edited = endpointDraftOf(dto);

    expect(edited.body).toBe('{\n  "status": "open"\n}');
    expect(toNewEndpointCommand(edited)).toEqual({
      name: "Orders",
      method: "POST",
      path: "/orders/search",
      query: { limit: "5" },
      body: { status: "open" },
      itemsPath: "$.data[*]",
      enabled: true,
    });
    expect(toUpdateEndpointCommand({ ...edited, method: "GET", itemsPath: "" })).toEqual({
      name: "Orders",
      method: "GET",
      path: "/orders/search",
      query: { limit: "5" },
      itemsPath: "",
      itemKeyPath: "",
      description: "",
      enabled: true,
    });
  });
});

describe("detectItems", () => {
  it("finds the first list of objects and the key that identifies an item", () => {
    expect(detectItems('[{"id":1},{"id":2}]')).toEqual({ itemsPath: "$[*]", itemKeyPath: "$.id", count: 2 });
    expect(detectItems('{"meta":{"page":1},"data":{"orders":[{"uuid":"a","total":3}]}}')).toEqual({
      itemsPath: "$.data.orders[*]",
      itemKeyPath: "$.uuid",
      count: 1,
    });
    expect(detectItems('{"results":[{"name":"x"}]}')).toEqual({ itemsPath: "$.results[*]", count: 1 });
  });

  it("finds nothing in answers without a list of objects or that are no JSON", () => {
    expect(detectItems('{"tags":["a","b"]}')).toBeNull();
    expect(detectItems('{"weird key":[{"id":1}]}')).toBeNull();
    expect(detectItems("<html>")).toBeNull();
    expect(detectItems(undefined)).toBeNull();
  });
});

describe("prettyBody", () => {
  it("indents JSON and leaves anything else as it is", () => {
    expect(prettyBody('{"a":1}')).toBe('{\n  "a": 1\n}');
    expect(prettyBody('{"a":')).toBe('{"a":');
    expect(prettyBody("plain")).toBe("plain");
  });
});

describe("discoveredDraft", () => {
  it("turns a suggestion into an endpoint draft", () => {
    const draft = discoveredDraft({
      name: "Open orders",
      method: "GET",
      path: "/orders",
      query: { status: "open" },
      itemsPath: "$.data[*]",
      itemKeyPath: "$.id",
      description: "Orders not shipped yet",
      source: "MODEL",
    });

    expect(toNewEndpointCommand(draft)).toEqual({
      name: "Open orders",
      method: "GET",
      path: "/orders",
      query: { status: "open" },
      itemsPath: "$.data[*]",
      itemKeyPath: "$.id",
      description: "Orders not shipped yet",
      enabled: true,
    });
  });

  it("leaves out what the suggestion does not know", () => {
    expect(toNewEndpointCommand(discoveredDraft({ name: "Status", method: "GET", path: "/status", query: {}, source: "DOCS" }))).toEqual({
      name: "Status",
      method: "GET",
      path: "/status",
      query: {},
      enabled: true,
    });
  });
});
