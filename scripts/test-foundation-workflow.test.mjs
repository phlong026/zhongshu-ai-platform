import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { runInNewContext } from 'node:vm'

const workflow = readFileSync(new URL('../.github/workflows/foundation.yml', import.meta.url), 'utf8')
const condition = workflow.match(/^  miniapp:\n    if: \$\{\{ (.+) \}\}$/m)?.[1]
const expected = "github.event_name == 'pull_request' || (github.event_name == 'workflow_dispatch' && inputs.verify_mini_build)"

test('Mini CI condition is explicit and matches the approved PR/manual scope', () => {
  assert.equal(condition, expected)
  assert.match(workflow, /^  pull_request:$/m)
  assert.match(workflow, /^  workflow_dispatch:$/m)
  assert.doesNotMatch(workflow, /^  (?:push|pull_request_target):/m)
})

for (const [event, enabled, runs] of [
  ['pull_request', false, true],
  ['pull_request', true, true],
  ['workflow_dispatch', false, false],
  ['workflow_dispatch', true, true],
  ['push', true, false]
]) {
  test(`Mini condition: ${event}, manual flag ${enabled} => ${runs}`, () => {
    // Only evaluate the exact bounded expression asserted above; never arbitrary workflow code.
    assert.equal(condition, expected)
    assert.equal(runInNewContext(condition, {
      github: { event_name: event }, inputs: { verify_mini_build: enabled }
    }, { timeout: 100 }), runs)
  })
}


test('whitespace gate checks committed changes against an explicit base', () => {
  assert.match(workflow, /fetch-depth: 0/)
  assert.match(workflow, /DIFF_BASE_SHA: \$\{\{ github\.event\.pull_request\.base\.sha \|\| '[a-f0-9]{40}' \}\}/)
  assert.match(workflow, /run: git diff --check "\$DIFF_BASE_SHA" HEAD/)
  assert.doesNotMatch(workflow, /^\s+- run: git diff --check$/m)
})
