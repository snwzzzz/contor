#!/usr/bin/env bash
# The personal production instance in Docker, in one command (see README, "Production instance (Docker)").
#
#   scripts/prod.sh up       back up the database, build what is committed on main and start (or update) Postgres,
#                            backend and web together (the .githooks run it after commits and merges on main)
#   scripts/prod.sh down     stop it (the data stays in the contor-prod_pgdata volume)
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
LOCK_DIR=".prod-build.lock"
# Backups taken before an update; older ones are deleted (`backup` ones are kept).
KEEP_UPDATE_BACKUPS=10

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

# backups/contor-<time>[-<kind>].dump, written from the running database.
backup() {
  mkdir -p backups
  local file="backups/contor-$(date -u +%Y%m%d-%H%M%S)${1:+-$1}.dump"
  compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$file"
  echo "Saved $file ($(du -h "$file" | cut -f1))."
}

# Before an update: a backup of the running instance (none on the first start), keeping the newest few.
backup_before_update() {
  [[ -f "$BUILD_DIR/infra/prod/compose.yml" ]] || return 0
  [[ -n "$(compose ps --status running -q postgres 2>/dev/null)" ]] || return 0
  backup before-update
  ls -1t backups/contor-*-before-update.dump | tail -n +$((KEEP_UPDATE_BACKUPS + 1)) | xargs -r rm -f
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
    # One update at a time (a commit and a merge on main can follow each other quickly).
    mkdir "$LOCK_DIR" 2> /dev/null || { echo "Another update is running (remove $LOCK_DIR if it is not)." >&2; exit 1; }
    trap 'rmdir "$LOCK_DIR"' EXIT
    init_env
    backup_before_update
    export_ref
    compose build
    # Compose keeps running containers whose image tag was rebuilt, so the app containers are recreated explicitly;
    # the database (and the relay whose network they share) only when missing.
    compose up -d --wait postgres net
    compose up -d --wait --force-recreate --no-deps backend web
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
    backup
    ;;
  *)
    sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
    ;;
esac
