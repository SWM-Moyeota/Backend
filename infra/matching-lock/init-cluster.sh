#!/bin/sh
# 6노드를 마스터 3 + 복제본 3 클러스터로 묶는다. 이미 묶여 있으면 아무것도 하지 않는다.
set -eu
NODES=""
for p in 7001 7002 7003 7004 7005 7006; do NODES="${NODES} ${HOST_IP}:${p}"; done
if redis-cli -h "${HOST_IP}" -p 7001 cluster info | grep -q 'cluster_state:ok'; then
  echo "cluster already ok"; exit 0
fi
# shellcheck disable=SC2086
redis-cli --cluster create ${NODES} --cluster-replicas 1 --cluster-yes
for attempt in $(seq 1 60); do
  if redis-cli -h "${HOST_IP}" -p 7001 cluster info | grep -q 'cluster_state:ok'; then echo "cluster ok"; exit 0; fi
  sleep 1
done
echo "cluster did not become ok" >&2
exit 1
