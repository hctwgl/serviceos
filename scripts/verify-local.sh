#!/usr/bin/env bash
set -euo pipefail

# ServiceOS 本地验证入口。
#
# Apple Silicon + OrbStack 场景下，如果用户环境中设置了
# DOCKER_DEFAULT_PLATFORM=linux/amd64，Testcontainers 会拉起 x86 PostgreSQL，
# 进而通过模拟器运行，导致真实 PostgreSQL 集成测试显著变慢。
# 本脚本只在当前 Maven 子进程中移除该环境变量，并校验宿主机原生架构镜像；
# 本地已有正确架构镜像时直接复用，不访问远端镜像仓库，也不跳过任何测试。

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repository_root}"

if [[ "$#" -eq 0 ]]; then
  set -- verify
fi

# 只有里程碑/全量 verify 才先执行机械预检；精准 test 保持快速反馈，不重复扩大验证范围。
if [[ " $* " == *" verify "* ]]; then
  bash scripts/test-verification-preflight.sh
  bash scripts/verify-milestone-preflight.sh
fi

postgres_image="${SERVICEOS_TEST_POSTGRES_IMAGE:-postgres:18-alpine}"
refresh_image="${SERVICEOS_TEST_REFRESH_IMAGE:-false}"
host_arch="$(uname -m)"

case "${host_arch}" in
  arm64|aarch64)
    native_platform="linux/arm64"
    expected_image_arch="arm64"
    ;;
  x86_64|amd64)
    native_platform="linux/amd64"
    expected_image_arch="amd64"
    ;;
  *)
    echo "无法识别宿主机架构：${host_arch}。请显式设置 SERVICEOS_TEST_NATIVE_PLATFORM。" >&2
    native_platform="${SERVICEOS_TEST_NATIVE_PLATFORM:-}"
    expected_image_arch=""
    ;;
esac

if [[ -n "${SERVICEOS_TEST_NATIVE_PLATFORM:-}" ]]; then
  native_platform="${SERVICEOS_TEST_NATIVE_PLATFORM}"
  expected_image_arch="${native_platform#linux/}"
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "未找到 docker 命令，无法运行 PostgreSQL Testcontainers。" >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "Docker/OrbStack 当前不可用，请先启动容器运行时。" >&2
  exit 1
fi

configured_platform="${DOCKER_DEFAULT_PLATFORM:-}"
if [[ -n "${configured_platform}" ]]; then
  if [[ -n "${native_platform}" && "${configured_platform}" != "${native_platform}" ]]; then
    echo "检测到 DOCKER_DEFAULT_PLATFORM=${configured_platform}，与宿主机原生平台 ${native_platform} 不一致。"
    echo "本次验证将仅在 Maven/Testcontainers 子进程中移除该变量，避免跨架构模拟。"
  else
    echo "检测到 DOCKER_DEFAULT_PLATFORM=${configured_platform}；本次验证仍将移除该全局覆盖，让镜像自动匹配宿主机。"
  fi
fi

pull_postgres_image() {
  if [[ -n "${native_platform}" ]]; then
    echo "正在拉取原生 PostgreSQL 测试镜像：${postgres_image} (${native_platform})"
    env -u DOCKER_DEFAULT_PLATFORM docker pull --platform "${native_platform}" "${postgres_image}" >/dev/null
  else
    echo "正在拉取 PostgreSQL 测试镜像：${postgres_image}"
    env -u DOCKER_DEFAULT_PLATFORM docker pull "${postgres_image}" >/dev/null
  fi
}

# 默认只在本地缺少镜像时拉取，避免每次验证都访问远端 registry。
# 需要主动检查并刷新镜像时，显式设置 SERVICEOS_TEST_REFRESH_IMAGE=true。
if [[ "${refresh_image}" == "true" ]]; then
  echo "已请求刷新 PostgreSQL 测试镜像。"
  pull_postgres_image
elif docker image inspect "${postgres_image}" >/dev/null 2>&1; then
  echo "复用本地 PostgreSQL 测试镜像：${postgres_image}"
else
  echo "本地不存在 PostgreSQL 测试镜像，执行首次拉取。"
  pull_postgres_image
fi

image_os="$(docker image inspect "${postgres_image}" --format '{{.Os}}' 2>/dev/null || true)"
image_arch="$(docker image inspect "${postgres_image}" --format '{{.Architecture}}' 2>/dev/null || true)"

if [[ -z "${image_os}" || -z "${image_arch}" ]]; then
  echo "无法读取 PostgreSQL 镜像架构：${postgres_image}" >&2
  exit 1
fi

actual_platform="${image_os}/${image_arch}"
echo "PostgreSQL 测试镜像架构：${actual_platform}"

if [[ -n "${expected_image_arch}" && "${image_arch}" != "${expected_image_arch}" ]]; then
  echo "PostgreSQL 镜像架构 ${actual_platform} 与宿主机期望 ${native_platform} 不一致。" >&2
  echo "请删除错误架构镜像后重试：docker image rm ${postgres_image}" >&2
  exit 1
fi

echo "执行 Maven 验证：./mvnw --no-transfer-progress $*"

# 只影响当前命令，不修改用户的永久环境变量。
# Testcontainers 将继承已经清理的平台环境，并使用上方校验过的本地原生镜像。
env -u DOCKER_DEFAULT_PLATFORM \
  SERVICEOS_TEST_POSTGRES_IMAGE="${postgres_image}" \
  ./mvnw --no-transfer-progress "$@"
