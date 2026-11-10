#!/bin/bash
# 채팅 순서 측정(D4) 한 번 실행 - k6 서버에서 root 로: bash loadtest/run-order.sh <이름> [CONNS] [SEND_EVERY_SEC]
#   예) bash loadtest/run-order.sh baseline        → testid d4-order-baseline, 연결 300, 1초마다 보내기
#   연결은 1/3 → 2/3 → 전체로 올라간다(d4 단계, 약 11분 30초). 단계마다 역전이 부하에 따라 어떻게 변하는지 본다
#   SEQ_GAP_WAIT_MS 를 길게(2초) 줘서 빈 번호 대기 시간을 자르지 않고 전부 본다 - 서버 정렬(E2)의 대기 상한을 정하는 근거
#   백그라운드로 돈다. 진행 보기: tail -f /root/d4-order-<이름>.out
set -euo pipefail
T="d4-order-${1:?이름을 주세요 (예: baseline)}"
CONNS="${2:-300}"
SEND="${3:-1}"
OUT="/root/$T.out"

cd "$(dirname "$0")"
export HOME=/root
export BASE_URL="${BASE_URL:-http://internal-moyeota-dev-alb-1803675880.ap-northeast-2.elb.amazonaws.com}"
export K6_PROMETHEUS_RW_SERVER_URL="${K6_PROMETHEUS_RW_SERVER_URL:-http://localhost:9090/api/v1/write}"
ulimit -n 65535
mkdir -p results

echo "사용자 ${CONNS}명 준비 중..."
USERS="$CONNS" RUN_ID="$(date +%s)" node seed.mjs 2>&1 | tr '\r' '\n' | grep -E 'users.json 저장|Error' || true

nohup k6 run -o experimental-prometheus-rw --tag testid="$T" -e CONNS="$CONNS" -e SEND_EVERY_SEC="$SEND" -e SEQ_GAP_WAIT_MS=2000 \
  --summary-export="results/$T.json" d4-chat-ws.js > "$OUT" 2>&1 &

echo "시작: $(date '+%H:%M:%S')  (연결 ${CONNS}, ${SEND}초마다 보내기, 약 12분)"
echo "진행 보기: tail -f $OUT"
