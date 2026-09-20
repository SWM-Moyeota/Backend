#!/bin/sh
# 매칭 잠금 실험용 Redis 토폴로지를 띄운다.  사용법: infra/matching-lock/up.sh sentinel | cluster
# 노드가 자신을 알릴 주소(HOST_IP)를 호스트 LAN IP 로 계산한다. Docker Desktop(macOS)에서 컨테이너와 호스트 JVM 이 같은 주소로 닿는 유일한 값이다.
set -eu
TOPOLOGY="${1:?sentinel 또는 cluster 를 지정하세요}"
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
docker compose -f "${DIR}/compose.${TOPOLOGY}.yaml" up -d --wait
if [ "${TOPOLOGY}" = "cluster" ]; then
  # 6노드가 뜬 뒤 한 번만 도는 init 컨테이너가 끝날 때까지 기다린다
  docker compose -f "${DIR}/compose.${TOPOLOGY}.yaml" wait cluster-init
fi
