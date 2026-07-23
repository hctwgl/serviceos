#!/usr/bin/env bash
set -euo pipefail

# Agent 精准验证统一入口：只包装仓库已存在、已记录的命令，不新增验证语义。
#
# 用法：
#   bash scripts/agent-verify.sh compile            编译 serviceos-backend（含依赖模块）
#   bash scripts/agent-verify.sh test <Class>       精准单元测试（可逗号分隔多个类）
#   bash scripts/agent-verify.sh it <Class>         精准 PostgreSQL IT（经 verify-local.sh 架构修正）
#   bash scripts/agent-verify.sh arch               Spring Modulith ArchitectureTest
#   bash scripts/agent-verify.sh contracts [base]   契约兼容门禁（默认对当前分支与 origin/master 的 merge-base）
#   bash scripts/agent-verify.sh client-ts          TypeScript Client 复现、编译、打包与消费门禁
#   bash scripts/agent-verify.sh client-swift       Swift Client 复现、Swift 6 编译与消费门禁
#   bash scripts/agent-verify.sh design-tokens      Web/Swift Design Token 复现与消费门禁
#   bash scripts/agent-verify.sh client-identities  Page/Feature/Action 注册、跨端生成与未知动作门禁
#   bash scripts/agent-verify.sh client-metadata    Web/iOS Header、后端低基数观测与 OpenAPI 元数据门禁
#   bash scripts/agent-verify.sh client-foundation  Track A 全部生成物、独立消费者与契约兼容总门禁
#   bash scripts/agent-verify.sh frontend           当前 Web Workspace 静态检查、单测与构建
#   bash scripts/agent-verify.sh technician-ios      Technician iOS Keychain/OIDC/Context/生成客户端基础门禁
#   bash scripts/agent-verify.sh technician-ios-app  Technician iOS Xcode/Simulator App、XCTest 与 XCUITest 门禁
#   bash scripts/agent-verify.sh technician-ios-distribution Technician iOS Production archive、隐私与签名失败关闭门禁
#   bash scripts/agent-verify.sh ios-core           iOS auth/context/error/trace 基础构建与消费门禁
#   bash scripts/agent-verify.sh docs               git diff --check + 脚本语法
#
# 全量 L3 验证统一走 bash scripts/verify-local.sh，不在本脚本内提供。

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${root}"

usage() {
  sed -n '3,16p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  exit "${1:-2}"
}

[[ $# -ge 1 ]] || usage 2
command_name="$1"
shift

case "${command_name}" in
  compile)
    exec ./mvnw --no-transfer-progress -pl serviceos-backend -am -DskipTests compile
    ;;
  test)
    [[ $# -ge 1 ]] || usage 2
    exec ./mvnw --no-transfer-progress -pl serviceos-backend -Dtest="$1" test
    ;;
  it)
    [[ $# -ge 1 ]] || usage 2
    exec bash scripts/verify-local.sh -pl serviceos-backend -Dtest="$1" test
    ;;
  arch)
    exec ./mvnw --no-transfer-progress -pl serviceos-backend -Dtest=ArchitectureTest test
    ;;
  contracts)
    # 契约兼容门禁与 CI java-contracts 一致：OpenAPI 破坏检查 + 已发布事件版本不可变 + 正负探针。
    # oasdiff 解析顺序：OASDIFF_BIN 环境变量 → PATH 中的 oasdiff → 安装固定版本到 target（不入库）。
    oasdiff_bin="${OASDIFF_BIN:-$(command -v oasdiff || true)}"
    if [[ -z "${oasdiff_bin}" || ! -x "${oasdiff_bin}" ]]; then
      oasdiff_bin="$(serviceos-contracts/scripts/install-oasdiff.sh serviceos-contracts/target/contract-tools)"
    fi
    export OASDIFF_BIN="${oasdiff_bin}"
    base_ref="${1:-${CONTRACT_BASE_REF:-}}"
    if [[ -z "${base_ref}" ]] && git cat-file -e 'origin/master^{commit}' 2>/dev/null; then
      # 使用分支起点而非 HEAD^，确保多提交变更中的早期破坏性修改不会被后续提交掩盖。
      base_ref="$(git merge-base HEAD origin/master)"
    fi
    if [[ -z "${base_ref}" ]]; then
      status_baseline="$(sed -n 's/^baselineCommit:[[:space:]]*//p' \
        serviceos-architecture/docs/implementation-status.md | head -1)"
      if [[ -n "${status_baseline}" ]] && git cat-file -e "${status_baseline}^{commit}" 2>/dev/null; then
        base_ref="${status_baseline}"
      fi
    fi
    if [[ -z "${base_ref}" ]]; then
      echo "无法解析稳定契约基线；请显式传入 base ref，或获取 origin/master。" >&2
      exit 2
    fi
    echo "契约兼容比较基线: ${base_ref}"
    serviceos-contracts/scripts/check-contract-compatibility.sh "${base_ref}"
    serviceos-contracts/scripts/test-contract-gates.sh
    ;;
  client-ts)
    serviceos-contracts/scripts/verify-client-generation-reproducibility.sh
    serviceos-contracts/scripts/verify-typescript-client-consumer.sh
    ;;
  client-swift)
    serviceos-contracts/scripts/verify-swift-client-generation-reproducibility.sh
    serviceos-contracts/scripts/verify-swift-client-consumer.sh
    ;;
  design-tokens)
    serviceos-contracts/scripts/verify-design-tokens.sh
    ;;
  client-identities)
    serviceos-contracts/scripts/verify-client-identities.sh
    ./mvnw --no-transfer-progress -pl serviceos-contracts -Dtest=ClientIdentityContractTest test
    ./mvnw --no-transfer-progress -pl serviceos-backend -Dtest=ClientIdentityPageRegistryAlignmentTest test
    ;;
  client-metadata)
    scripts/verify-ios-core.sh
    ./mvnw --no-transfer-progress -pl serviceos-backend -Dtest=CorrelationContextFilterTest test
    ./mvnw --no-transfer-progress -pl serviceos-contracts -Dtest=ClientMetadataContractTest test
    ;;
  client-foundation)
    scripts/verify-client-foundation.sh
    ;;
  frontend)
    corepack pnpm --dir serviceos-frontend check
    ;;
  technician-ios)
    scripts/verify-technician-ios-foundation.sh
    ;;
  technician-ios-app)
    scripts/verify-technician-ios-app.sh
    ;;
  technician-ios-distribution)
    scripts/verify-technician-ios-distribution.sh
    ;;
  ios-core)
    scripts/verify-ios-core.sh
    ;;
  docs)
    git diff --check
    bash -n scripts/*.sh
    echo "docs 检查通过。"
    ;;
  *)
    usage 2
    ;;
esac
