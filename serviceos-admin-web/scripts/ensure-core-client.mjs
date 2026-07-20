#!/usr/bin/env node
/**
 * 确保 Admin Web 可消费最新 OpenAPI 生成的 @serviceos/core-client。
 * 生成物位于 contracts target，不入库；构建前必须可复现。
 */
import { existsSync } from 'node:fs'
import { spawnSync } from 'node:child_process'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const root = resolve(here, '../..')
const clientDir = resolve(root, 'serviceos-contracts/target/generated-clients/typescript-fetch')
const distIndex = resolve(clientDir, 'dist/index.js')
const generateScript = resolve(root, 'serviceos-contracts/scripts/generate-client-artifact.sh')

function run(command, args, cwd) {
  const result = spawnSync(command, args, { cwd, stdio: 'inherit', env: process.env })
  if (result.status !== 0) {
    process.exit(result.status ?? 1)
  }
}

if (!existsSync(resolve(clientDir, 'package.json'))) {
  run('bash', [generateScript], root)
}

if (!existsSync(distIndex)) {
  const tsc = resolve(root, 'serviceos-admin-web/node_modules/.bin/tsc')
  run(tsc, ['-p', 'tsconfig.json'], clientDir)
  run(tsc, ['-p', 'tsconfig.esm.json'], clientDir)
}

console.log('core-client ready:', clientDir)
