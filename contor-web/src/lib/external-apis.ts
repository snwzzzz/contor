import type { ApiAuthConfigDto } from "@/lib/api/generated/model/apiAuthConfigDto";
import type { ApiAuthType } from "@/lib/api/generated/model/apiAuthType";
import type { ApiHeaderDto } from "@/lib/api/generated/model/apiHeaderDto";
import type { ApiState } from "@/lib/api/generated/model/apiState";
import type { EndpointMethod } from "@/lib/api/generated/model/endpointMethod";
import type { ExternalApiDto } from "@/lib/api/generated/model/externalApiDto";
import type { ExternalApiEndpointDto } from "@/lib/api/generated/model/externalApiEndpointDto";
import type { NewExternalApiCommand } from "@/lib/api/generated/model/newExternalApiCommand";
import type { NewExternalApiEndpointCommand } from "@/lib/api/generated/model/newExternalApiEndpointCommand";
import type { TestExternalApiCommand } from "@/lib/api/generated/model/testExternalApiCommand";
import type { UpdateExternalApiCommand } from "@/lib/api/generated/model/updateExternalApiCommand";
import type { UpdateExternalApiEndpointCommand } from "@/lib/api/generated/model/updateExternalApiEndpointCommand";
import type { Tint } from "@/lib/collection-tint";

/** Form state and command building of the add/edit app modal (external APIs). */

const MAX_NAME = 80;

/** The interval choices of the modal; the API allows 15 minutes to a week. */
export const INTERVAL_PRESETS = [
  { minutes: 15, label: "Every 15 minutes" },
  { minutes: 60, label: "Every hour" },
  { minutes: 360, label: "Every 6 hours" },
  { minutes: 1440, label: "Daily" },
] as const;

export const DEFAULT_INTERVAL = 360;

type AuthField = keyof AuthConfigDraft;

interface AuthKindInfo {
  label: string;
  /** Settings this kind needs, in form order. */
  fields: readonly AuthField[];
  /** The secret's name in the form; none for kinds without a secret. */
  secretLabel?: string;
}

export const AUTH_KINDS: Record<ApiAuthType, AuthKindInfo> = {
  NONE: { label: "None", fields: [] },
  API_KEY_HEADER: { label: "API key in a header", fields: ["headerName"], secretLabel: "API key" },
  API_KEY_QUERY: { label: "API key in the query", fields: ["queryParam"], secretLabel: "API key" },
  BEARER: { label: "Bearer token", fields: [], secretLabel: "Token" },
  BASIC: { label: "User name and password", fields: ["username"], secretLabel: "Password" },
  OAUTH2_CLIENT_CREDENTIALS: { label: "OAuth client credentials", fields: ["tokenUrl", "clientId", "scope"], secretLabel: "Client secret" },
};

export const AUTH_FIELD_LABELS: Record<AuthField, string> = {
  headerName: "Header name",
  queryParam: "Parameter name",
  tokenUrl: "Token URL",
  clientId: "Client ID",
  scope: "Scope",
  username: "User name",
};

const AUTH_FIELD_MISSING: Record<AuthField, string> = {
  headerName: "Enter the header name.",
  queryParam: "Enter the parameter name.",
  tokenUrl: "Enter the token URL.",
  clientId: "Enter the client ID.",
  scope: "",
  username: "Enter the user name.",
};

const SECRET_MISSING: Partial<Record<ApiAuthType, string>> = {
  API_KEY_HEADER: "Enter the API key.",
  API_KEY_QUERY: "Enter the API key.",
  BEARER: "Enter the token.",
  BASIC: "Enter the password.",
  OAUTH2_CLIENT_CREDENTIALS: "Enter the client secret.",
};

export interface AuthConfigDraft {
  headerName: string;
  queryParam: string;
  tokenUrl: string;
  scope: string;
  username: string;
  clientId: string;
}

/** A header (or query parameter) row; `stored`: a saved secret header, known by name only. */
export interface HeaderRow {
  id: string;
  name: string;
  value: string;
  secret: boolean;
  stored: boolean;
}

export interface AppDraft {
  name: string;
  baseUrl: string;
  description: string;
  docsUrl: string;
  authKind: ApiAuthType;
  authConfig: AuthConfigDraft;
  /** `null`: keep the stored secret. */
  secret: string | null;
  headers: HeaderRow[];
  pollIntervalMinutes: number;
  enabled: boolean;
  aiAnalysis: boolean;
}

