#!/bin/bash
# D9 한 번 실행 - k6 서버에서 root 로: bash loadtest/run-d9.sh <조건> [분]
#   조건 nodeploy | p1-all | p1-one | p2-all | p2-one   (p1 = 분리 전, p2 = 분리 후, all = AllAtOnce, one = OneAtATime)
#   매번 새 사용자 150명을 만든다(토큰 유효 30분이라 실행마다 새로 받는다). 끝나면 /root/d9-<조건>.out 에 요약이 남는다
#   백그라운드로 돌므로 SSM 세션이 끊겨도 측정은 계속된다. 진행은 tail -f /root/d9-<조건>.out
set -euo pipefail
T="d9-${1:?조건을 주세요 (nodeploy, p1-all, p1-one, p2-all, p2-one)}"
MIN="${2:-15}"
OUT="/root/$T.out"

cd "$(dirname "$0")"
export HOME=/root
export BASE_URL="${BASE_URL:-http://internal-moyeota-dev-alb-1803675880.ap-northeast-2.elb.amazonaws.com}"
export K6_PROMETHEUS_RW_SERVER_URL="${K6_PROMETHEUS_RW_SERVER_URL:-http://localhost:9090/api/v1/write}"
ulimit -n 65535
mkdir -p results

echo "사용자 150명 준비 중..."
USERS=150 RUN_ID="$(date +%s)" node seed.mjs 2>&1 | tr '\r' '\n' | grep -E 'users.json|실패' || true

nohup k6 run -o experimental-prometheus-rw --tag testid="$T" -e CONNS=150 -e DURATION_MIN="$MIN" \
  --summary-export="results/$T.json" d9-ws-deploy.js > "$OUT" 2>&1 &

echo "시작: $(date '+%H:%M:%S')  (${MIN}분, 방 50개 준비에 수십 초 더 걸림)"
echo "진행 보기: tail -f $OUT"
echo "배포 측정이면 출력에 'running' 이 보이고 3분 뒤 CodeDeploy 에서 moyeota-dev 재배포"
