import assert from 'node:assert/strict'
import test from 'node:test'
import { runLoginAttempt } from '../apps/zhongshu-admin-web/src/views/Login/components/loginAttempt.ts'

const state = () => ({ pending: { value: false }, loading: { value: undefined } })

test('invalid form with no loading overlay is safely retryable', async () => {
  const { pending, loading } = state()
  await runLoginAttempt(pending, loading, async () => {})
  assert.equal(pending.value, false)
  assert.equal(loading.value, undefined)
})

test('rejected login preserves the original error before an overlay exists', async () => {
  const { pending, loading } = state()
  const error = new Error('incorrect disposable test password')
  await assert.rejects(runLoginAttempt(pending, loading, async () => { throw error }), (actual) => actual === error)
  assert.equal(pending.value, false)
  assert.equal(loading.value, undefined)
  let retried = false
  await runLoginAttempt(pending, loading, async () => { retried = true })
  assert.equal(retried, true)
})

test('successful login closes its overlay once and clears the reference', async () => {
  const { pending, loading } = state()
  let closed = 0
  await runLoginAttempt(pending, loading, async () => {
    assert.equal(pending.value, true)
    loading.value = { close() { closed += 1 } }
  })
  assert.equal(closed, 1)
  assert.equal(pending.value, false)
  assert.equal(loading.value, undefined)
  await runLoginAttempt(pending, loading, async () => {})
  assert.equal(closed, 1)
})

test('navigation failure after login still closes the overlay', async () => {
  const { pending, loading } = state()
  let closed = 0
  await assert.rejects(runLoginAttempt(pending, loading, async () => {
    loading.value = { close() { closed += 1 } }
    throw new Error('navigation failed')
  }), /navigation failed/)
  assert.equal(closed, 1)
  assert.equal(pending.value, false)
  assert.equal(loading.value, undefined)
})

test('repeated submission while pending does not send a second login', async () => {
  const { pending, loading } = state()
  let finish
  let submitted = 0
  const first = runLoginAttempt(pending, loading, async () => {
    submitted += 1
    await new Promise((resolve) => { finish = resolve })
  })
  await runLoginAttempt(pending, loading, async () => { submitted += 1 })
  assert.equal(submitted, 1)
  assert.equal(pending.value, true)
  finish()
  await first
  assert.equal(pending.value, false)
})
