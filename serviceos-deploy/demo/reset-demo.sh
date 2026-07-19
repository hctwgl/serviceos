#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

bash "${ROOT}/serviceos-deploy/demo/clear-demo.sh"
bash "${ROOT}/serviceos-deploy/demo/init-demo.sh"
echo "演示数据已重置。"
