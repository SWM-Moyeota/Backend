#!/usr/bin/env bash
# k6 시나리오를 Prometheus remote write 와 함께 실행한다.
#   loadtest/scripts/run.sh <scenario> [profile] [k6 옵션...]
#   loadtest/scripts/run.sh matching smoke
#   loadtest/scripts/run.sh ride-e2e load
#   BASE_URL=http://10.0.1.23:8080 loadtest/scripts/run.sh mixed stress -e PARTY_CAPACITY=3
# scenario: matching | ride-e2e | dispatch-race | driver-location | mixed | seed
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
K6_DIR="$HERE/../k6"
RESULTS="$HERE/../results"
mkdir -p "$RESULTS"

SCENARIO=${1:?"시나리오 이름 필요: matching | ride-e2e | dispatch-race | driver-location | mixed | seed"}
PROFILE=${2:-smoke}
shift $(( $# >= 2 ? 2 : $# ))

if [[ "$SCENARIO" == "seed" ]]; then
  SCRIPT="$K6_DIR/seed.js"
else
  SCRIPT="$K6_DIR/scenarios/$SCENARIO.js"
fi
[[ -f "$SCRIPT" ]] || { echo "없는 시나리오: $SCRIPT"; exit 1; }

TEST_ID=${TEST_ID:-"$SCENARIO-$PROFILE-$(date +%Y%m%d-%H%M)"}
PROM_URL=${PROM_URL:-http://localhost:9090}

export K6_PROMETHEUS_RW_SERVER_URL="$PROM_URL/api/v1/write"
export K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99),avg,max"
export K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM=false

echo "[run] scenario=$SCENARIO profile=$PROFILE testid=$TEST_ID base=${BASE_URL:-http://localhost:8080}"
k6 run \
  -o experimental-prometheus-rw \
  -e PROFILE="$PROFILE" \
  -e TEST_ID="$TEST_ID" \
  ${BASE_URL:+-e BASE_URL="$BASE_URL"} \
  --summary-export "$RESULTS/$TEST_ID.json" \
  "$@" \
  "$SCRIPT" | tee "$RESULTS/$TEST_ID.log"

echo "[run] 결과: $RESULTS/$TEST_ID.json  Grafana: http://localhost:${GRAFANA_PORT:-3000} (testid=$TEST_ID)"
