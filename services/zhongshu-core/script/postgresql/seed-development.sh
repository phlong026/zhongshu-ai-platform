#!/usr/bin/env bash
set -euo pipefail
# Explicit local disposable fixture. Never called by application startup or Flyway.
[[ "${ZS_DEV_SEED_CONFIRM:-}" == 'disposable-local-test-only' ]] || {
  echo 'Set ZS_DEV_SEED_CONFIRM=disposable-local-test-only to initialize the local fixture.' >&2; exit 1;
}
[[ "${PGHOST:-}" == '127.0.0.1' || "${PGHOST:-}" == 'localhost' ]] || {
  echo 'Development seed requires loopback PostgreSQL.' >&2; exit 1;
}
[[ "${PGDATABASE:-}" =~ ^[a-zA-Z_][a-zA-Z0-9_]*_test$ ]] || { echo 'Use a simple PGDATABASE ending in _test.' >&2; exit 1; }
[[ "${PGPORT:-}" =~ ^[0-9]{1,5}$ ]] && (( 10#$PGPORT >= 1 && 10#$PGPORT <= 65535 )) || {
  echo 'PGPORT must be an explicit numeric TCP port.' >&2; exit 1;
}
unset PGHOSTADDR PGSERVICE PGSERVICEFILE PGPASSFILE PGOPTIONS
[[ "${ZS_DEV_ADMIN_PASSWORD_HASH:-}" =~ ^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$ ]] || {
  echo 'Provide a generated BCrypt hash via ZS_DEV_ADMIN_PASSWORD_HASH; no fixed password is supplied.' >&2; exit 1;
}
[[ -n "${ZS_DEV_OAUTH_CLIENT_SECRET:-}" ]] || { echo 'ZS_DEV_OAUTH_CLIENT_SECRET is required.' >&2; exit 1; }
psql -X -h "$PGHOST" -p "$PGPORT" -d "$PGDATABASE" --set=ON_ERROR_STOP=1 --file="$(dirname "$0")/seed-development.sql"
