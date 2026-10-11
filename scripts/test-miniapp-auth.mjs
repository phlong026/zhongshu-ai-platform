// Node 24: node --test scripts/test-miniapp-auth.mjs
// Runs the production pure functions and actual interceptor/token-store source.
// Only UniApp conditional compilation and module dependencies are mocked; no
// application dependencies, network, browser, or real credentials are required.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { stripTypeScriptTypes } from 'node:module';
import { runInNewContext } from 'node:vm';
import { buildRequestHeaders, isTokenFreeRequest } from '../apps/zhongshu-miniapp/src/http/authHeaders.ts';
import { stringifyQuery } from '../apps/zhongshu-miniapp/src/http/tools/queryString.ts';
import { isDoubleTokenRes, isSingleTokenRes } from '../apps/zhongshu-miniapp/src/api/types/login.ts';

const baseUrl = 'https://api.example.test/admin-api';
const baseOptions = {
  url: '/system/user/get',
  token: 'session-access-token',
  tenantEnabled: true,
  tenantId: 1,
  visitTenantId: 2,
  apiBaseUrls: [baseUrl, '/proxy-api'],
};
const publicPaths = [
  '/system/auth/login',
  '/system/auth/refresh-token',
  '/system/tenant/get-id-by-name',
];

function assertNoSession(header) {
  for (const key of Object.keys(header)) {
    assert.notEqual(key.toLowerCase(), 'authorization');
    assert.notEqual(key.toLowerCase(), 'visit-tenant-id');
  }
}

test('each exact public API excludes an existing token and visit tenant', () => {
  for (const path of publicPaths) {
    for (const url of [path, `${baseUrl}${path}?value=1`, `/proxy-api${path}#fragment`]) {
      assert.equal(isTokenFreeRequest(url, baseOptions.apiBaseUrls), true, url);
      const header = buildRequestHeaders({ ...baseOptions, url });
      assertNoSession(header);
      assert.equal(header['tenant-id'], 1, 'login still needs its tenant identifier');
    }
  }
});

test('public APIs remove explicit or stale authentication headers in any casing', () => {
  for (const url of publicPaths) {
    const header = buildRequestHeaders({
      ...baseOptions,
      url,
      header: {
        Authorization: 'Bearer stale-token',
        authorization: 'Bearer other-token',
        AUTHORIZATION: 'Bearer uppercase-token',
        'Visit-Tenant-Id': 99,
        'visit-tenant-id': 98,
        Accept: 'application/json',
      },
    });
    assertNoSession(header);
    assert.equal(header.Accept, 'application/json');
  }
});

test('header.isToken=false overrides a valid token and is preserved for retries', () => {
  const header = buildRequestHeaders({
    ...baseOptions,
    header: { isToken: false, Authorization: 'Bearer supplied-token', 'visit-tenant-id': 88 },
  });
  assertNoSession(header);
  assert.equal(header.isToken, false);
  assert.equal(header['tenant-id'], 1);
  assertNoSession(buildRequestHeaders({ ...baseOptions, token: 'refreshed-token', header }));
});

test('ordinary authenticated requests carry the current token and visit tenant', () => {
  const header = buildRequestHeaders({ ...baseOptions, header: { 'Content-Type': 'application/json' } });
  assert.equal(header.Authorization, 'Bearer session-access-token');
  assert.equal(header['tenant-id'], 1);
  assert.equal(header['visit-tenant-id'], 2);
  assert.equal(header['Content-Type'], 'application/json');
});

test('only boolean false opts out and boolean true cannot override the whitelist', () => {
  for (const isToken of [undefined, true, 'false', 0]) {
    const header = buildRequestHeaders({ ...baseOptions, header: { isToken } });
    assert.equal(header.Authorization, 'Bearer session-access-token');
  }
  assertNoSession(buildRequestHeaders({ ...baseOptions, url: publicPaths[0], header: { isToken: true } }));
});

test('query, fragment, hostname and path substrings cannot bypass authentication', () => {
  const protectedUrls = [
    '/system/user/get?redirect=/system/auth/login',
    '/system/user/get#/system/auth/login',
    '/system/auth/login-audit',
    '/system/auth/login/history',
    '/system/auth/refresh-token-status',
    '/system/tenant/get-id-by-name-extra',
    '/reports/system/auth/login',
    '/other/admin-api/system/auth/login',
    '/admin-api-extra/system/auth/login',
    '/admin-api/system/auth/refresh-token/other',
    'https://login.example.test/admin-api/system/user/get',
    'https://api.example.test/admin-api/system/user/get?next=/refresh-token',
    '/system/auth/%6Cogin',
    '/system/auth/../auth/login',
  ];
  for (const url of protectedUrls) {
    assert.equal(isTokenFreeRequest(url, baseOptions.apiBaseUrls), false, url);
    assert.equal(buildRequestHeaders({ ...baseOptions, url }).Authorization, 'Bearer session-access-token', url);
  }
});

