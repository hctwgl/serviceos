#!/usr/bin/env bash
set -euo pipefail

# 固定版本和发布校验值，避免 CI 通过可变 tag 或远程安装脚本执行未知内容。
readonly OASDIFF_VERSION="1.17.0"
readonly DESTINATION_DIR="${1:-${TMPDIR:-/tmp}/serviceos-contract-tools}"

case "$(uname -s):$(uname -m)" in
  Darwin:arm64|Darwin:x86_64)
    archive="oasdiff_${OASDIFF_VERSION}_darwin_all.tar.gz"
    expected_sha256="1a07d8166349aa9c2fc402a849caad86206a2747952030b6d775da945704a2e8"
    ;;
  Linux:x86_64|Linux:amd64)
    archive="oasdiff_${OASDIFF_VERSION}_linux_amd64.tar.gz"
    expected_sha256="cddb4763e66d6012cd4e70d41c7f742eee23db30bcdc8d64ef36b183bc6c1e97"
    ;;
  Linux:aarch64|Linux:arm64)
    archive="oasdiff_${OASDIFF_VERSION}_linux_arm64.tar.gz"
    expected_sha256="6af37f76983a8813f27d591a5b9fe4df8106fb512194831b3d7c5ed6a185312b"
    ;;
  *)
    echo "Unsupported platform for pinned oasdiff: $(uname -s) $(uname -m)" >&2
    exit 2
    ;;
esac

binary="${DESTINATION_DIR}/oasdiff"
if [[ -x "${binary}" ]] && [[ "$("${binary}" --version 2>/dev/null)" == *"${OASDIFF_VERSION}"* ]]; then
  printf '%s\n' "${binary}"
  exit 0
fi

mkdir -p "${DESTINATION_DIR}"
temporary_directory="$(mktemp -d "${TMPDIR:-/tmp}/serviceos-oasdiff.XXXXXX")"
trap 'rm -rf "${temporary_directory}"' EXIT

archive_path="${temporary_directory}/${archive}"
curl --fail --silent --show-error --location --retry 3 \
  "https://github.com/oasdiff/oasdiff/releases/download/v${OASDIFF_VERSION}/${archive}" \
  --output "${archive_path}"

if command -v sha256sum >/dev/null 2>&1; then
  actual_sha256="$(sha256sum "${archive_path}" | awk '{print $1}')"
else
  actual_sha256="$(shasum -a 256 "${archive_path}" | awk '{print $1}')"
fi

if [[ "${actual_sha256}" != "${expected_sha256}" ]]; then
  echo "oasdiff archive checksum mismatch: expected=${expected_sha256} actual=${actual_sha256}" >&2
  exit 1
fi

tar -xzf "${archive_path}" -C "${temporary_directory}"
extracted_binary="$(find "${temporary_directory}" -type f -name oasdiff -perm -u+x | head -n 1)"
if [[ -z "${extracted_binary}" ]]; then
  echo "oasdiff binary not found in ${archive}" >&2
  exit 1
fi

install -m 0755 "${extracted_binary}" "${binary}"
printf '%s\n' "${binary}"
