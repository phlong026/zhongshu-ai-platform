#!/usr/bin/env bash
set -euo pipefail
# Both services must already run in this command's network environment.
cd "$(dirname "$0")/../.."
node script/postgresql/check-development-environment.mjs
[[ "${ZS_DEV_SEED_CONFIRM:-}" == 'disposable-local-test-only' ]] || { echo 'Confirm the disposable fixture.' >&2; exit 1; }
jar="$PWD/yudao-server/target/yudao-server.jar"
[[ -f "$jar" ]] || { echo 'Build the server jar with JDK 17 first.' >&2; exit 1; }
mkdir -p yudao-server/target/postgres-api-smoke
logs="$PWD/yudao-server/target/postgres-api-smoke"
temporary=$(mktemp -d "$logs/libs.XXXXXX")
cleanup() {
  result=$?; trap - EXIT INT TERM
  rm -rf "$temporary"; exit "$result"
}
trap cleanup EXIT INT TERM
unzip -q "$jar" 'BOOT-INF/lib/*' 'BOOT-INF/classes/db/migration/*' -d "$temporary"
classpath="$temporary/BOOT-INF/lib/*:$temporary/BOOT-INF/classes"
export ZS_DEV_ADMIN_PASSWORD="$(node -e "process.stdout.write(require('crypto').randomBytes(12).toString('base64url'))")"
export ZS_DEV_OAUTH_CLIENT_SECRET="$(node -e "process.stdout.write(require('crypto').randomBytes(32).toString('hex'))")"
export ZS_DEV_ADMIN_PASSWORD_HASH="$(java -cp "$classpath" script/postgresql/PrepareDevelopmentDatabase.java hash)"
java -cp "$classpath" script/postgresql/PrepareDevelopmentDatabase.java > "$logs/migration.log" 2>&1
export PGUSER="$ZS_PG_MIGRATION_USERNAME" PGPASSWORD="$ZS_PG_MIGRATION_PASSWORD"
bash script/postgresql/seed-development.sh > "$logs/seed.log" 2>&1
unset PGUSER PGPASSWORD ZS_PG_MIGRATION_USERNAME ZS_PG_MIGRATION_PASSWORD
export SPRING_PROFILES_ACTIVE=postgres ZS_DB_MIGRATE=false ZS_CAPTCHA_ENABLED=false
export ZS_REDIS_DATABASE=15
export ZS_APPLICATION_MYBATIS_PLUS_ENCRYPTOR_PASSWORD="$(node -e "process.stdout.write(require('crypto').randomBytes(16).toString('hex'))")"
export ZS_SERVER_PORT="${ZS_SERVER_PORT:-48080}"
export ZS_API_SMOKE_BASE_URL="http://127.0.0.1:$ZS_SERVER_PORT"
# A unique directory contains only this run's stdout, rolling file and rollover fragments.
run_logs=$(mktemp -d "$logs/run.XXXXXX")
export ZS_LOG_FILE="$run_logs/rolling-application.log" ZS_API_SMOKE_APPLICATION_LOG="$run_logs/application.log"
printf 'INFO application logs: %s\n' "$run_logs"
node script/postgresql/api-smoke-supervisor.mjs "$jar" | tee "$logs/results.log"
