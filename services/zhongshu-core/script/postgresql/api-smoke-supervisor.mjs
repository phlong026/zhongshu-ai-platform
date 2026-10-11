import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { closeSync, lstatSync, openSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

export function collectRunLogs(applicationLog, rollingLog) {
  assert.ok(applicationLog && rollingLog, 'Both log sinks must be configured');
  const directory = path.dirname(rollingLog);
  assert.equal(path.dirname(applicationLog), directory, 'Log sinks must share the isolated run directory');
  const prefix = path.basename(rollingLog) + '.';
  const files = [applicationLog, rollingLog, ...readdirSync(directory)
    .filter(name => name.startsWith(prefix)).sort().map(name => path.join(directory, name))];
  // Missing sinks, symlinks and incomplete/non-file fragments fail closed.
  for (const file of files) assert.ok(lstatSync(file).isFile(), 'A required log is not a regular file');
  return files;
}

function waitForClose(closed, timeoutMs) {
  let timer;
  return Promise.race([closed, new Promise(resolve => {
    timer = setTimeout(() => resolve(null), timeoutMs);
  })]).finally(() => clearTimeout(timer));
}

// Dependency injection supports deterministic local process tests without running Java or services.
export async function superviseApplication({ command, args = [], applicationLog, runAssertions, verifyLogs,
  env = process.env, shutdownTimeoutMs = 60000, killTimeoutMs = 5000 }) {
  const fd = openSync(applicationLog, 'wx');
  let child;
  try {
    child = spawn(command, args, { env, stdio: ['ignore', fd, fd] });
  } finally {
    closeSync(fd);
  }
  const controller = new AbortController();
  let outcome;
  let stopping = false;
  let interrupted = false;
  let unexpectedExit = false;
  let spawnFailed = false;
  const closed = new Promise(resolve => {
    child.once('error', () => { spawnFailed = true; });
    child.once('close', (code, signal) => {
      unexpectedExit = !stopping;
      outcome = { code, signal };
      controller.abort();
      resolve(outcome);
    });
  });
  const interrupt = () => {
    interrupted = true;
    controller.abort();
  };
  process.once('SIGINT', interrupt);
  process.once('SIGTERM', interrupt);
  const failures = new Set();
  try {
    try {
      await Promise.race([runAssertions(controller.signal), closed.then(() => {
        throw new Error('Application exited before API assertions finished');
      })]);
    } catch {
      failures.add('API assertions');
    }
    stopping = true;
    if (!outcome) {
      child.kill('SIGTERM');
      if (!await waitForClose(closed, shutdownTimeoutMs)) {
        failures.add('graceful shutdown timeout');
        child.kill('SIGKILL');
        await waitForClose(closed, killTimeoutMs);
      }
    }
    if (spawnFailed || unexpectedExit) failures.add('unexpected application exit');
    if (interrupted) failures.add('supervisor interrupted');
    if (!outcome) {
      failures.add('application close not observed');
    } else {
      if (!(outcome.signal === 'SIGTERM' || (outcome.signal === null && [0, 143].includes(outcome.code)))) {
        failures.add('abnormal application shutdown');
      }
      // Even on API failure, inspect all logs, but only after the child close event.
      try { await verifyLogs(); } catch { failures.add('post-shutdown credential-log verification'); }
    }
  } finally {
    controller.abort();
    process.removeListener('SIGINT', interrupt);
    process.removeListener('SIGTERM', interrupt);
  }
  // Never emit caught errors: assertion values and fetch URLs may contain credentials.
  if (failures.size) throw new Error('Smoke failed: ' + [...failures].join(', ') + '; sensitive details omitted');
}

async function main() {
  const jar = process.argv[2];
  assert.ok(jar, 'A built server jar is required');
  const { runApiSmoke, verifyCapturedSecretsInLogs } = await import('./test-api-smoke.mjs');
  const applicationLog = process.env.ZS_API_SMOKE_APPLICATION_LOG;
  const rollingLog = process.env.ZS_LOG_FILE;
  await superviseApplication({ command: 'java', args: ['-Xmx768m', '-jar', jar, '--server.address=127.0.0.1'],
    applicationLog, runAssertions: signal => runApiSmoke({ signal }),
    verifyLogs: () => verifyCapturedSecretsInLogs(collectRunLogs(applicationLog, rollingLog)) });
  console.log('PASS Java stopped gracefully; stdout, rolling log and all current-run fragments exclude every captured credential.');
  console.log('PASS PostgreSQL API smoke complete. Browser UI was not exercised.');
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try { await main(); } catch {
    console.error('FAIL supervised API smoke; sensitive failure details are intentionally omitted.');
    process.exitCode = 1;
  }
}
