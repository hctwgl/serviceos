import { createHash } from 'node:crypto'
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url))
const moduleDirectory = path.resolve(scriptDirectory, '..')
const sourcePath = path.join(moduleDirectory, 'src/main/resources/client-identities/serviceos-client-identities-v1.json')
const outputRoot = path.join(moduleDirectory, 'target/generated-client-identities')
const typescriptPath = path.join(outputRoot, 'typescript/client-identities.ts')
const swiftPath = path.join(outputRoot, 'swift/ServiceOSClientIdentities.swift')
const manifestPath = path.join(outputRoot, 'manifest.json')

const sourceText = await readFile(sourcePath, 'utf8')
const source = JSON.parse(sourceText)
const expectedKeys = ['actionCodes', 'catalogVersion', 'featureIds', 'pageIds', 'schemaVersion']
if (source.schemaVersion !== 1 || Object.keys(source).sort().join(',') !== expectedKeys.join(',')) {
  throw new Error('Client Identity 顶层结构或 schemaVersion 非法')
}
if (source.catalogVersion !== 'client-identities-v1') {
  throw new Error('Client Identity catalogVersion 非法')
}

const requireUniqueStrings = (name, values, pattern) => {
  if (!Array.isArray(values) || values.length === 0 || values.some((value) => typeof value !== 'string' || !pattern.test(value))) {
    throw new Error(`${name} 必须是符合命名规则的非空字符串数组`)
  }
  if (new Set(values).size !== values.length) throw new Error(`${name} 不得包含重复值`)
}
requireUniqueStrings('pageIds', source.pageIds, /^(?:ADMIN|NETWORK|TECHNICIAN)\.[A-Z0-9_.]+$/)
requireUniqueStrings('actionCodes', source.actionCodes, /^(?:[A-Z][A-Z0-9_]*|[a-z][A-Za-z0-9]*(?:\.[a-z][A-Za-z0-9]*)+)$/)

if (!Array.isArray(source.featureIds) || source.featureIds.length === 0) throw new Error('featureIds 必须是非空数组')
const featureIds = source.featureIds.map((feature) => {
  if (!feature || Object.keys(feature).sort().join(',') !== 'defaultState,id,lifecycle'
      || !/^[A-Z][A-Z0-9_]*$/.test(feature.id)
      || !['DISABLED', 'ENABLED'].includes(feature.defaultState)
      || !['ACTIVE', 'RESERVED'].includes(feature.lifecycle)) {
    throw new Error('Feature ID 结构或值非法')
  }
  if (feature.lifecycle === 'RESERVED' && feature.defaultState !== 'DISABLED') {
    throw new Error(`预留 Feature 必须默认关闭: ${feature.id}`)
  }
  return feature.id
})
if (new Set(featureIds).size !== featureIds.length) throw new Error('featureIds 不得重复')

const quote = (value) => JSON.stringify(value)
const union = (values) => values.map(quote).join(' | ')
const tsLines = [
  '// Generated from serviceos-client-identities-v1.json. Do not edit.',
  `export const CLIENT_IDENTITY_CATALOG_VERSION = ${quote(source.catalogVersion)} as const`,
  `export const PAGE_IDS = [${source.pageIds.map(quote).join(', ')}] as const`,
  'export type PageId = (typeof PAGE_IDS)[number]',
  `export const FEATURE_IDS = [${featureIds.map(quote).join(', ')}] as const`,
  'export type FeatureId = (typeof FEATURE_IDS)[number]',
  `export const ACTION_CODES = [${source.actionCodes.map(quote).join(', ')}] as const`,
  'export type ActionCode = (typeof ACTION_CODES)[number]',
  'const ACTION_CODE_SET: ReadonlySet<string> = new Set(ACTION_CODES)',
  'export function isKnownActionCode(value: string): value is ActionCode { return ACTION_CODE_SET.has(value) }',
  '// 服务端新增动作而旧客户端尚未识别时必须隐藏，不能猜测命令、参数或授权语义。',
  'export function filterKnownActionCodes(values: readonly string[]): ActionCode[] { return values.filter(isKnownActionCode) }',
  '',
]

const swiftStringArray = (values) => values.map((value) => `        ${quote(value)}`).join(',\n')
const swiftLines = [
  '// Generated from serviceos-client-identities-v1.json. Do not edit.',
  'public enum ServiceOSClientIdentities {',
  `    public static let catalogVersion = ${quote(source.catalogVersion)}`,
  '    public static let pageIds: Set<String> = [',
  swiftStringArray(source.pageIds),
  '    ]',
  '    public static let featureIds: Set<String> = [',
  swiftStringArray(featureIds),
  '    ]',
  '    public static let actionCodes: Set<String> = [',
  swiftStringArray(source.actionCodes),
  '    ]',
  '',
  '    public static func isKnownActionCode(_ value: String) -> Bool { actionCodes.contains(value) }',
  '    /// 服务端新增动作而旧客户端尚未识别时必须隐藏，不能猜测命令、参数或授权语义。',
  '    public static func filterKnownActionCodes(_ values: [String]) -> [String] {',
  '        values.filter(isKnownActionCode)',
  '    }',
  '}',
  '',
]

await rm(outputRoot, { recursive: true, force: true })
await mkdir(path.dirname(typescriptPath), { recursive: true })
await mkdir(path.dirname(swiftPath), { recursive: true })
await writeFile(typescriptPath, `${tsLines.join('\n')}`, 'utf8')
await writeFile(swiftPath, `${swiftLines.join('\n')}`, 'utf8')

const sha256 = (value) => createHash('sha256').update(value).digest('hex')
const typescriptText = await readFile(typescriptPath, 'utf8')
const swiftText = await readFile(swiftPath, 'utf8')
const manifest = {
  artifact: 'serviceos-client-identities',
  schemaVersion: source.schemaVersion,
  catalogVersion: source.catalogVersion,
  sourcePath: 'serviceos-contracts/src/main/resources/client-identities/serviceos-client-identities-v1.json',
  sourceSha256: sha256(sourceText),
  outputs: {
    typescriptSha256: sha256(typescriptText),
    swiftSha256: sha256(swiftText),
  },
  generatedTreeSha256: sha256(`${sha256(typescriptText)}\n${sha256(swiftText)}\n`),
}
await writeFile(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8')

console.log(`Generated Client Identities: ${outputRoot}`)
console.log(`Client Identity tree digest: ${manifest.generatedTreeSha256}`)
