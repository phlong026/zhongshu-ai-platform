import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { gunzipSync } from 'node:zlib';
import { pathToFileURL } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
const base = process.env.ZS_API_SMOKE_BASE_URL;
assert.match(base ?? '', /^http:\/\/(?:127\.0\.0\.1|localhost):\d+$/);
const password = process.env.ZS_DEV_ADMIN_PASSWORD;
assert.ok(password && password.length === 16, 'Generated 16-character test password is required');
const wrongPassword = (password[0] === 'A' ? 'B' : 'A') + password.slice(1);
const secrets = new Set([password, password + 'invalid', wrongPassword]);
// Captured credentials stay inside this module; neither export nor persist their values.
export async function runApiSmoke({ signal } = {}) {
  const sleep = ms => delay(ms, undefined, { signal });
  const requestSignal = ms => signal ? AbortSignal.any([signal, AbortSignal.timeout(ms)]) : AbortSignal.timeout(ms);
  const privateFixtureId = '00000000-0000-0000-0000-000000000001';
  const publicFixturePrefix = '/admin-api/infra/file/99001/get/';
  async function request(path, { token, tenant, visitTenant, method = 'GET', body } = {}) {
    const headers = {};
    if (token) headers.Authorization = `Bearer ${token}`;
    if (tenant) headers['tenant-id'] = String(tenant);
    if (visitTenant) headers['visit-tenant-id'] = String(visitTenant);
    if (body && !(body instanceof FormData)) headers['Content-Type'] = 'application/json';
    const response = await fetch(base + path, { method, headers,
      body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
      signal: requestSignal(15000) });
    return { status: response.status, ...await response.json() };
  }
  function ok(result, label) {
    assert.equal(result.code, 0, `${label}: HTTP ${result.status}, API code ${result.code}`); return result.data;
  }
  function denied(result, label) {
    assert.ok(result.status < 500, `${label}: unexpected server error`);
    assert.ok([401, 403].includes(result.code) || [401, 403].includes(result.status),
      `${label}: expected authentication/authorization denial, got ${result.code}`);
  }
  function pass(label) { console.log(`PASS ${label}`); }
  let healthy = false;
  for (let i = 0; i < 120; i++) {
    try {
      const response = await fetch(base + '/actuator/health', { signal: requestSignal(1000) });
      const data = await response.json();
      if (response.ok && data.status === 'UP') { healthy = true; break; }
    } catch {
      if (signal?.aborted) throw new Error('API smoke interrupted');
      // The application can still be starting.
    }
    await sleep(1000);
  }
  assert.ok(healthy, 'Application did not become healthy; inspect the local application log');
  pass('real jar health, PostgreSQL and Redis');
  for (const endpoint of ['env', 'configprops', 'heapdump']) {
    const response = await fetch(base + '/actuator/' + endpoint, { signal: requestSignal(10000) });
    if (!(response.headers.get('content-type') ?? '').includes('json')) {
      await response.body?.cancel();
      assert.ok([401, 403, 404].includes(response.status), `Sensitive Actuator ${endpoint} is reachable`);
    } else {
      const data = await response.json();
      assert.ok([401, 403, 404].includes(response.status) || [401, 403, 404].includes(data.code), `Sensitive Actuator ${endpoint} is reachable`);
    }
  }
  pass('sensitive Actuator endpoints unavailable');
  // A real public DBFileClient download must work anonymously, with no tenant header.
  const publicResponse = await fetch(base + publicFixturePrefix + 'pg-smoke/public-fixture.txt', {
    signal: requestSignal(15000) });
  assert.equal(publicResponse.status, 200, 'Public fixture must remain anonymously readable');
  assert.deepEqual(new Uint8Array(await publicResponse.arrayBuffer()),
    new TextEncoder().encode('zhongshu-public-smoke-fixture'), 'Public fixture bytes must match');
  const privateViaPublic = await fetch(base + publicFixturePrefix + privateFixtureId, {
    signal: requestSignal(15000) });
  assert.equal(privateViaPublic.status, 404, 'A private UUID must not resolve in the public FileClient namespace');
  assert.equal((await privateViaPublic.arrayBuffer()).byteLength, 0, 'Public path must not return private bytes');
  pass('public DB asset downloads anonymously; private UUID has no public-download bypass');
  denied(await request('/admin-api/system/auth/get-permission-info', { tenant: 99001 }), 'anonymous identity');
  pass('anonymous access rejected');
  assert.equal((await request('/admin-api/system/auth/login', { tenant: 99001, method: 'POST',
    body: { username: 'pgadmina', password: password + 'invalid' } })).code, 400, 'Invalid login remains a validation error');
  const wrongLogin = await request('/admin-api/system/auth/login', { tenant: 99001, method: 'POST',
    body: { username: 'pgadmina', password: wrongPassword } });
  assert.ok(wrongLogin.status < 500 && wrongLogin.code !== 0, 'Wrong well-formed password must be rejected');
  const malformedResponse = await fetch(base + '/admin-api/system/auth/login', { method: 'POST',
    headers: { 'Content-Type': 'application/json', 'tenant-id': '99001' },
    body: '{"password":"' + password + '", broken', signal: requestSignal(15000) });
  assert.equal((await malformedResponse.json()).code, 400, 'Malformed credential-bearing JSON remains a validation error');
  pass('wrong password, overlong password and malformed login payload rejected safely');
  const users = [{ username: 'pgadmina', tenant: 99001 }, { username: 'pgadminb', tenant: 99002 }];
  for (const user of users) {
    const login = ok(await request('/admin-api/system/auth/login', { tenant: user.tenant, method: 'POST',
      body: { username: user.username, password } }), 'login');
    assert.ok(login.accessToken && login.refreshToken);
    secrets.add(login.accessToken); secrets.add(login.refreshToken);
    let token = login.accessToken;
    const identity = ok(await request('/admin-api/system/auth/get-permission-info', { token, tenant: user.tenant }), 'identity');
    assert.equal(identity.user.username, user.username); assert.ok(identity.menus.length > 0);
    assert.ok(Array.isArray(identity.permissions), 'Identity must include server-issued permissions');
    assert.equal(identity.permissions.includes('system:tenant:visit'), user.tenant === 99001,
      'Only A has the explicitly seeded operations visit permission');
    assert.equal(identity.roles.includes('pg_visit_operator_fixture'), user.tenant === 99001,
      'Only A has the isolated operations fixture role');
    pass(`tenant ${user.tenant}: password login and navigation permissions`);
    const page = ok(await request('/admin-api/system/user/page?pageNo=1&pageSize=10', { token, tenant: user.tenant }), 'user page');
    assert.deepEqual(page.list.map(item => item.username), [user.username]);
    const menus = ok(await request('/admin-api/system/menu/simple-list', { token, tenant: user.tenant }), 'menu list');
    assert.equal(menus.length, 7);
    assert.equal(menus.filter(menu => menu.type !== 3).length, 6);
    assert.deepEqual(menus.filter(menu => menu.type === 3).map(menu => menu.id), [99007]);
    pass(`tenant ${user.tenant}: scoped user list, six navigation items plus one operations-fixture permission button`);
    const otherTenant = user.tenant === 99001 ? 99002 : 99001;
    denied(await request('/admin-api/system/user/page?pageNo=1&pageSize=10', { token, tenant: otherTenant }), 'forged tenant header');
    pass(`tenant ${user.tenant}: forged tenant header rejected`);
    if (user.tenant === 99001) {
      // Positive control: the existing operations permission really activated tenant B context.
      const visitedPage = ok(await request('/admin-api/system/user/page?pageNo=1&pageSize=10', {
        token, tenant: user.tenant, visitTenant: otherTenant }), 'authorized operations tenant visit');
      assert.deepEqual(visitedPage.list.map(item => item.username), ['pgadminb']);
      const privateAfterVisit = await request('/admin-api/infra/private-file/' + privateFixtureId, {
        token, tenant: user.tenant, visitTenant: otherTenant });
      assert.equal(privateAfterVisit.code, 403, 'Authorized tenant visit must still be refused by private-file access');
      pass('tenant A: approved operations visit reaches B user list, but known B private attachment remains forbidden');
    } else {
      const unapprovedVisit = await request('/admin-api/infra/private-file/' + privateFixtureId, {
        token, tenant: user.tenant, visitTenant: otherTenant });
      assert.equal(unapprovedVisit.code, 403, 'B has no operations visit permission');
      pass('tenant B: unapproved visit-tenant header rejected');
    }
    const privateWithoutObjectGrant = await request('/admin-api/infra/private-file/' + privateFixtureId, {
      token, tenant: user.tenant });
    assert.equal(privateWithoutObjectGrant.code, 403, 'A known private ID must not replace an object authorization');
    const multipart = new FormData();
    multipart.set('objectType', 'pg_test_object'); multipart.set('objectId', 'object-1'); multipart.set('purpose', 'verification');
    multipart.set('file', new Blob(['fixture']), 'fixture.txt');
    denied(await request('/admin-api/infra/private-file/upload', { token, tenant: user.tenant, method: 'POST', body: multipart }),
      'private upload without an object authorizer');
    pass(`tenant ${user.tenant}: private upload fails closed without business authorizer`);
    const refreshed = ok(await request('/admin-api/system/auth/refresh-token?refreshToken=' + encodeURIComponent(login.refreshToken), {
      tenant: user.tenant, method: 'POST' }), 'refresh');
    assert.ok(refreshed.accessToken && refreshed.accessToken !== token);
    secrets.add(refreshed.accessToken); secrets.add(refreshed.refreshToken);
    denied(await request('/admin-api/system/auth/get-permission-info', { token, tenant: user.tenant }), 'superseded token');
    token = refreshed.accessToken;
    ok(await request('/admin-api/system/auth/get-permission-info', { token, tenant: user.tenant }), 'refreshed identity');
    ok(await request('/admin-api/system/auth/logout', { token, tenant: user.tenant, method: 'POST' }), 'logout');
    denied(await request('/admin-api/system/auth/get-permission-info', { token, tenant: user.tenant }), 'logged-out token');
    pass(`tenant ${user.tenant}: refresh invalidates old access token; logout invalidates new token`);
  }
  pass('API assertions complete; credential-log verification is still pending');
}

