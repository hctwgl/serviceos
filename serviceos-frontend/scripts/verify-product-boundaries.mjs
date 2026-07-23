import { readdir, readFile } from 'node:fs/promises'
import { extname, join, relative } from 'node:path'

const workspaceRoot = new URL('..', import.meta.url)
const adminSource = new URL('../apps/admin/src/', import.meta.url)

async function sourceFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  const nested = await Promise.all(entries.map(async (entry) => {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) return sourceFiles(path)
    return ['.ts', '.vue', '.css'].includes(extname(path)) ? [path] : []
  }))
  return nested.flat()
}

const violations = []
for (const file of await sourceFiles(adminSource.pathname)) {
  const source = await readFile(file, 'utf8')
  const displayPath = relative(workspaceRoot.pathname, file)
  const visibleSource = extname(file) === '.vue'
    ? source.match(/<template\b[^>]*>([\s\S]*?)<\/template>/i)?.[1] ?? ''
    : source

  if (/from\s+['"](?:ant-design-vue|@ant-design\/icons-vue)['"]/.test(source)) {
    violations.push(`${displayPath}：必须通过 @serviceos/design-system 使用 Ant Design Vue`)
  }
  if (/#[0-9a-f]{3,8}\b/i.test(source)) {
    violations.push(`${displayPath}：不得绕过设计令牌硬编码颜色`)
  }
  if (extname(file) === '.vue'
      && /\b(?:principals?|sourceId|tenantId|capabilit(?:y|ies)|UUID|JSON)\b/i.test(visibleSource)) {
    violations.push(`${displayPath}：产品页面包含禁止展示的技术字段或术语`)
  }
  if (extname(file) === '.vue'
      && /(?:Demo Data|Golden Path|Portal Stub|演示数据|按 ID 查询|Token 登录)/i.test(visibleSource)) {
    violations.push(`${displayPath}：正式页面包含演示或调试入口`)
  }
}

if (violations.length > 0) {
  console.error(violations.join('\n'))
  process.exit(1)
}

console.log('产品边界静态检查通过')
