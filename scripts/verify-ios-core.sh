#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source_directory="${repository_root}/serviceos-ios-core/Sources/ServiceOSIOSCore"
build_directory="$(mktemp -d "${TMPDIR:-/tmp}/serviceos-ios-core-build.XXXXXX")"
cleanup() { rm -rf "${build_directory}"; }
trap cleanup EXIT

if rg -n '\b(ADMIN|NETWORK|TECHNICIAN|CONSUMER)\b' "${source_directory}"; then
  echo "共享 iOS Core 不得包含 Portal 或角色假设。" >&2
  exit 1
fi

find "${source_directory}" -name '*.swift' -print0 | xargs -0 swiftc \
  -swift-version 6 -parse-as-library -module-name ServiceOSIOSCore \
  -emit-module -emit-library \
  -emit-module-path "${build_directory}/ServiceOSIOSCore.swiftmodule" \
  -o "${build_directory}/libServiceOSIOSCore.dylib"

swiftc -swift-version 6 -parse-as-library \
  -I "${build_directory}" -L "${build_directory}" -lServiceOSIOSCore \
  "${repository_root}/serviceos-ios-core/Tests/IOSCoreSmoke.swift" \
  -o "${build_directory}/ios-core-smoke"
DYLD_LIBRARY_PATH="${build_directory}" "${build_directory}/ios-core-smoke"

echo "iOS Core 门禁通过：$(swiftc --version | head -1)"