test('base-path handling accepts configured root/trailing slash and rejects unconfigured prefixes', () => {
  assert.equal(isTokenFreeRequest(`${baseUrl}/system/auth/login`, [`${baseUrl}/`]), true);
  assert.equal(isTokenFreeRequest('https://api.example.test/system/auth/login', ['https://api.example.test/']), true);
  assert.equal(isTokenFreeRequest('/custom-api/system/auth/login'), false);
  assert.equal(isTokenFreeRequest('/custom-api/system/auth/login', ['/custom-api']), true);
  assert.equal(isTokenFreeRequest('/custom-api/other/system/auth/login', ['/custom-api']), false);
});

test('missing or expired token cannot carry a visit tenant, including stale caller headers', () => {
  for (const token of [undefined, '']) {
    const header = buildRequestHeaders({ ...baseOptions, token, header: { 'Visit-Tenant-Id': 99 } });
    assertNoSession(header);
    assert.equal(header['tenant-id'], 1);
  }
});

test('tenant feature disabled or no current visit tenant prevents stale visit headers', () => {
  for (const overrides of [{ tenantEnabled: false }, { visitTenantId: null }, { visitTenantId: undefined }]) {
    const header = buildRequestHeaders({ ...baseOptions, ...overrides, header: { 'visit-tenant-id': 99 } });
    assert.equal(header.Authorization, 'Bearer session-access-token');
    assert.equal(header['visit-tenant-id'], undefined);
  }
  assert.equal(buildRequestHeaders({ ...baseOptions, tenantEnabled: false })['tenant-id'], undefined);
});

test('normal requests preserve explicit caller authorization when no store token is available', () => {
  const header = buildRequestHeaders({
    ...baseOptions,
    token: '',
    header: { authorization: 'Custom caller-credential', 'visit-tenant-id': 99 },
  });
  assert.equal(header.authorization, 'Custom caller-credential');
  assert.equal(header['visit-tenant-id'], undefined);
});

test('header calculation is immutable and refresh/tenant switch replace old casing and values', () => {
  const original = Object.freeze({ authorization: 'Bearer previous', 'TENANT-ID': 90, 'Visit-Tenant-Id': 91, trace: 'trace-id' });
  const header = buildRequestHeaders({ ...baseOptions, header: original });
  assert.equal(original.authorization, 'Bearer previous');
  assert.deepEqual(header, {
    trace: 'trace-id', Authorization: 'Bearer session-access-token', 'tenant-id': 1, 'visit-tenant-id': 2,
  });
  const replay = buildRequestHeaders({ ...baseOptions, header, token: 'new-access-token', visitTenantId: 3 });
  assert.equal(replay.Authorization, 'Bearer new-access-token');
  assert.equal(replay['visit-tenant-id'], 3);
});

