#!/usr/bin/env bash
# Starts contor-api for end-to-end tests: fresh Testcontainers Postgres (no reuse, so first-run setup is pending),
# random encryption key, dedicated port. Output goes to e2e/.tmp/backend.log, where the tests read the setup token.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
log_dir="$here/../.tmp"
mkdir -p "$log_dir"
: > "$log_dir/backend.log"
export APP_PORT="${E2E_API_PORT:-18080}"
export APP_ENCRYPTION_KEY="$(openssl rand -base64 32)"
cd "$here/../../../contor-api"
exec ./mvnw -q -B spring-boot:test-run \
  -Dspring-boot.run.jvmArguments="-Dcontor.testcontainers.reuse=false" >> "$log_dir/backend.log" 2>&1
