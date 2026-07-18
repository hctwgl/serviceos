import { createHash } from 'node:crypto'
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url))
const moduleDirectory = path.resolve(scriptDirectory, '..')
const sourcePath = path.join(moduleDirectory, 'src/main/resources/design-tokens/serviceos-design-tokens-v1.json')
const outputRoot = path.join(moduleDirectory, 'target/generated-design-tokens')
const webPath = path.join(outputRoot, 'web/serviceos-design-tokens.css')
const swiftPath = path.join(outputRoot, 'swift/ServiceOSDesignTokens.swift')
const manifestPath = path.join(outputRoot, 'manifest.json')

const sourceText = await readFile(sourcePath, 'utf8')
const source = JSON.parse(sourceText)

const requiredCategories = ['color', 'spacing', 'radius', 'typography', 'shadow']
if (source.schemaVersion !== 1 || Object.keys(source).sort().join(',') !== [...requiredCategories, 'schemaVersion'].sort().join(',')) {
  throw new Error('Design Token 顶层结构或 schemaVersion 非法')
}

const tokenNamePattern = /^[a-z][A-Za-z0-9]*$/
for (const category of requiredCategories) {
  const values = source[category]
  if (!values || Array.isArray(values) || typeof values !== 'object' || Object.keys(values).length === 0) {
    throw new Error(`Design Token 分类 ${category} 必须是非空对象`)
  }
  for (const [name, value] of Object.entries(values)) {
    if (!tokenNamePattern.test(name)) throw new Error(`Design Token 名称非法: ${category}.${name}`)
    if (typeof value !== 'string' && typeof value !== 'number') {
      throw new Error(`Design Token 值类型非法: ${category}.${name}`)
    }
    if (typeof value === 'string' && value.trim().length === 0) {
      throw new Error(`Design Token 字符串不得为空: ${category}.${name}`)
    }
    if (typeof value === 'number' && (!Number.isFinite(value) || value <= 0)) {
      throw new Error(`Design Token 数值必须为正数: ${category}.${name}`)
    }
    if (category === 'color' && !/^#[0-9A-F]{6}$/.test(value)) {
      throw new Error(`颜色必须使用大写六位 HEX: ${category}.${name}`)
    }
  }
}

const forbiddenRoleWords = /\b(?:ADMIN|NETWORK|TECHNICIAN|CONSUMER)\b/
if (forbiddenRoleWords.test(sourceText)) {
  throw new Error('共享 Design Token 不得包含 Portal 或角色假设')
}

const kebab = (value) => value.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`)
const cssValue = (category, value) => {
  if ((category === 'spacing' || category === 'radius') && typeof value === 'number') return `${value}px`
  return String(value)
}

const cssLines = [
  '/* Generated from serviceos-design-tokens-v1.json. Do not edit. */',
  ':root {',
]
for (const category of requiredCategories) {
  for (const [name, value] of Object.entries(source[category])) {
    cssLines.push(`  --serviceos-${category}-${kebab(name)}: ${cssValue(category, value)};`)
  }
}
cssLines.push('}', '')

const swiftLiteral = (value) => typeof value === 'number' ? String(value) : JSON.stringify(value)
const swiftType = (value) => typeof value === 'number' ? 'Double' : 'String'
const swiftLines = [
  '// Generated from serviceos-design-tokens-v1.json. Do not edit.',
  'public enum ServiceOSDesignTokens {',
]
for (const category of requiredCategories) {
  swiftLines.push(`    public enum ${category[0].toUpperCase()}${category.slice(1)} {`)
  for (const [name, value] of Object.entries(source[category])) {
    swiftLines.push(`        public static let ${name}: ${swiftType(value)} = ${swiftLiteral(value)}`)
  }
  swiftLines.push('    }')
}
swiftLines.push('}', '')

await rm(outputRoot, { recursive: true, force: true })
await mkdir(path.dirname(webPath), { recursive: true })
await mkdir(path.dirname(swiftPath), { recursive: true })
await writeFile(webPath, `${cssLines.join('\n')}`, 'utf8')
await writeFile(swiftPath, `${swiftLines.join('\n')}`, 'utf8')

const sha256 = (value) => createHash('sha256').update(value).digest('hex')
const webText = await readFile(webPath, 'utf8')
const swiftText = await readFile(swiftPath, 'utf8')
const manifest = {
  artifact: 'serviceos-design-tokens',
  schemaVersion: source.schemaVersion,
  sourcePath: 'serviceos-contracts/src/main/resources/design-tokens/serviceos-design-tokens-v1.json',
  sourceSha256: sha256(sourceText),
  outputs: {
    webCssSha256: sha256(webText),
    swiftSha256: sha256(swiftText),
  },
  generatedTreeSha256: sha256(`${sha256(webText)}\n${sha256(swiftText)}\n`),
}
await writeFile(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8')

console.log(`Generated Design Tokens: ${outputRoot}`)
console.log(`Design Token tree digest: ${manifest.generatedTreeSha256}`)
