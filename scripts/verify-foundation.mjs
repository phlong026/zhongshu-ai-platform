import { existsSync, readFileSync, realpathSync } from 'node:fs'
import { dirname, isAbsolute, relative, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'
import { execFileSync } from 'node:child_process'
import { verifyFiles } from './verify-source-copy.mjs'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const requirementPattern = /\b(?:FND-[A-Z]+|PILOT-REQ|PEND)-\d{3}\b/g
const states = new Set(['DOCUMENTED', 'IMPLEMENTED', 'LOCALLY_VERIFIED', 'CI_VERIFIED', 'DEPLOYED', 'UAT_ACCEPTED', 'PRODUCTION_GO'])
const decisionLabels = { CONFIRMED: '已确认', PROPOSED: '建议方案', PENDING: '待决策', DEFERRED: '延后决策' }
const rows = text => text.split('\n').filter(line => line.startsWith('|')).map(line => line.split('|').slice(1, -1).map(cell => cell.trim().replaceAll('`', '')))
const same = (a, b) => JSON.stringify(Object.entries(a ?? {}).sort()) === JSON.stringify(Object.entries(b ?? {}).sort())
const outside = (base, target) => { const path = relative(base, target); return path === '..' || path.startsWith('..' + sep) || isAbsolute(path) }

export function verifyDocuments(projectRoot, state, read = path => readFileSync(resolve(projectRoot, path), 'utf8')) {
  const errors = []
  const report = (path, message) => errors.push(`${path}: ${message}`)
  const sources = Object.fromEntries(Object.keys(state.documents).map(path => [path, read(path)]))
  const readme = sources['README.md']
  for (const [path, version] of Object.entries(state.documents)) {
    const source = sources[path]
    const markers = [...source.matchAll(/<!-- foundation-state: (.*?) -->/g)]
    if (markers.length !== 1) report(path, 'exactly one foundation-state marker required')
    else {
      try {
        const marker = JSON.parse(markers[0][1])
        if (marker.version !== version) report(path, 'version marker mismatch')
        if (!same(marker.decisions, state.decisions)) report(path, 'decision state mismatch')
      } catch { report(path, 'invalid state JSON') }
    }
    if (version) {
      if (!source.includes(`> 文档版本：${version}`)) report(path, 'header version mismatch')
      const history = rows(source).filter(row => /^\d{4}-\d{2}-\d{2}$/.test(row[0]) && /^V\d/.test(row[1]))
      if (history.at(-1)?.[1] !== version) report(path, 'latest history version mismatch')
      const index = readme.split('\n').find(line => line.includes(`](${path})`))
      if (!index?.includes(`| ${version} |`)) report(path, 'README index version mismatch')
    }
    // Only governed product docs are scanned; imported donor prose remains an immutable snapshot.
    for (const match of source.matchAll(/\[[^\]\n]+\]\(([^)\s]+)(?:\s+"[^"]*")?\)/g)) {
      const href = match[1]
      if (/^(https?:|mailto:|#)/.test(href)) continue
      let target
      try { target = resolve(projectRoot, dirname(path), decodeURIComponent(href.split('#')[0])) }
      catch { report(path, `invalid link encoding: ${href}`); continue }
      if (outside(projectRoot, target) || !existsSync(target) || outside(realpathSync(projectRoot), realpathSync(target))) {
        report(path, `broken/outside relative link: ${href}`)
      }
    }
  }
  const planPath = 'docs/01-底座代码复用与改造方案.md'
  const decisions = rows(sources[planPath]).filter(row => /^D-\d{2}$/.test(row[0]))
  for (const [id, status] of Object.entries(state.decisions)) {
    const matching = decisions.filter(row => row[0] === id)
    if (matching.length !== 1 || matching[0][1] !== decisionLabels[status]) report(planPath, `decision table mismatch: ${id}`)
  }
  const ledgerPath = 'docs/02-一期底座需求规格与待决策台账.md'
  const ledger = sources[ledgerPath]
  const definitions = rows(ledger.split('## 9. 验收状态定义')[0]).filter(row => /^(FND-[A-Z]+|PILOT-REQ|PEND)-\d{3}$/.test(row[0]))
  const ids = new Set()
  for (const row of definitions) {
    if (ids.has(row[0])) report(ledgerPath, `duplicate requirement definition: ${row[0]}`)
    ids.add(row[0])
  }
  for (const [path, source] of Object.entries(sources)) {
    for (const id of source.match(requirementPattern) ?? []) if (!ids.has(id)) report(path, `undefined requirement: ${id}`)
    for (const match of source.matchAll(/\b((?:FND-[A-Z]+|PILOT-REQ|PEND)-)(\d{3})[～–](\d{3})\b/g)) {
      const start = Number(match[2]), end = Number(match[3])
      if (end < start) report(path, `invalid requirement range: ${match[0]}`)
      for (let n = start; n <= end; n++) {
        const id = match[1] + String(n).padStart(3, '0')
        if (!ids.has(id)) report(path, `undefined requirement: ${id}`)
      }
    }
  }
  const evidenceText = ledger.split('### 9.3 实施证据登记')[1]?.split('\n## 10.')[0] ?? ''
  const evidence = rows(evidenceText).filter(row => /^FND-/.test(row[0]))
  const registered = new Set()
  for (const row of evidence) {
    if (registered.has(row[0])) report(ledgerPath, `duplicate evidence: ${row[0]}`)
    registered.add(row[0])
    if (!ids.has(row[0]) || !states.has(row[1]) || row.length !== 6 || row.some(cell => !cell)) report(ledgerPath, `invalid/incomplete evidence: ${row[0]}`)
    const date = row[5] ?? ''
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || Number.isNaN(Date.parse(date)) || new Date(date).toISOString().slice(0, 10) !== date) report(ledgerPath, `invalid evidence date: ${row[0]}`)
    if (row.slice(2, 5).some(cell => /^(已完成|通过|N\/A)$/.test(cell))) report(ledgerPath, `evidence needs details: ${row[0]}`)
    if (row[1] === 'CI_VERIFIED' && !/https?:\/\//.test(row.slice(3, 5).join(' '))) report(ledgerPath, `CI evidence needs a verified run link: ${row[0]}`)
  }
  for (const row of rows(ledger)) {
    if (ids.has(row[0]) && row.some(cell => states.has(cell) && cell !== 'DOCUMENTED') && !registered.has(row[0])) report(ledgerPath, `missing evidence: ${row[0]}`)
  }
  const confirmedSection = ledger.split('## 3. 已确认架构决策')[1]?.split('\n## 4.')[0] ?? ''
  const confirmed = new Set(rows(confirmedSection).map(row => row[0]).filter(id => /^D-\d{2}$/.test(id)))
  for (const [id, status] of Object.entries(state.decisions)) {
    if (confirmed.has(id) !== (status === 'CONFIRMED')) report(ledgerPath, `confirmed decision mismatch: ${id}`)
    if (!ledger.includes(id)) report(ledgerPath, `missing decision definition: ${id}`)
  }
  for (const row of definitions.filter(row => /^PEND-/.test(row[0]))) {
    if (state.decisions[row[1]] !== 'CONFIRMED' && decisionLabels[state.decisions[row[1]]] !== row[2]) report(ledgerPath, `pending decision mismatch: ${row[1]}`)
  }
  return errors
}

export function verifyModules(projectRoot, state) {
  const readXml = path => readFileSync(resolve(projectRoot, path), 'utf8').replace(/<!--[\s\S]*?-->/g, '')
  const core = 'services/zhongshu-core/'
  const modules = [...readXml(`${core}pom.xml`).matchAll(/<module>\s*([^<]+?)\s*<\/module>/g)].map(m => m[1]).sort()
  const errors = []
  if (JSON.stringify(modules) !== JSON.stringify([...state.architecture.enabledModules].sort())) errors.push(`active root modules differ: ${modules.join(',')}`)
  const serverModules = [...readXml(`${core}yudao-server/pom.xml`).matchAll(/<artifactId>\s*(yudao-module-[^<]+?)\s*<\/artifactId>/g)].map(m => m[1]).sort()
  if (JSON.stringify(serverModules) !== JSON.stringify(['yudao-module-infra', 'yudao-module-system'])) errors.push(`active server modules differ: ${serverModules.join(',')}`)
  if (!/<java.version>\s*17\s*<\/java.version>/.test(readXml(`${core}pom.xml`)) || state.architecture.java !== 17) errors.push('JDK17 target required')
  return errors
}

export function verifyProvenance(projectRoot) {
  const baseline = '000e1dfb77d0b46f58a4a8993c4e4011a90a699d'
  const errors = []
  for (const path of ['third_party/source-copy-manifest.json', 'third_party/source-sanitization.json']) {
    try {
      const original = execFileSync('git', ['show', `${baseline}:${path}`], { cwd: projectRoot, maxBuffer: 20 * 1024 * 1024 })
      if (!original.equals(readFileSync(resolve(projectRoot, path)))) errors.push(`${path}: provenance baseline changed`)
    } catch { errors.push(`${path}: cannot read baseline ${baseline}; fetch full history`) }
  }
  const manifest = JSON.parse(readFileSync(resolve(projectRoot, 'third_party/source-copy-manifest.json'), 'utf8'))
  const donorFiles = manifest.files.filter(entry => entry.path.startsWith('reference/donors/'))
  if (donorFiles.length === 0) errors.push('donor baseline missing')
  errors.push(...verifyFiles(projectRoot, donorFiles).map(issue => `${issue.path}: ${issue.reason}`))
  return errors
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const state = JSON.parse(readFileSync(resolve(root, 'docs/foundation-state.json'), 'utf8'))
  const errors = [...verifyDocuments(root, state), ...verifyModules(root, state), ...verifyProvenance(root)]
  if (errors.length) { console.error(errors.join('\n')); process.exitCode = 1 }
  else console.log('Foundation governance passed: versions/decisions/links/IDs/evidence, module allowlist, and immutable source records/donor files. Runtime and semantic acceptance remain separate.')
}
