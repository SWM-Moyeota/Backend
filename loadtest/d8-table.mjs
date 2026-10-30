// D8 의 --summary-export JSON 을 단계별 표로 바꾼다.
//   node d8-table.mjs results/d8-c300-r1.json results/d8-c1000-r1.json
import { readFileSync } from 'node:fs';

const files = process.argv.slice(2);
if (!files.length) { console.error('사용법: node d8-table.mjs <summary.json>...'); process.exit(1); }

const count = (metrics, key) => (metrics[key] ? metrics[key].count : 0);
const fmt = (v) => (v === undefined ? '-' : Number.isInteger(v) ? String(v) : v.toFixed(1));

for (const file of files) {
  const { metrics, setup_data: setup = {} } = JSON.parse(readFileSync(file, 'utf8'));
  const steps = Object.keys(metrics)
    .map((k) => /^d8_action\{step:(\d+(?:\.\d+)?),result:ok\}$/.exec(k))
    .filter(Boolean).map((m) => m[1]).sort((a, b) => Number(a) - Number(b));
  const p95 = (key) => (metrics[key] && metrics[key].max > 0 ? `${fmt(metrics[key]['p(95)'])}ms` : '-');
  const max = (key) => (metrics[key] && metrics[key].max > 0 ? `${fmt(metrics[key].max)}ms` : '-');
  console.log(`\n## ${file}`);
  console.log(`화면 일치 ${count(metrics, 'sse_view_check{result:match}')}, 불일치 ${['members', 'status', 'host', 'version'].map((r) => `${r}=${count(metrics, `sse_view_check{result:${r}}`)}`).join(' ')}, 대조 못 한 구간 ${count(metrics, 'sse_delivery_unverified')}, 재연결 ${count(metrics, 'sse_reconnects')}, SSE 오류 ${count(metrics, 'sse_errors')}, 밀린 입퇴장 ${count(metrics, 'dropped_iterations')}`);
  console.log(`받을 이벤트 ${count(metrics, 'sse_delivery_expected')}, 받은 이벤트 ${count(metrics, 'sse_delivery_received')}, 중간 미수신 ${count(metrics, 'sse_delivery_lost')}, 끝 구간 미수신 ${count(metrics, 'sse_delivery_tail')}, connected 전 도착 ${count(metrics, 'sse_before_connected')}`);
  if (count(metrics, 'sse_delivery_tail') > 0) console.log('끝 구간 미수신이 있다 - 마지막 단계는 판정을 보류한다');
  console.log(`수신 지연은 측정 주기 ${setup.probePeriodSec ?? '?'}초 미만만 잰다. 주기 건너뜀이 있으면 그 단계의 지연을 믿지 않는다`);
  console.log('| 목표(건/s) | 달성(건/s) | 겹쳐 거절 | 기타 실패 | 입퇴장 응답 p95 | 수신 지연 p95 | 수신 지연 최대 | 주기 건너뜀 | 보낸 시각 지연 p95 | 도착 순서 건너뜀(유실 아님) | 순서 역전 | 중간 미수신 |');
  console.log('|---|---|---|---|---|---|---|---|---|---|---|---|');
  for (const s of steps) {
    const ok = count(metrics, `d8_action{step:${s},result:ok}`);
    const achieved = setup.stepSec ? fmt(ok / setup.stepSec) : `${ok}건`;
    console.log(`| ${s} | ${achieved} | ${count(metrics, `d8_action{step:${s},result:skipped}`)} | ${count(metrics, `d8_action{step:${s},result:other}`)} | ${p95(`d8_action_ms{step:${s}}`)} | ${p95(`sse_event_latency_ms{step:${s}}`)} | ${max(`sse_event_latency_ms{step:${s}}`)} | ${count(metrics, `sse_probe_tick_skip{step:${s}}`)} | ${p95(`sse_probe_send_late_ms{step:${s}}`)} | ${count(metrics, `sse_arrival_gap{step:${s}}`)} | ${count(metrics, `sse_order_inversion{step:${s}}`)} | ${count(metrics, `sse_delivery_lost{step:${s}}`)} |`);
  }
}
