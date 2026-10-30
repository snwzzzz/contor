import type { ExternalApiDto } from "@/lib/api/generated/model/externalApiDto";

/** A saved app (external API) as the API returns it. */
export function appDto(id: number, name: string, overrides: Partial<ExternalApiDto> = {}): ExternalApiDto {
  return {
    id,
    name,
    slug: `app_${id}`,
    baseUrl: `https://api.${name.toLowerCase()}.example/v1`,
    authKind: "NONE",
    authConfig: {},
    hasSecret: false,
    headers: [],
    secretHeaderNames: [],
    pollIntervalMinutes: 360,
    enabled: true,
    aiAnalysis: true,
    status: "ACTIVE",
    failureCount: 0,
    dailyRequestLimit: 200,
    endpointCount: 2,
    createdAt: "2026-10-28T08:00:00Z",
    ...overrides,
  };
}
