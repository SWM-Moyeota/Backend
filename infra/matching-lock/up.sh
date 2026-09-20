#!/bin/sh
# 매칭 잠금 실험용 Redis 토폴로지를 띄운다.  사용법: infra/matching-lock/up.sh sentinel
# 노드가 자신을 알릴 주소(HOST_IP)를 호스트 LAN IP 로 계산한다. Docker Desktop(macOS)에서 컨테이너와 호스트 JVM 이 같은 주소로 닿는 유일한 값이다.
set -eu
TOPOLOGY="${1:?sentinel 중 하나를 지정하세요}"
DIR="$(cd "$(dirname "$0")" && pwd)"

if [ -z "${HOST_IP:-}" ]; then
  if command -v ipconfig >/dev/null 2>&1; then
    HOST_IP="$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || true)"
  fi
  if [ -z "${HOST_IP:-}" ] && command -v hostname >/dev/null 2>&1; then
    HOST_IP="$(hostname -I 2>/dev/null | awk '{print $1}' || true)"
  fi
fi
if [ -z "${HOST_IP:-}" ]; then
  echo "HOST_IP 를 계산하지 못했습니다. HOST_IP=<호스트 LAN IP> 로 지정하세요." >&2
  exit 1
fi
export HOST_IP
echo "HOST_IP=${HOST_IP}"
exec docker compose -f "${DIR}/compose.${TOPOLOGY}.yaml" up -d --wait