// Execute real source with lightweight platform/store mocks. Fail on unknown
// imports so new dependencies cannot silently become untested global objects.
function loadSource(relativePath, exportName, bindings, symbols = new Set()) {
  const file = new URL(`../apps/zhongshu-miniapp/src/${relativePath}`, import.meta.url);
  let source = readFileSync(file, 'utf8');
  const active = [true];
  source = source.split('\n').filter((line) => {
    const start = line.match(/^\s*\/\/ #(ifdef|ifndef) (\S+)\s*$/);
    if (start) {
      const match = symbols.has(start[2]);
      active.push(active.at(-1) && (start[1] === 'ifdef' ? match : !match));
      return false;
    }
    if (/^\s*\/\/ #endif\s*$/.test(line)) {
      assert.ok(active.length > 1, 'balanced UniApp conditional compilation');
      active.pop();
      return false;
    }
    return active.at(-1);
  }).join('\n');
  assert.equal(active.length, 1, 'closed UniApp conditional compilation');
  source = stripTypeScriptTypes(source);
  source = source.replace(/^import\s+\{([\s\S]*?)\}\s+from\s+['"][^'"]+['"];?\s*$/gm, (_, imports) => {
    for (const imported of imports.split(',').map(value => value.trim()).filter(Boolean)) {
      const local = imported.split(/\s+as\s+/).at(-1);
      assert.ok(Object.hasOwn(bindings, local), `mock/import binding supplied for ${local}`);
    }
    return '';
  });
  source = source.replaceAll('import.meta.env', '__env').replace(/\bexport const /g, 'const ');
  return runInNewContext(`${source}\n;${exportName}`, bindings, { filename: file.pathname });
}

function interceptorFixture({ platform = 'MP-WEIXIN', proxy = false, tenantEnabled = true, token = 'session-access-token' } = {}) {
  const interceptors = new Map();
  const state = { token, tenantId: 1, visitTenantId: 2, tokenReads: 0, userReads: 0 };
  const plugin = loadSource('http/interceptor.ts', 'requestInterceptor', {
    __env: { VITE_APP_TENANT_ENABLE: String(tenantEnabled), VITE_APP_PROXY_ENABLE: String(proxy), VITE_APP_PROXY_PREFIX: '/proxy-api' },
    getEnvBaseUrl: () => baseUrl,
    useTokenStore: () => ({ updateNowTime: () => { state.tokenReads += 1; return { validToken: state.token }; } }),
    useUserStore: () => { state.userReads += 1; return state; },
    buildRequestHeaders,
    stringifyQuery,
    ApiEncrypt: { encryptRequest: data => `encrypted:${JSON.stringify(data)}`, getEncryptHeader: () => 'X-Api-Encrypt' },
    uni: { addInterceptor: (name, interceptor) => interceptors.set(name, interceptor) },
    console,
  }, new Set([platform]));
  plugin.install();
  return { interceptors, state };
}

for (const mode of [{ platform: 'MP-WEIXIN' }, { platform: 'H5' }, { platform: 'H5', proxy: true }]) {
  const label = `${mode.platform}${mode.proxy ? ' proxy' : ''}`;
  test(`actual ${label} interceptor handles login, refresh, protected requests and replay`, () => {
    const { interceptors, state } = interceptorFixture(mode);
    assert.equal(interceptors.size, 2);
    assert.equal(interceptors.get('request'), interceptors.get('uploadFile'));
    const { invoke } = interceptors.get('request');
    const prefix = mode.proxy ? '/proxy-api' : baseUrl;
    for (const url of publicPaths) {
      const result = invoke({ url, query: { name: 'tenant name' }, header: { authorization: 'Bearer stale', 'Visit-Tenant-Id': 9 } });
      assert.equal(result.url, `${prefix}${url}?name=tenant%20name`);
      assert.equal(result.timeout, 60000);
      assertNoSession(result.header);
      assert.equal(result.header['tenant-id'], 1);
    }
    const protectedResult = invoke({ url: '/system/user/get?redirect=/system/auth/login', header: { Accept: '*/*' } });
    assert.equal(protectedResult.header.Authorization, 'Bearer session-access-token');
    assert.equal(protectedResult.header['visit-tenant-id'], 2);
    state.token = 'refreshed-access-token';
    state.visitTenantId = 3;
    const replay = invoke(protectedResult);
    assert.equal(replay.header.Authorization, 'Bearer refreshed-access-token');
    assert.equal(replay.header['visit-tenant-id'], 3);
    assertNoSession(invoke({ url: '/system/user/get', header: { isToken: false, Authorization: 'Bearer explicit' } }).header);
    assert.ok(state.tokenReads > 0);
  });
}

test('actual interceptor protects absolute upload URLs and preserves encryption/query behavior', () => {
  const { interceptors } = interceptorFixture();
  const result = interceptors.get('uploadFile').invoke({
    url: `${baseUrl}/infra/file/upload?type=image`,
    query: { names: ['one', 'two'], absent: undefined },
    header: { Accept: '*/*' },
    isEncrypt: true,
    data: { value: 1 },
  });
  assert.equal(result.url, `${baseUrl}/infra/file/upload?type=image&names=one&names=two`);
  assert.equal(result.header.Authorization, 'Bearer session-access-token');
  assert.equal(result.header['visit-tenant-id'], 2);
  assert.equal(result.header.Accept, '*/*');
  assert.equal(result.header['X-Api-Encrypt'], 'true');
  assert.equal(result.data, 'encrypted:{"value":1}');
});

test('actual interceptor handles absent token and disabled tenancy without reading the user store', () => {
  const { interceptors, state } = interceptorFixture({ token: '', tenantEnabled: false });
  const result = interceptors.get('request').invoke({ url: '/system/user/get', header: { 'visit-tenant-id': 9 } });
  assertNoSession(result.header);
  assert.equal(result.header['tenant-id'], undefined);
  assert.equal(state.userReads, 0);
});

function tokenStoreFixture({ doubleTokenMode = true, refreshResult, refreshError } = {}) {
  const logs = [];
  const refreshArguments = [];
  const storage = new Map();
  const ref = value => ({ __isRef: true, value });
  const computed = getter => ({ __isRef: true, get value() { return getter(); } });
  const useStore = loadSource('store/token.ts', 'useTokenStore', {
    ref,
    computed,
    defineStore: (_name, setup) => {
      let store;
      return () => store ??= new Proxy(setup(), {
        get(target, key) { const value = target[key]; return value?.__isRef ? value.value : value; },
      });
    },
    useToast: () => ({ success: () => {} }),
    _login: async () => {},
    _logout: async () => {},
    _socialLogin: async () => {},
    register: async () => {},
    smsLogin: async () => {},
    _refreshToken: async (token) => {
      refreshArguments.push(token);
      if (refreshError) throw refreshError;
      return refreshResult;
    },
    isDoubleTokenMode: doubleTokenMode,
    isDoubleTokenRes,
    isSingleTokenRes,
    useUserStore: () => ({ fetchUserInfo: async () => {}, clearUserInfo: () => {} }),
    useDictStore: () => ({ loadDictCacheWithRetry: async () => {}, clearDictCache: () => {} }),
    uni: {
      setStorageSync: (key, value) => storage.set(key, value),
      getStorageSync: key => storage.get(key),
      removeStorageSync: key => storage.delete(key),
      $emit: () => {},
    },
    console: Object.fromEntries(['log', 'error', 'warn', 'debug', 'info'].map(method => [method, (...args) => logs.push({ method, args })])),
  });
  return { store: useStore(), logs, storage, refreshArguments };
}

test('actual token store refresh updates both tokens and expiry without logging the response', async () => {
  const response = { accessToken: 'new-secret-access', refreshToken: 'new-secret-refresh', expiresTime: Date.now() + 60000 };
  const { store, logs, storage, refreshArguments } = tokenStoreFixture({ refreshResult: response });
  store.setTokenInfo({ accessToken: 'expired-access', refreshToken: 'existing-refresh', expiresTime: Date.now() - 1000 });
  assert.equal(await store.refreshToken(), response);
  assert.deepEqual(refreshArguments, ['existing-refresh']);
  assert.equal(store.tokenInfo, response);
  assert.equal(store.validToken, 'new-secret-access');
  assert.equal(storage.get('accessTokenExpireTime'), response.expiresTime);
  assert.deepEqual(logs, [], 'no token-bearing refresh response is logged');
});

test('actual token store tryGetValidToken refreshes once and reuses the valid result', async () => {
  const response = { accessToken: 'fresh-access', refreshToken: 'fresh-refresh', expiresTime: Date.now() + 60000 };
  const { store, refreshArguments, logs } = tokenStoreFixture({ refreshResult: response });
  store.setTokenInfo({ accessToken: 'expired', refreshToken: 'old-refresh', expiresTime: Date.now() - 1000 });
  assert.equal(await store.tryGetValidToken(), 'fresh-access');
  assert.equal(await store.tryGetValidToken(), 'fresh-access');
  assert.deepEqual(refreshArguments, ['old-refresh']);
  assert.deepEqual(logs, []);
});

test('actual token store rejects refresh errors and invalid or single-token sessions', async () => {
  const error = new Error('refresh failed');
  const failure = tokenStoreFixture({ refreshError: error });
  failure.store.setTokenInfo({ accessToken: 'old-access', refreshToken: 'old-refresh', expiresTime: Date.now() - 1000 });
  await assert.rejects(failure.store.refreshToken(), value => value === error);
  assert.equal(failure.store.tokenInfo.accessToken, 'old-access');
  const missing = tokenStoreFixture();
  await assert.rejects(missing.store.refreshToken(), /无效的refreshToken/);
  assert.deepEqual(missing.refreshArguments, []);
  const single = tokenStoreFixture({ doubleTokenMode: false });
  await assert.rejects(single.store.refreshToken(), /单token模式不支持刷新token/);
  assert.deepEqual(single.refreshArguments, []);
});
