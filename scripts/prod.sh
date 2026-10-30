#!/usr/bin/env bash
# The personal production instance in Docker, in one command (see README, "Production instance (Docker)").
#
#   scripts/prod.sh up       build what is committed on main and start (or update) Postgres, backend and web together
#   scripts/prod.sh down     stop it (the data stays in the contor-prod-pgdata volume)
#   scripts/prod.sh status   what runs and whether it is healthy
#   scripts/prod.sh logs [service]   follow the logs (postgres, backend, web)
#   scripts/prod.sh token    the one-time setup token, until the owner account exists
#   scripts/prod.sh backup   dump the database to backups/contor-<time>.dump
#
# Builds always come from a clean export of main (CONTOR_REF=<branch or commit> to try something else), never from the
# working tree, so uncommitted work and the dev setup are never part of it. Runs in Git Bash on Windows as well.
set -euo pipefail
# Git Bash would rewrite arguments such as "main:.env.prod.example" as Windows paths.
export MSYS_NO_PATHCONV=1

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
REF="${CONTOR_REF:-main}"
BUILD_DIR=".prod-build"
ENV_FILE=".env.prod"

compose() {
  docker compose -p contor-prod --env-file "$ENV_FILE" -f "$BUILD_DIR/infra/prod/compose.yml" "$@"
}

need_build_dir() {
  [[ -f "$BUILD_DIR/infra/prod/compose.yml" ]] || { echo "Not set up yet: run scripts/prod.sh up first." >&2; exit 1; }
}

# First run: .env.prod from the example, with a database password and an encryption key of its own.
init_env() {
  [[ -f "$ENV_FILE" ]] && return
  local example
  example="$(git show "$REF:.env.prod.example")"
  printf '%s\n' "$example" > "$ENV_FILE"
  local password key
  password="$(openssl rand -hex 24)"
  key="$(openssl rand -base64 32)"
  sed -i "s#^DB_PASSWORD=.*#DB_PASSWORD=$password#; s#^APP_ENCRYPTION_KEY=.*#APP_ENCRYPTION_KEY=$key#" "$ENV_FILE"
  echo "Created $ENV_FILE with a new database password and encryption key. Keep a copy of it somewhere safe."
}

# The committed tree of $REF, nothing else (no node_modules, builds or secrets).
export_ref() {
  git rev-parse --verify --quiet "$REF^{commit}" > /dev/null || { echo "Unknown branch or commit: $REF" >&2; exit 1; }
  rm -rf "$BUILD_DIR"
  mkdir -p "$BUILD_DIR"
  git archive "$REF" | tar -x -C "$BUILD_DIR"
  echo "Building $REF ($(git rev-parse --short "$REF"))."
}

print_token() {
  local token
  token="$(compose logs --no-log-prefix backend 2>/dev/null | grep -A4 "contor first-run setup" | tail -1 | tr -d '[:space:]' || true)"
  if [[ -n "$token" ]]; then
    echo "First run: create the owner account with this setup token: $token"
  fi
}

case "${1:-up}" in
  up)
    init_env
    export_ref
    compose up -d --build --wait
    port="$(grep -E '^WEB_PORT=' "$ENV_FILE" | cut -d= -f2)"
    echo "contor runs at http://127.0.0.1:${port:-3001}"
    print_token
    ;;
  down)
    need_build_dir
    compose down
    ;;
  status)
    need_build_dir
    compose ps
    ;;
  logs)
    need_build_dir
    compose logs -f --tail 200 "${@:2}"
    ;;
  token)
    need_build_dir
    print_token
    ;;
  backup)
    need_build_dir
    mkdir -p backups
    file="backups/contor-$(date -u +%Y%m%d-%H%M%S).dump"
    compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$file"
    echo "Saved $file ($(du -h "$file" | cut -f1))."
    ;;
  *)
    sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
    ;;
esac
