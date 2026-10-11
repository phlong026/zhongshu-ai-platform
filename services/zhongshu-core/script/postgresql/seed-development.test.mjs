import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, chmodSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { checkDevelopmentEnvironment } from './check-development-environment.mjs';
const seed = fileURLToPath(new URL('./seed-development.sh', import.meta.url));
const base = { ...process.env, ZS_DEV_SEED_CONFIRM: 'disposable-local-test-only', PGHOST: '127.0.0.1',
  PGPORT: '15432', PGDATABASE: 'zhongshu_test', ZS_DEV_ADMIN_PASSWORD_HASH: '$2b$12$' + 'A'.repeat(53), ZS_DEV_OAUTH_CLIENT_SECRET: 'fixture-only' };
function run(overrides) { return spawnSync('bash', [seed], { env: { ...base, ...overrides }, encoding: 'utf8' }); }
test('development seed rejects unconfirmed, non-loopback and indirect database inputs', () => {
  for (const overrides of [{ ZS_DEV_SEED_CONFIRM: '' }, { PGHOST: 'example.com' }, { PGDATABASE: 'production' },
    { PGDATABASE: 'host=example.com dbname=other_test' }, { PGDATABASE: 'postgresql://example.com/other_test' },
    { PGPORT: '' }, { PGPORT: '15432 host=example.com' }, { PGPORT: '65536' }, { ZS_DEV_ADMIN_PASSWORD_HASH: '$2b$not-a-hash' }
  ]) assert.notEqual(run(overrides).status, 0, JSON.stringify(Object.keys(overrides)));
});
test('libpq overrides are removed and explicit TCP arguments win', () => {
  const folder = mkdtempSync(join(tmpdir(), 'pg-seed-guard-'));
  try {
    const stub = join(folder, 'psql');
    writeFileSync(stub, '#!/usr/bin/env bash\nset -eu\n' +
      '[[ -z "${PGHOSTADDR:-}${PGSERVICE:-}${PGSERVICEFILE:-}${PGOPTIONS:-}" ]]\n' +
      '[[ "$1" == -X && "$2" == -h && "$3" == 127.0.0.1 && "$4" == -p && "$5" == 15432 && "$6" == -d && "$7" == zhongshu_test ]]\n');
    chmodSync(stub, 0o700);
    const result = run({ PATH: folder + ':' + process.env.PATH, PGHOSTADDR: '203.0.113.1', PGSERVICE: 'external',
      PGSERVICEFILE: '/untrusted/service', PGOPTIONS: '-c search_path=other' });
    assert.equal(result.status, 0, result.stderr);
  } finally { rmSync(folder, { recursive: true, force: true }); }
});
test('API smoke checks one matching PostgreSQL target and confirmed dedicated local Redis before writes', () => {
  const env = { ...base, ZS_PG_URL: 'jdbc:postgresql://127.0.0.1:15432/zhongshu_test', ZS_PG_USERNAME: 'runtime',
    ZS_PG_PASSWORD: 'fixture-only', ZS_PG_MIGRATION_USERNAME: 'migrator', ZS_PG_MIGRATION_PASSWORD: 'fixture-only',
    ZS_REDIS_HOST: '127.0.0.1', ZS_REDIS_PORT: '26379', ZS_REDIS_PASSWORD: 'fixture-only',
    ZS_DEV_REDIS_CONFIRM: 'dedicated-disposable-test-instance', PGHOSTADDR: '', PGSERVICE: '', PGSERVICEFILE: '', PGOPTIONS: '' };
  assert.doesNotThrow(() => checkDevelopmentEnvironment(env));
  for (const overrides of [{ PGDATABASE: 'different_test' }, { PGPORT: '5432' }, { PGHOST: 'localhost' },
    { ZS_PG_URL: env.ZS_PG_URL + '?host=remote' }, { ZS_PG_URL: env.ZS_PG_URL + '#fragment' },
    { ZS_PG_URL: 'jdbc:postgresql://name@127.0.0.1:15432/zhongshu_test' }, { PGHOSTADDR: '203.0.113.1' },
    { PGSERVICE: 'external' }, { ZS_REDIS_HOST: 'remote' }, { ZS_DEV_REDIS_CONFIRM: '' }, { ZS_REDIS_PORT: '99999' }
  ]) assert.throws(() => checkDevelopmentEnvironment({ ...env, ...overrides }));
});