export interface EndpointDraft {
  name: string;
  method: EndpointMethod;
  path: string;
  query: HeaderRow[];
  /** JSON text of a POST body. */
  body: string;
  itemsPath: string;
  itemKeyPath: string;
  description: string;
  enabled: boolean;
}

/** Field errors keyed by field name, `header.<rowId>` and `query.<rowId>`. */
export type FieldErrors = Record<string, string>;

export function newRowId(): string {
  return crypto.randomUUID();
}

export function apiHost(baseUrl: string): string {
  try {
    return new URL(baseUrl).host;
  } catch {
    return baseUrl;
  }
}

/** The tint of an app's initial: one of the ten, by id. */
export function appTint(apiId: number): Tint {
  return ((apiId % 10) + 1) as Tint;
}

export function intervalLabel(minutes: number): string {
  const preset = INTERVAL_PRESETS.find((entry) => entry.minutes === minutes);
  if (preset) return preset.label;
  return minutes % 60 === 0 ? `Every ${minutes / 60} hours` : `Every ${minutes} minutes`;
}

/** The status dot of an app; only states that need attention get one (design language: color only for status). */
export function statusMark(status: ApiState): { label: string; dot: string } | null {
  switch (status) {
    case "FAILING":
      return { label: "Failing", dot: "bg-destructive" };
    case "NEEDS_SETUP":
      return { label: "Needs setup", dot: "bg-warning" };
    case "PAUSED":
      return { label: "Paused", dot: "bg-muted-foreground" };
    default:
      return null;
  }
}

export function emptyDraft(): AppDraft {
  return {
    name: "",
    baseUrl: "",
    description: "",
    docsUrl: "",
    authKind: "NONE",
    authConfig: { headerName: "", queryParam: "", tokenUrl: "", scope: "", username: "", clientId: "" },
    secret: "",
    headers: [],
    pollIntervalMinutes: DEFAULT_INTERVAL,
    enabled: true,
    aiAnalysis: true,
  };
}

export function draftOf(api: ExternalApiDto): AppDraft {
  const config = api.authConfig;
  return {
    name: api.name,
    baseUrl: api.baseUrl,
    description: api.description ?? "",
    docsUrl: api.docsUrl ?? "",
    authKind: api.authKind,
    authConfig: {
      headerName: config.headerName ?? "",
      queryParam: config.queryParam ?? "",
      tokenUrl: config.tokenUrl ?? "",
      scope: config.scope ?? "",
      username: config.username ?? "",
      clientId: config.clientId ?? "",
    },
    secret: api.hasSecret ? null : "",
    headers: [
      ...api.headers.map((header) => ({ id: newRowId(), name: header.name, value: header.value, secret: false, stored: false })),
      ...api.secretHeaderNames.map((name) => ({ id: newRowId(), name, value: "", secret: true, stored: true })),
    ],
    pollIntervalMinutes: api.pollIntervalMinutes,
    enabled: api.enabled,
    aiAnalysis: api.aiAnalysis,
  };
}

