# contor-web for the personal production instance (built by scripts/prod.sh from a clean export of main).
# Build context: the repository root (the API client is generated from api-spec/openapi.yaml during the build).

FROM node:24-slim AS build
WORKDIR /src
COPY api-spec api-spec
COPY contor-web/package.json contor-web/package-lock.json contor-web/
RUN --mount=type=cache,target=/root/.npm cd contor-web && npm ci --no-audit --no-fund
COPY contor-web contor-web
# The /api rewrite is fixed at build time. The web server shares the backend's network namespace (compose.yml), so the
# backend is on loopback: the proxy is trusted for X-Forwarded-For and the backend never listens on a network.
ENV CONTOR_API_URL=http://127.0.0.1:8080 NEXT_TELEMETRY_DISABLED=1
RUN cd contor-web && npm run build && npm prune --omit=dev --no-audit --no-fund

FROM node:24-slim
ENV NODE_ENV=production CONTOR_API_URL=http://127.0.0.1:8080 NEXT_TELEMETRY_DISABLED=1
WORKDIR /app
COPY --from=build --chown=node:node /src/contor-web/package.json /src/contor-web/next.config.ts ./
COPY --from=build --chown=node:node /src/contor-web/node_modules node_modules
COPY --from=build --chown=node:node /src/contor-web/.next .next
USER node
CMD ["node_modules/.bin/next", "start", "-H", "0.0.0.0", "-p", "3000"]
