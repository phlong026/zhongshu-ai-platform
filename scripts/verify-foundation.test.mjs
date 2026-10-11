import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { verifyDocuments, verifyModules, verifyProvenance } from './verify-foundation.mjs'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const state = JSON.parse(readFileSync(resolve(root, 'docs/foundation-state.json'), 'utf8'))
const plan = 'docs/01-底座代码复用与改造方案.md'
const ledger = 'docs/02-一期底座需求规格与待决策台账.md'
const check = (path, transform) => verifyDocuments(root, state, name => {
  const source = readFileSync(resolve(root, name), 'utf8')
  if (name !== path) return source
  const result = transform(source)
  assert.notEqual(result, source, `Mutation must change ${path}`)
  return result
})
const rejects = (path, transform, message) => assert.ok(check(path, transform).some(error => error.includes(message)), message)

test('governed documents, versions and decisions agree', () => assert.deepEqual(verifyDocuments(root, state), []))
test('rejects mismatching or duplicate decision markers', () => {
  rejects('README.md', s => s.replace('"D-09":"PENDING"', '"D-09":"CONFIRMED"'), 'decision state mismatch')
  rejects('README.md', s => s + '\n' + s.match(/<!-- foundation-state: .*? -->/)[0], 'exactly one')
})
test('rejects visible decision table drift even when marker is unchanged', () => {
  rejects(plan, s => s.replace('| D-09 | 待决策 |', '| D-09 | 已确认 |'), 'decision table mismatch')
  rejects(ledger, s => s.replace('| PEND-002 | D-09 | 待决策 |', '| PEND-002 | D-09 | 已确认 |'), 'pending decision mismatch')
})
test('rejects document header and latest history version drift', () => {
  rejects(ledger, s => s.replace('> 文档版本：V0.7', '> 文档版本：V0.6'), 'header version mismatch')
  rejects(ledger, s => s.replace('| 2026-10-11 | V0.7 |', '| 2026-10-11 | V0.6 |'), 'latest history version mismatch')
})
test('rejects README index version drift', () => rejects('README.md', s => s.replace('| V0.8 | 说明', '| V0.7 | 说明'), 'README index version mismatch'))
test('rejects missing, outside and malformed relative links', () => {
  rejects('README.md', s => s + '\n[missing](docs/missing.md)', 'broken/outside relative link')
  rejects('README.md', s => s + '\n[outside](../../other.md)', 'broken/outside relative link')
  rejects('README.md', s => s + '\n[malformed](%E0%A4%A)', 'invalid link encoding')
})
test('rejects duplicate requirement definitions', () => rejects(ledger, s => s.replace('## 9. 验收状态定义', '| FND-SRC-001 | duplicate | test |\n\n## 9. 验收状态定义'), 'duplicate requirement definition'))
test('rejects undefined requirement references including shorthand range ends', () => {
  rejects(ledger, s => s + '\nFND-MISSING-999', 'undefined requirement')
  rejects(ledger, s => s + '\nFND-DOC-001～004', 'undefined requirement: FND-DOC-004')
})
test('rejects promoted requirements without evidence', () => rejects(ledger, s => s.replace('| FND-SYS-001 | 用户、角色', '| FND-SYS-001 | IMPLEMENTED | 用户、角色'), 'missing evidence: FND-SYS-001'))
test('rejects empty, duplicate and invalid-date evidence', () => {
  rejects(ledger, s => s.replace('N/A：未发布；分发/上架前完成依赖与素材审查', ''), 'invalid/incomplete evidence')
  rejects(ledger, s => s.replace('\n## 10. 文档同步与变更控制', '\n| FND-SRC-001 | IMPLEMENTED | path | test | N/A: local | 2026-02-30 |\n\n## 10. 文档同步与变更控制'), 'duplicate evidence')
  rejects(ledger, s => s.replace('| 2026-09-08 |\n| FND-SRC-002', '| 2026-02-30 |\n| FND-SRC-002'), 'invalid evidence date')
})
test('module allowlist rejects accidental activation and Java drift', () => {
  const dir = mkdtempSync(resolve(tmpdir(), 'foundation-gate-'))
  try {
    mkdirSync(resolve(dir, 'services/zhongshu-core/yudao-server'), { recursive: true })
    const core = resolve(dir, 'services/zhongshu-core/pom.xml')
    const server = resolve(dir, 'services/zhongshu-core/yudao-server/pom.xml')
    writeFileSync(core, `<project><java.version>17</java.version>${state.architecture.enabledModules.map(m => `<module>${m}</module>`).join('')}</project>`)
    writeFileSync(server, '<project><artifactId>yudao-module-system</artifactId><artifactId>yudao-module-infra</artifactId><!-- <artifactId>yudao-module-ai</artifactId> --></project>')
    assert.deepEqual(verifyModules(dir, state), [])
    writeFileSync(server, readFileSync(server, 'utf8').replace('<!-- ', '').replace(' -->', ''))
    assert.ok(verifyModules(dir, state).some(e => e.includes('server modules differ')))
    writeFileSync(core, readFileSync(core, 'utf8').replace('<java.version>17', '<java.version>21'))
    assert.ok(verifyModules(dir, state).some(e => e.includes('JDK17')))
    writeFileSync(core, readFileSync(core, 'utf8').replace('</project>', '<module>yudao-module-ai</module></project>'))
    assert.ok(verifyModules(dir, state).some(e => e.includes('active root modules differ')))
  } finally { rmSync(dir, { recursive: true, force: true }) }
})
test('source manifests equal the original baseline and donor files remain intact', () => assert.deepEqual(verifyProvenance(root), []))