/** Problem with an `https://` address, or none. */
function httpsProblem(value: string, label: string): string | undefined {
  if (!/^https:\/\//i.test(value)) return `The ${label} must start with https://.`;
  try {
    if (!new URL(value).hostname) return "This is not a valid URL.";
  } catch {
    return "This is not a valid URL.";
  }
  return undefined;
}

export function validateBasics(draft: AppDraft): FieldErrors {
  const errors: FieldErrors = {};
  const name = draft.name.trim();
  if (!name) errors.name = "Enter a name.";
  else if (name.length > MAX_NAME) errors.name = `At most ${MAX_NAME} characters.`;

  const baseUrl = draft.baseUrl.trim();
  const baseProblem = baseUrl ? httpsProblem(baseUrl, "base URL") : "Enter the base URL.";
  if (baseProblem) errors.baseUrl = baseProblem;
  else if (baseUrl.includes("?")) errors.baseUrl = "The base URL has no query; add query parameters to an endpoint.";

  const docsUrl = draft.docsUrl.trim();
  const docsProblem = docsUrl ? httpsProblem(docsUrl, "documentation link") : undefined;
  if (docsProblem) errors.docsUrl = docsProblem;
  return errors;
}

function storedHeaderNames(rows: readonly HeaderRow[]): string[] {
  return rows.filter((row) => row.stored).map((row) => row.name);
}

/** The secret headers are sent (they replace all stored ones) once one was added, removed or given a new value. */
function secretHeadersChanged(rows: readonly HeaderRow[], storedNames: readonly string[] = storedHeaderNames(rows)): boolean {
  const secret = rows.filter((row) => row.secret);
  if (secret.some((row) => !row.stored || row.value !== "")) return true;
  return secret.length !== storedNames.length;
}

export function validateAuth(draft: AppDraft, storedSecretHeaders?: readonly string[]): FieldErrors {
  const errors: FieldErrors = {};
  const kind = AUTH_KINDS[draft.authKind];
  for (const field of kind.fields) {
    const value = draft.authConfig[field].trim();
    if (!value && AUTH_FIELD_MISSING[field]) errors[field] = AUTH_FIELD_MISSING[field];
  }
  const tokenUrl = draft.authConfig.tokenUrl.trim();
  if (draft.authKind === "OAUTH2_CLIENT_CREDENTIALS" && tokenUrl) {
    const problem = httpsProblem(tokenUrl, "token URL");
    if (problem) errors.tokenUrl = problem;
  }
  if (kind.secretLabel && draft.secret !== null && !draft.secret.trim()) errors.secret = SECRET_MISSING[draft.authKind] ?? "";

  const seen = new Map<string, string>();
  const resend = secretHeadersChanged(draft.headers, storedSecretHeaders);
  for (const row of draft.headers) {
    const key = `header.${row.id}`;
    const name = row.name.trim();
    if (!name) {
      errors[key] = "Enter the header name.";
      continue;
    }
    const first = seen.get(name.toLowerCase());
    if (first) {
      errors[key] = `${first} is given twice.`;
      continue;
    }
    seen.set(name.toLowerCase(), name);
    if (row.secret && !row.value) {
      if (!row.stored) errors[key] = "Enter the value.";
      else if (resend) errors[key] = "Enter the value again: secret headers are saved together.";
    }
  }
  return errors;
}

function blankToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

function authConfigOf(draft: AppDraft): ApiAuthConfigDto {
  const config = draft.authConfig;
  return {
    headerName: blankToNull(config.headerName),
    queryParam: blankToNull(config.queryParam),
    tokenUrl: blankToNull(config.tokenUrl),
    scope: blankToNull(config.scope),
    username: blankToNull(config.username),
    clientId: blankToNull(config.clientId),
  };
}

function headerDtos(rows: readonly HeaderRow[], secret: boolean): ApiHeaderDto[] {
  return rows.filter((row) => row.secret === secret).map((row) => ({ name: row.name.trim(), value: row.value }));
}

function usesSecret(kind: ApiAuthType): boolean {
  return AUTH_KINDS[kind].secretLabel !== undefined;
}

export function toNewCommand(draft: AppDraft): NewExternalApiCommand {
  const description = draft.description.trim();
  const docsUrl = draft.docsUrl.trim();
  const secret = draft.secret?.trim();
  return {
    name: draft.name.trim(),
    baseUrl: draft.baseUrl.trim(),
    ...(description ? { description } : {}),
    ...(docsUrl ? { docsUrl } : {}),
    authKind: draft.authKind,
    authConfig: authConfigOf(draft),
    ...(usesSecret(draft.authKind) && secret ? { secret } : {}),
    headers: headerDtos(draft.headers, false),
    secretHeaders: headerDtos(draft.headers, true),
    pollIntervalMinutes: draft.pollIntervalMinutes,
    enabled: draft.enabled,
    aiAnalysis: draft.aiAnalysis,
  };
}

function sameHeaders(a: readonly ApiHeaderDto[], b: readonly ApiHeaderDto[]): boolean {
  return a.length === b.length && a.every((header, index) => header.name === b[index].name && header.value === b[index].value);
}

/** Only what differs from the saved app; `""` removes a cleared text. */
export function toUpdateCommand(draft: AppDraft, saved: ExternalApiDto): UpdateExternalApiCommand {
  const command: UpdateExternalApiCommand = {};
  const name = draft.name.trim();
  if (name !== saved.name) command.name = name;
  const description = draft.description.trim();
  if (description !== (saved.description ?? "")) command.description = description;
  const baseUrl = draft.baseUrl.trim();
  if (baseUrl !== saved.baseUrl) command.baseUrl = baseUrl;
  const docsUrl = draft.docsUrl.trim();
  if (docsUrl !== (saved.docsUrl ?? "")) command.docsUrl = docsUrl;

  const authConfig = authConfigOf(draft);
  const savedConfig = authConfigOf(draftOf({ ...saved, headers: [], secretHeaderNames: [] }));
  const configChanged = (Object.keys(authConfig) as (keyof ApiAuthConfigDto)[]).some((key) => authConfig[key] !== savedConfig[key]);
  if (draft.authKind !== saved.authKind || configChanged) {
    command.authKind = draft.authKind;
    command.authConfig = authConfig;
  }
  if (!usesSecret(draft.authKind)) {
    if (saved.hasSecret) command.secret = "";
  } else if (draft.secret !== null && draft.secret.trim()) {
    command.secret = draft.secret.trim();
  }

  const headers = headerDtos(draft.headers, false);
  if (!sameHeaders(headers, saved.headers)) command.headers = headers;
  if (secretHeadersChanged(draft.headers, saved.secretHeaderNames)) command.secretHeaders = headerDtos(draft.headers, true);

  if (draft.pollIntervalMinutes !== saved.pollIntervalMinutes) command.pollIntervalMinutes = draft.pollIntervalMinutes;
  if (draft.enabled !== saved.enabled) command.enabled = draft.enabled;
  if (draft.aiAnalysis !== saved.aiAnalysis) command.aiAnalysis = draft.aiAnalysis;
  return command;
}

function queryOf(rows: readonly HeaderRow[]): Record<string, string> {
  return Object.fromEntries(rows.map((row) => [row.name.trim(), row.value]));
}

/** The POST body as an object, or undefined for GET. */
function bodyOf(endpoint: EndpointDraft): Record<string, unknown> | undefined {
  if (endpoint.method !== "POST") return undefined;
  return parseObject(endpoint.body) ?? {};
}

function parseObject(text: string): Record<string, unknown> | null {
  if (!text.trim()) return {};
  try {
    const value: unknown = JSON.parse(text);
    return value !== null && typeof value === "object" && !Array.isArray(value) ? (value as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}

/**
 * One call with the unsaved settings. With `apiId` (editing), the server fills in a stored secret that was kept and
 * the stored secret headers while they are unchanged.
 */
export function toTestCommand(draft: AppDraft, endpoint: EndpointDraft, apiId?: number): TestExternalApiCommand {
  const secret = draft.secret?.trim();
  const body = bodyOf(endpoint);
  return {
    ...(apiId !== undefined ? { apiId } : {}),
    baseUrl: draft.baseUrl.trim(),
    authKind: draft.authKind,
    authConfig: authConfigOf(draft),
    ...(usesSecret(draft.authKind) && secret ? { secret } : {}),
    headers: headerDtos(draft.headers, false),
    ...(apiId === undefined || secretHeadersChanged(draft.headers) ? { secretHeaders: headerDtos(draft.headers, true) } : {}),
    method: endpoint.method,
    path: endpoint.path.trim(),
    query: queryOf(endpoint.query),
    ...(body ? { body } : {}),
  };
}

export function emptyEndpoint(): EndpointDraft {
  return { name: "", method: "GET", path: "", query: [], body: "{}", itemsPath: "", itemKeyPath: "", description: "", enabled: true };
}

export function endpointDraftOf(endpoint: ExternalApiEndpointDto): EndpointDraft {
  return {
    name: endpoint.name,
    method: endpoint.method,
    path: endpoint.path,
    query: Object.entries(endpoint.query).map(([name, value]) => ({ id: newRowId(), name, value, secret: false, stored: false })),
    body: JSON.stringify(endpoint.body ?? {}, null, 2),
    itemsPath: endpoint.itemsPath ?? "",
    itemKeyPath: endpoint.itemKeyPath ?? "",
    description: endpoint.description ?? "",
    enabled: endpoint.enabled,
  };
}

export function validateEndpoint(endpoint: EndpointDraft): FieldErrors {
  const errors: FieldErrors = {};
  const name = endpoint.name.trim();
  if (!name) errors.name = "Enter a name.";
  else if (name.length > MAX_NAME) errors.name = `At most ${MAX_NAME} characters.`;
  const path = endpoint.path.trim();
  if (!path) errors.path = "Enter the path.";
  else if (!path.startsWith("/")) errors.path = "The path starts with /.";
  else if (path.includes("?")) errors.path = "Add query parameters below, not in the path.";
  for (const row of endpoint.query) {
    if (!row.name.trim()) errors[`query.${row.id}`] = "Enter the parameter name.";
  }
  if (endpoint.method === "POST" && parseObject(endpoint.body) === null) errors.body = "The body is a JSON object, e.g. {}.";
  return errors;
}

export function toNewEndpointCommand(endpoint: EndpointDraft): NewExternalApiEndpointCommand {
  const itemsPath = endpoint.itemsPath.trim();
  const itemKeyPath = endpoint.itemKeyPath.trim();
  const description = endpoint.description.trim();
  const body = bodyOf(endpoint);
  return {
    name: endpoint.name.trim(),
    method: endpoint.method,
    path: endpoint.path.trim(),
    query: queryOf(endpoint.query),
    ...(body ? { body } : {}),
    ...(itemsPath ? { itemsPath } : {}),
    ...(itemKeyPath ? { itemKeyPath } : {}),
    ...(description ? { description } : {}),
    enabled: endpoint.enabled,
  };
}

/** Every value of the form; `""` removes an optional text. */
export function toUpdateEndpointCommand(endpoint: EndpointDraft): UpdateExternalApiEndpointCommand {
  const body = bodyOf(endpoint);
  return {
    name: endpoint.name.trim(),
    method: endpoint.method,
    path: endpoint.path.trim(),
    query: queryOf(endpoint.query),
    ...(body ? { body } : {}),
    itemsPath: endpoint.itemsPath.trim(),
    itemKeyPath: endpoint.itemKeyPath.trim(),
    description: endpoint.description.trim(),
    enabled: endpoint.enabled,
  };
}

/** Keys that work in a JSON path of the API (`.name`). */
const PLAIN_KEY = /^[A-Za-z_][A-Za-z0-9_-]*$/;
/** Keys that usually identify an item, best first. */
const ID_KEYS = ["id", "uuid", "key", "sku", "code", "slug"];

export interface DetectedItems {
  itemsPath: string;
  itemKeyPath?: string;
  count: number;
}

function isObjectList(value: unknown): value is Record<string, unknown>[] {
  return Array.isArray(value) && value.length > 0 && value.every((entry) => entry !== null && typeof entry === "object" && !Array.isArray(entry));
}

function findList(value: unknown, path: string, depth: number): { path: string; items: Record<string, unknown>[] } | null {
  if (isObjectList(value)) return { path: `${path}[*]`, items: value };
  if (depth === 0 || value === null || typeof value !== "object" || Array.isArray(value)) return null;
  // Breadth first: a list on this level wins over one deeper down.
  const entries = Object.entries(value).filter(([key]) => PLAIN_KEY.test(key));
  for (const [key, child] of entries) {
    if (isObjectList(child)) return { path: `${path}.${key}[*]`, items: child };
  }
  for (const [key, child] of entries) {
    const found = findList(child, `${path}.${key}`, depth - 1);
    if (found) return found;
  }
  return null;
}

/** Where the items are in a JSON answer (the first list of objects, at most three levels deep) and their key. */
export function detectItems(body: string | undefined): DetectedItems | null {
  if (!body) return null;
  let value: unknown;
  try {
    value = JSON.parse(body);
  } catch {
    return null;
  }
  const found = findList(value, "$", 3);
  if (!found) return null;
  const key = ID_KEYS.find((candidate) => candidate in found.items[0]);
  return { itemsPath: found.path, ...(key ? { itemKeyPath: `$.${key}` } : {}), count: found.items.length };
}

/** JSON indented for reading; anything else (or a cut-off answer) as it is. */
export function prettyBody(body: string): string {
  try {
    return JSON.stringify(JSON.parse(body), null, 2);
  } catch {
    return body;
  }
}
