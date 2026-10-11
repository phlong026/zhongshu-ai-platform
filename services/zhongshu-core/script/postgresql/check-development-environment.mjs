import assert from 'node:assert/strict';
import { pathToFileURL } from 'node:url';
export function checkDevelopmentEnvironment(env) {
  assert.equal(env.ZS_DEV_SEED_CONFIRM, 'disposable-local-test-only', 'Confirm the disposable test database');
  const match = /^jdbc:postgresql:\/\/(127\.0\.0\.1|localhost):([1-9][0-9]{0,4})\/([A-Za-z_][A-Za-z0-9_]*_test)$/.exec(env.ZS_PG_URL ?? '');
  assert.ok(match && Number(match[2]) <= 65535, 'Use a plain loopback JDBC URL with explicit port and simple *_test database, no query, fragment or userinfo');
  assert.equal(env.PGHOST, match[1], 'libpq PGHOST must match the JDBC host');
  assert.equal(env.PGPORT, match[2], 'libpq PGPORT must match the JDBC port');
  assert.equal(env.PGDATABASE, match[3], 'libpq PGDATABASE must match the JDBC database');
  for (const name of ['PGHOSTADDR', 'PGSERVICE', 'PGSERVICEFILE', 'PGOPTIONS']) assert.ok(!env[name], `Remove ${name} before smoke testing`);
  assert.ok(['localhost', '127.0.0.1'].includes(env.ZS_REDIS_HOST), 'Redis must use a loopback test instance');
  assert.match(env.ZS_REDIS_PORT ?? '', /^[1-9][0-9]{0,4}$/, 'Set an explicit Redis TCP port');
  assert.ok(Number(env.ZS_REDIS_PORT) <= 65535, 'Invalid Redis port');
  assert.equal(env.ZS_DEV_REDIS_CONFIRM, 'dedicated-disposable-test-instance', 'A database number alone does not establish a dedicated test Redis');
  for (const name of ['ZS_PG_USERNAME', 'ZS_PG_PASSWORD', 'ZS_PG_MIGRATION_USERNAME', 'ZS_PG_MIGRATION_PASSWORD', 'ZS_REDIS_PASSWORD']) assert.ok(env[name], `${name} is required`);
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  checkDevelopmentEnvironment(process.env);
  console.log('Development target checks passed before any database or Redis changes.');
}
