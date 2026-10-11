#!/usr/bin/env node
/**
 * Real-browser smoke test for an ISOLATED local server and disposable test user.
 * Never point this test at production or supply a production account.
 * Requires the official Playwright package and an installed Chromium browser.
 * Build only the isolated test copy with VITE_APP_CAPTCHA_ENABLE=false; leave
 * the production build setting enabled. This test does not solve CAPTCHAs.
 * Example (after starting PostgreSQL/Redis/API/Web in the same environment):
 * ZHONGSHU_TEST_ONLY=1 ADMIN_UI_URL=http://127.0.0.1:38080 \
 * ADMIN_TEST_TENANT=... ADMIN_TEST_USERNAME=... ADMIN_TEST_PASSWORD=... \
 * node scripts/test-admin-ui.mjs
 * Optional: PLAYWRIGHT_MODULE=/absolute/path/to/playwright/index.mjs,
 * CHROMIUM_EXECUTABLE_PATH=/usr/bin/chromium, ADMIN_UI_ARTIFACTS=/tmp/admin-ui.
 * Credentials are used only in memory; no tokens, HAR, trace, or storage-state files
 * are written. The test deliberately makes exactly one incorrect-password attempt.
 */
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { pathToFileURL } from 'node:url'

assert.equal(process.env.ZHONGSHU_TEST_ONLY, '1', 'Set ZHONGSHU_TEST_ONLY=1 for an isolated test environment')
const base = new URL(process.env.ADMIN_UI_URL || 'http://127.0.0.1:38080')
assert.ok(['127.0.0.1', 'localhost', '[::1]'].includes(base.hostname), 'Only loopback test servers are accepted')
assert.equal(base.protocol, 'http:', 'Use the isolated HTTP loopback server')
const tenant = process.env.ADMIN_TEST_TENANT
const username = process.env.ADMIN_TEST_USERNAME
const password = process.env.ADMIN_TEST_PASSWORD
assert.ok(tenant && username && password, 'Provide disposable ADMIN_TEST_TENANT, ADMIN_TEST_USERNAME, and ADMIN_TEST_PASSWORD')
const moduleName = process.env.PLAYWRIGHT_MODULE || 'playwright'
const { chromium } = await import(path.isAbsolute(moduleName) ? pathToFileURL(moduleName).href : moduleName)
const artifacts = path.resolve(process.env.ADMIN_UI_ARTIFACTS || '/tmp/zhongshu-admin-ui')
await mkdir(artifacts, { recursive: true })
const launchOptions = { headless: true }
if (process.env.CHROMIUM_EXECUTABLE_PATH) launchOptions.executablePath = process.env.CHROMIUM_EXECUTABLE_PATH
const browser = await chromium.launch(launchOptions)
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'zh-CN' })
const page = await context.newPage()
const pageErrors = []
const apiResults = []
let loginRequests = 0
page.on('pageerror', (error) => pageErrors.push(error.message))
page.on('request', (request) => {
  if (new URL(request.url()).pathname === '/admin-api/system/auth/login') loginRequests += 1
})
page.on('response', (response) => {
  const url = new URL(response.url())
  if (url.pathname.startsWith('/admin-api/')) apiResults.push({ path: url.pathname, status: response.status() })
})
const result = { status: 'failed', checks: [], apiResults }
const loginButton = page.getByRole('button', { name: '登录', exact: true })
const passwordField = page.getByPlaceholder('请输入密码', { exact: true })
const screenshot = (name) => page.screenshot({ path: path.join(artifacts, name), fullPage: true, mask: [page.locator('input[type="password"]')] })
const check = (name) => { result.checks.push(name); console.log(`PASS ${name}`) }
try {
  await page.goto(new URL('/login', base).href, { waitUntil: 'networkidle' })
  await loginButton.waitFor({ state: 'visible' })
  assert.ok(await page.getByPlaceholder('请输入用户名', { exact: true }).isVisible())
  check('login page renders')
  await screenshot('01-login.png')
  await page.getByPlaceholder('请输入租户名称', { exact: true }).fill('')
  await page.getByPlaceholder('请输入用户名', { exact: true }).fill('')
  await passwordField.fill('')
  await loginButton.click()
  await page.locator('.el-form-item__error').first().waitFor({ state: 'visible' })
  assert.equal(loginRequests, 0)
  assert.equal(await loginButton.isEnabled(), true)
  assert.equal(pageErrors.length, 0, pageErrors.join('\n'))
  check('empty form is rejected without request or runtime error')
  await page.getByPlaceholder('请输入租户名称', { exact: true }).fill(tenant)
  await page.getByPlaceholder('请输入用户名', { exact: true }).fill(username)
  await page.getByRole('checkbox', { name: '记住我' }).uncheck()
  await passwordField.fill('intentionally-invalid-ui-test-password')
  const rejected = page.waitForResponse((response) => new URL(response.url()).pathname === '/admin-api/system/auth/login')
  await loginButton.click()
  const rejectedBody = await (await rejected).json()
  assert.notEqual(rejectedBody.code, 0, 'Wrong password must be rejected')
  await page.waitForFunction(() => !document.querySelector('.login-form button.is-loading'))
  assert.equal(new URL(page.url()).pathname, '/login')
  assert.equal(await loginButton.isEnabled(), true)
  assert.ok(!pageErrors.some((message) => /(?:undefined|null).*close|close.*(?:undefined|null)/.test(message)), 'Rejected login must not close an uncreated loader')
  assert.ok(pageErrors.every((message) => message === 'error'), pageErrors.join('\n'))
  check('wrong password is rejected and login remains retryable')
  await screenshot('02-rejected-login.png')
  result.expectedRejectionErrorCount = pageErrors.splice(0).length
  await passwordField.fill(password)
  const loggedIn = page.waitForResponse((response) => new URL(response.url()).pathname === '/admin-api/system/auth/login')
  await loginButton.click()
  const loginBody = await (await loggedIn).json()
  assert.equal(loginBody.code, 0, 'Disposable account login must succeed')
  await page.waitForURL((url) => url.pathname !== '/login')
  await page.locator('.v-user-info').waitFor({ state: 'visible' })
  assert.equal(loginRequests, 2, 'Exactly one failed and one successful request should be made')
  assert.equal(pageErrors.length, 0, pageErrors.join('\n'))
  check('real API login loads the authenticated shell')
  await screenshot('03-authenticated-admin.png')
  await page.reload({ waitUntil: 'networkidle' })
  await page.locator('.v-user-info').waitFor({ state: 'visible' })
  assert.notEqual(new URL(page.url()).pathname, '/login')
  check('authenticated page survives reload')
  await page.locator('.v-user-info').click()
  await page.getByText('退出系统', { exact: true }).click()
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await page.locator('.v-user-info').waitFor({ state: 'visible' })
  check('cancelling logout keeps the session')
  await page.locator('.v-user-info').click()
  await page.getByText('退出系统', { exact: true }).click()
  await page.getByRole('button', { name: '确定', exact: true }).click()
  await page.waitForURL((url) => url.pathname === '/login')
  await page.goto(new URL('/index', base).href, { waitUntil: 'networkidle' })
  await loginButton.waitFor({ state: 'visible' })
  assert.equal(new URL(page.url()).pathname, '/login')
  check('logout removes access and protected URL redirects to login')
  assert.equal(pageErrors.length, 0, pageErrors.join('\n'))
  result.status = 'passed'
} catch (error) {
  result.error = error.message
  result.pageErrors = pageErrors
  process.exitCode = 1
  console.error(`FAIL ${error.message}`)
  await screenshot('failure.png').catch(() => {})
} finally {
  await writeFile(path.join(artifacts, 'result.json'), JSON.stringify(result, null, 2) + '\n')
  await context.close()
  await browser.close()
}
