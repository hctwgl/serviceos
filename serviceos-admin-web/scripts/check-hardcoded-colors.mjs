#!/usr/bin/env node
/**
 * 禁止在业务组件中用硬编码色值表达状态语义。
 * Foundations（tokens/theme）与测试夹具白名单除外。
 */
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'

const root = new URL('..', import.meta.url).pathname
const scanRoots = [join(root, 'src')]

const allowPath = (file) => {
  const rel = relative(root, file).replaceAll('\\', '/')
  if (rel.startsWith('src/styles/')) return true
  if (rel === 'src/app/app-theme.ts') return true
  if (rel.endsWith('.md')) return true
  return false
}

const FORBIDDEN = [
  /#(?:ff0000|f00|00ff00|0f0|0000ff|00f)\b/i,
  /\b(?:red|green|blue)-(?:50|[1-9]00)\b/,
  /color:\s*['"]red['"]/i,
  /background:\s*['"]green['"]/i,
]

const offenders = []

function walk(dir) {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name)
    const st = statSync(full)
    if (st.isDirectory()) {
      if (name === 'node_modules' || name === 'dist') continue
      walk(full)
      continue
    }
    if (!/\.(vue|ts|css)$/.test(name)) continue
    if (allowPath(full)) continue
    const text = readFileSync(full, 'utf8')
    for (const rule of FORBIDDEN) {
      if (rule.test(text)) {
        offenders.push(`${relative(root, full)} :: ${rule}`)
      }
    }
  }
}

for (const dir of scanRoots) walk(dir)

if (offenders.length) {
  console.error('Hardcoded status-like colors detected:')
  for (const line of offenders) console.error(' -', line)
  process.exit(1)
}

console.log('check-hardcoded-colors: ok')
