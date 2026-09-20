// 모든 시나리오가 공유하는 설정. 환경변수로 덮어쓴다.
//   BASE_URL   대상 서버 (기본 http://localhost:8080)
//   PROFILE    smoke | load | stress   (docs/loadtest/scenarios.md 의 3단계 부하 모델)
//   TARGET_VUS load 단계의 목표 VU. stress 는 이 값의 2배, 4배까지 계단식으로 올린다
//   TEST_ID    Grafana 에서 회차를 구분하는 태그. 비우면 시나리오-프로파일-시각 으로 자동 생성
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const PROFILE = __ENV.PROFILE || 'smoke';
export const PASSWORD = 'LoadTest1!';

// 시딩된 계정 수 (seed.js 와 같은 값이어야 한다)
export const PASSENGER_POOL = Number(__ENV.PASSENGERS || 1000);
export const DRIVER_POOL = Number(__ENV.DRIVERS || 600);

export function passengerLoginId(i) { return `lt_p_${String(i).padStart(6, '0')}`; }
export function driverLoginId(i) { return `lt_d_${String(i).padStart(6, '0')}`; }

export function testId(scenario) {
  return __ENV.TEST_ID || `${scenario}-${PROFILE}-${new Date().toISOString().slice(0, 16).replace(/[-:T]/g, '')}`;
}

/** ramping-vus 실행기. target 은 load 단계 기준 VU 수 */
export function rampingVus(target, exec) {
  const t = Number(__ENV.TARGET_VUS || target);
  const base = { executor: 'ramping-vus', startVUs: 0, gracefulRampDown: '30s' };
  if (exec) base.exec = exec;
  switch (PROFILE) {
    case 'smoke':
      return { ...base, stages: [{ duration: '1m', target: Math.min(3, t) }] };
    case 'load':
      return { ...base, stages: [
        { duration: '2m', target: t },
        { duration: '10m', target: t },
        { duration: '1m', target: 0 },
      ] };
    case 'stress':
      return { ...base, stages: [
        { duration: '2m', target: t },
        { duration: '4m', target: t },
        { duration: '2m', target: t * 2 },
        { duration: '4m', target: t * 2 },
        { duration: '2m', target: t * 4 },
        { duration: '4m', target: t * 4 },
        { duration: '2m', target: 0 },
      ] };
    default:
      throw new Error(`알 수 없는 PROFILE=${PROFILE} (smoke|load|stress)`);
  }
}

/** 시나리오가 요구하는 최대 VU 수 - setup 에서 로그인할 계정 수 계산용 */
export function maxVus(target) {
  const t = Number(__ENV.TARGET_VUS || target);
  switch (PROFILE) {
    case 'smoke': return Math.min(3, t);
    case 'load': return t;
    case 'stress': return t * 4;
    default: return t;
  }
}

/** constant-arrival-rate 실행기. ratePerSec 은 load 단계 기준 초당 요청 수 */
export function arrivalRate(ratePerSec, exec) {
  const r = Number(__ENV.TARGET_RATE || ratePerSec);
  const mult = PROFILE === 'stress' ? 4 : PROFILE === 'smoke' ? 0.05 : 1;
  const rate = Math.max(1, Math.round(r * mult));
  const duration = PROFILE === 'smoke' ? '1m' : PROFILE === 'load' ? '12m' : '20m';
  const base = {
    executor: 'constant-arrival-rate',
    rate, timeUnit: '1s', duration,
    preAllocatedVUs: Math.min(500, rate),
    maxVUs: Math.min(1000, rate * 2),
  };
  if (exec) base.exec = exec;
  return base;
}

export const BASE_THRESHOLDS = {
  http_req_failed: ['rate<0.01'],
  http_req_duration: ['p(95)<500'],
};