export function verifyCapturedSecretsInLogs(logPaths) {
  assert.ok(Array.isArray(logPaths) && logPaths.length >= 2,
    'Both application and rolling log sinks must be inspected');
  for (const logPath of logPaths) {
    let contents;
    try {
      const bytes = readFileSync(logPath);
      contents = (logPath.endsWith('.gz') ? gunzipSync(bytes) : bytes).toString('utf8');
    } catch {
      throw new Error('A required log or compressed fragment could not be inspected; details omitted');
    }
    // Assertions deliberately omit actual values, filenames and underlying exceptions.
    assert.ok([...secrets].every(secret => secret && !contents.includes(secret)),
      'An application log contains a test credential or token; values are intentionally not reported');
    assert.ok(!/Max queue flush timeout[^\n]*exceeded/i.test(contents),
      'The asynchronous logging queue did not flush completely');
  }
}

// Direct invocation can only report a live snapshot; the official runner stops Java first.
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    await runApiSmoke();
    verifyCapturedSecretsInLogs([process.env.ZS_API_SMOKE_APPLICATION_LOG, process.env.ZS_LOG_FILE]);
    console.log('PASS live log snapshot only; use run-api-smoke.sh for post-shutdown verification.');
  } catch {
    console.error('FAIL API smoke or live log snapshot; sensitive failure details are intentionally omitted.');
    process.exitCode = 1;
  }
}
