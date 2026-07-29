#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
local_config="${repo_root}/.release.local"

if [[ ! -f "${local_config}" ]]; then
  echo "缺少 ${local_config}"
  echo "请创建该文件并配置 JKS_PATH 和 JKS_PASSWORD。"
  exit 1
fi

set -a
# shellcheck source=/dev/null
source "${local_config}"
set +a

: "${JKS_PATH:=${repo_root}/tailscale.jks}"

if [[ -z "${JKS_PASSWORD:-}" ]]; then
  echo "JKS_PASSWORD 尚未填写，请编辑 ${local_config}。"
  exit 1
fi

if [[ "${JKS_PATH}" != /* ]]; then
  JKS_PATH="${repo_root}/${JKS_PATH}"
fi

if [[ ! -f "${JKS_PATH}" ]]; then
  echo "找不到签名文件：${JKS_PATH}"
  exit 1
fi

export JKS_PATH JKS_PASSWORD

cd "${repo_root}/android"
./gradlew assembleRelease

echo
echo "Release APK 已生成："
find build/outputs/apk/release -maxdepth 1 -type f -name '*-release.apk' -print
