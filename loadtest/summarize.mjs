// k6 의 --summary-export JSON 을 터미널 끝에 찍히는 요약과 같은 형식의 텍스트로 바꾼다.
//   node summarize.mjs results/d2-sse-4000-r7070.json              # 화면에 출력
//   node summarize.mjs results/d2-sse-4000-r7070.json "2026-10-06 21:58 KST, VUS=4000 USE_SSE=1" > results/d2-sse-4000-r7070.txt
//   두 번째 인자(선택)는 첫 줄에 메모로 찍힌다 - JSON 에는 실행 시각·옵션이 없다
// JSON 은 기계용이라 사람이 읽기 어렵고, 터미널 요약은 세션이 끝나면 사라진다. 둘 다 results/ 에 남겨 실행 간 비교에 쓴다.
import { readFileSync } from 'node:fs';

const [path, note] = process.argv.slice(2);
if (!path) { console.error('사용법: node summarize.mjs <summary.json>'); process.exit(1); }
const { metrics, root_group: root } = JSON.parse(readFileSync(path, 'utf8'));

// ── 값 표시 ──
const DURATION = /^(http_req_|iteration_duration|ws_connecting|ws_session_duration|.*_ms$)/;   // 시간 지표 - ms 로 저장돼 있다
const isDuration = (name) => DURATION.test(name.replace(/\{.*\}$/, ''));
function ms(v) {
  if (v < 1) return `${(v * 1000).toFixed(2)}µs`;
  if (v < 1000) return `${v.toFixed(2)}ms`;
  if (v < 60_000) return `${(v / 1000).toFixed(2)}s`;
  return `${Math.floor(v / 60_000)}m${Math.round((v % 60_000) / 1000)}s`;
}
const bytes = (v) => (v >= 1e9 ? `${(v / 1e9).toFixed(1)} GB` : v >= 1e6 ? `${(v / 1e6).toFixed(0)} MB` : `${(v / 1e3).toFixed(0)} kB`);
const pct = (v) => `${(Math.floor(v * 10000) / 100).toFixed(2)}%`;   // k6 처럼 내림 - 99.999% 를 100.00% 로 보이지 않게
const num = (v) => (Number.isInteger(v) ? String(v) : v.toFixed(2));

function line(name, m) {
  if ('avg' in m) {                                                     // trend
    const f = isDuration(name) ? ms : num;
    return ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)'].filter((k) => k in m).map((k) => `${k}=${f(m[k])}`).join('  ');
  }
  if ('passes' in m) return `${pct(m.value)}  ${m.passes} out of ${m.passes + m.fails}`;   // rate
  if ('count' in m && 'rate' in m) {                                    // counter
    if (name.startsWith('data_')) return `${bytes(m.count)}  ${bytes(m.rate)}/s`;
    return `${m.count}  ${num(m.rate)}/s`;
  }
  if ('value' in m) return `${num(m.value)}  min=${num(m.min)}  max=${num(m.max)}`;        // gauge
  return JSON.stringify(m);
}

function thresholdValue(name, m, expr) {
  const stat = expr.replace(/[<>=!].*$/, '').trim();
  if (stat === 'rate') return `rate=${pct(m.value)}`;
  if (stat === 'count') return `count=${m.count}`;
  if (stat in m) return `${stat}=${isDuration(name) ? ms(m[stat]) : num(m[stat])}`;
  return expr;
}

const out = [];
if (note) out.push(`# ${note}`, '');
const pad = (s, n) => s + ' '.repeat(Math.max(1, n - [...s].length));

// ── THRESHOLDS ──
const thresholds = Object.entries(metrics).filter(([, m]) => m.thresholds).sort();
if (thresholds.length) {
  out.push('█ THRESHOLDS');
  for (const [name, m] of thresholds) {
    for (const [expr, crossed] of Object.entries(m.thresholds)) {   // --summary-export 는 true 가 "임계값을 넘었다(실패)" 다
      out.push(`  ${pad(name, 42)} ${crossed ? '✗' : '✓'} '${expr}'  ${thresholdValue(name, m, expr)}`);
    }
  }
  out.push('');
}

// ── TOTAL RESULTS ──
out.push('█ TOTAL RESULTS');
if (metrics.checks) {
  const c = metrics.checks;
  out.push(`  checks_total.......: ${c.passes + c.fails}`);
  out.push(`  checks_succeeded...: ${pct(c.value)}  ${c.passes} out of ${c.passes + c.fails}`);
  out.push(`  checks_failed......: ${pct(1 - c.value)}  ${c.fails} out of ${c.passes + c.fails}`);
  for (const [name, ch] of Object.entries(root.checks || {})) {
    out.push(`  ${ch.fails === 0 ? '✓' : '✗'} ${name}`);
    if (ch.fails > 0) out.push(`    ↳ ${Math.floor((ch.passes / (ch.passes + ch.fails)) * 100)}% — ✓ ${ch.passes} / ✗ ${ch.fails}`);
  }
  out.push('');
}

const BUILTIN = {
  HTTP: ['http_req_duration', 'http_req_failed', 'http_reqs', 'http_req_blocked', 'http_req_connecting', 'http_req_tls_handshaking', 'http_req_sending', 'http_req_waiting', 'http_req_receiving'],
  EXECUTION: ['iteration_duration', 'iterations', 'vus', 'vus_max'],
  NETWORK: ['data_received', 'data_sent'],
  WEBSOCKET: ['ws_connecting', 'ws_errors', 'ws_msgs_received', 'ws_msgs_sent', 'ws_session_duration', 'ws_sessions'],
};
const builtin = new Set([...Object.values(BUILTIN).flat(), 'checks']);
const base = (n) => n.replace(/\{.*\}$/, '');
const section = (title, names) => {
  const rows = names.filter((n) => metrics[n]);
  if (!rows.length) return;
  out.push(`  ${title}`);
  for (const n of rows) {
    out.push(`  ${pad(n, 34)} ${line(n, metrics[n])}`);
    for (const sub of Object.keys(metrics).filter((k) => k.startsWith(`${n}{`)).sort()) {   // http_req_duration{expected_response:true} 같은 하위 지표
      out.push(`    ${pad(sub.slice(n.length), 32)} ${line(sub, metrics[sub])}`);
    }
  }
  out.push('');
};
section('CUSTOM', Object.keys(metrics).filter((n) => !builtin.has(base(n)) && !n.includes('{')).sort());
for (const [title, names] of Object.entries(BUILTIN)) section(title, names);

console.log(out.join('\n'));
