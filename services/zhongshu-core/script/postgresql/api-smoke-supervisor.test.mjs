import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { gzipSync } from 'node:zlib';
import test from 'node:test';
import { collectRunLogs, superviseApplication } from './api-smoke-supervisor.mjs';

// Ephemeral fake credentials exercise the closure without calling an API or printing values.
process.env.ZS_API_SMOKE_BASE_URL = 'http://127.0.0.1:48080';
const credential = randomBytes(12).toString('base64url');
process.env.ZS_DEV_ADMIN_PASSWORD = credential;
const { verifyCapturedSecretsInLogs } = await import('./test-api-smoke.mjs');

function fixture(t) {
  const directory = mkdtempSync(path.join(os.tmpdir(), 'zs-smoke-supervisor-'));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  return { directory, stdout: path.join(directory, 'application.log'),
    rolling: path.join(directory, 'rolling-application.log') };
}
function seedCleanLogs(files) {
  writeFileSync(files.stdout, 'clean stdout\n');
  writeFileSync(files.rolling, 'clean rolling\n');
}
async function ready(stdout, signal) {
  for (let i = 0; i < 150; i++) {
    if (readFileSync(stdout, 'utf8').includes('READY')) return;
    await delay(20, undefined, { signal });
  }
  throw new Error('Fake application did not become ready');
}
const fakeApplication = `
  const fs = require('node:fs');
  const zlib = require('node:zlib');
  const log = process.env.TEST_ROLLING;
  fs.writeFileSync(log, 'startup\\n');
  process.on('SIGTERM', () => {
    if (process.env.TEST_IGNORE_TERM === 'true') return;
    // Deliberately write only during shutdown; the verifier must run after close.
    setTimeout(() => {
      fs.appendFileSync(log, 'shutdown complete\\n');
      fs.writeFileSync(log + '.2026-10-11.0.log.gz', zlib.gzipSync(process.env.TEST_LAST_LINE || 'clean archive'));
      console.log('SHUTDOWN COMPLETE');
      process.exit(0);
    }, 40);
  });
  console.log('READY');
  setInterval(() => {}, 1000);
`;
function options(files, overrides = {}) {
  return { command: process.execPath, args: ['-e', fakeApplication], applicationLog: files.stdout,
    env: { ...process.env, TEST_ROLLING: files.rolling }, shutdownTimeoutMs: 2000, killTimeoutMs: 2000,
    runAssertions: signal => ready(files.stdout, signal),
    verifyLogs: () => verifyCapturedSecretsInLogs(collectRunLogs(files.stdout, files.rolling)), ...overrides };
}

test('collects current stdout, rolling and every plain/gzip fragment, excluding unrelated files', t => {
  const files = fixture(t); seedCleanLogs(files);
  writeFileSync(files.rolling + '.2026-10-11.0.log', 'clean plain archive');
  writeFileSync(files.rolling + '.2026-10-11.1.log.gz', gzipSync('clean gzip archive'));
  writeFileSync(path.join(files.directory, 'unrelated.log'), credential);
  const paths = collectRunLogs(files.stdout, files.rolling);
  assert.equal(paths.length, 4);
  verifyCapturedSecretsInLogs(paths);
});

test('fails without reporting captured credentials in stdout or gzip fragments', t => {
  const files = fixture(t); seedCleanLogs(files);
  for (const leakedPath of [files.stdout, files.rolling + '.2026-10-11.0.log.gz']) {
    writeFileSync(leakedPath, leakedPath.endsWith('.gz') ? gzipSync(credential) : credential);
    assert.throws(() => verifyCapturedSecretsInLogs(collectRunLogs(files.stdout, files.rolling)), error => {
      assert.ok(!error.message.includes(credential), 'Failure message must not disclose a credential');
      return /values are intentionally not reported/.test(error.message);
    });
    writeFileSync(leakedPath, leakedPath.endsWith('.gz') ? gzipSync('clean') : 'clean');
  }
});

test('missing sink, corrupt gzip and async flush timeout cannot pass', t => {
  const files = fixture(t);
  writeFileSync(files.stdout, 'clean');
  assert.throws(() => collectRunLogs(files.stdout, files.rolling));
  writeFileSync(files.rolling, 'clean');
  const archive = files.rolling + '.2026-10-11.0.log.gz';
  writeFileSync(archive, 'invalid gzip');
  assert.throws(() => verifyCapturedSecretsInLogs(collectRunLogs(files.stdout, files.rolling)), /could not be inspected/);
  rmSync(archive);
  writeFileSync(files.rolling, 'Max queue flush timeout (1000 ms) exceeded. Approximately 12 queued events were possibly discarded.');
  assert.throws(() => verifyCapturedSecretsInLogs(collectRunLogs(files.stdout, files.rolling)), /did not flush completely/);
});

test('supervisor scans only after graceful close, including shutdown-only gzip output', async t => {
  const files = fixture(t);
  let verified = false;
  await superviseApplication(options(files, { verifyLogs: () => {
    assert.ok(readFileSync(files.stdout, 'utf8').includes('SHUTDOWN COMPLETE'));
    assert.ok(readFileSync(files.rolling, 'utf8').includes('shutdown complete'));
    const paths = collectRunLogs(files.stdout, files.rolling);
    assert.equal(paths.length, 3);
    verifyCapturedSecretsInLogs(paths);
    verified = true;
  } }));
  assert.ok(verified);
});

test('a credential written only at shutdown is detected without exposing it', async t => {
  const files = fixture(t);
  await assert.rejects(superviseApplication(options(files, {
    env: { ...process.env, TEST_ROLLING: files.rolling, TEST_LAST_LINE: credential }
  })), error => {
    assert.ok(!error.message.includes(credential), 'Supervisor must not disclose a credential');
    return /post-shutdown credential-log verification/.test(error.message);
  });
});

test('graceful shutdown timeout fails even when SIGKILL completes and logs are clean', async t => {
  const files = fixture(t);
  let verified = false;
  await assert.rejects(superviseApplication(options(files, { shutdownTimeoutMs: 100,
    env: { ...process.env, TEST_ROLLING: files.rolling, TEST_IGNORE_TERM: 'true' },
    verifyLogs: () => { verified = true; verifyCapturedSecretsInLogs(collectRunLogs(files.stdout, files.rolling)); }
  })), /graceful shutdown timeout/);
  assert.ok(verified, 'Forced close still permits a final scan, but never a successful result');
});

test('failed API assertions still stop the application, flush and scan without dumping the error', async t => {
  const files = fixture(t);
  let verified = false;
  await assert.rejects(superviseApplication(options(files, {
    runAssertions: async signal => { await ready(files.stdout, signal); throw new Error(credential); },
    verifyLogs: () => {
      assert.ok(readFileSync(files.stdout, 'utf8').includes('SHUTDOWN COMPLETE'));
      verifyCapturedSecretsInLogs(collectRunLogs(files.stdout, files.rolling)); verified = true;
    }
  })), error => {
    assert.ok(!error.message.includes(credential), 'API failures must not dump sensitive values');
    return /API assertions/.test(error.message);
  });
  assert.ok(verified);
});

test('unexpected application exit fails and aborts pending assertions', async t => {
  const files = fixture(t);
  await assert.rejects(superviseApplication(options(files, {
    args: ['-e', "require('node:fs').writeFileSync(process.env.TEST_ROLLING, 'clean'); process.exit(1)"],
    runAssertions: signal => delay(30000, undefined, { signal })
  })), /unexpected application exit/);
});
