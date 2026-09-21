// S3 - 기사 50명이 5초마다 위치 보고 (Redis GEO 쓰기 경로). TAXI_ENABLED=true 서버에서만 의미 있음.
// 기사는 seed 된 사용자 중 users[100..149] 를 기사로 등록해서 쓴다 (승객 시나리오와 안 겹치게).
//   k6 run -o experimental-prometheus-rw loadtest/s3-driver-location.js
import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import { BASE, auth, 강남역 } from './lib/api.js';

const users = new SharedArray('users', () => JSON.parse(open('./users.json')));
const DRIVERS = Number(__ENV.DRIVERS || 50);
const OFFSET = 100;

export const options = {
  scenarios: {
    report: {
      executor: 'constant-arrival-rate',
      rate: DRIVERS, timeUnit: '5s',                 // 기사당 5초에 1회
      duration: __ENV.DURATION || '3m',
      preAllocatedVUs: 20, maxVUs: 100,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:POST /dispatch/location}': ['p(95)<200'],
  },
};

export function setup() {
  for (let i = 0; i < DRIVERS; i++) {
    const t = users[OFFSET + i].token;
    http.post(`${BASE}/api/v1/drivers`, JSON.stringify({           // 이미 등록이면 409 - 무시
      qualificationNumber: `서울-${String(i).padStart(4, '0')}-0000`,
      bankName: '국민은행', accountNumber: `123-456-${String(i).padStart(6, '0')}`,
      vehicle: { seats: 4, plateNumber: `${String(i).padStart(2, '0')}가${String(1000 + i)}`, type: '중형' },
    }), auth(t));
    const on = http.post(`${BASE}/api/v1/dispatch/online`, JSON.stringify({ latitude: 강남역.lat, longitude: 강남역.lng }), auth(t));
    check(on, { '영업 시작': (r) => r.status < 300 });
  }
}

export default function () {
  const i = Math.floor(Math.random() * DRIVERS);
  const jitter = () => (Math.random() - 0.5) * 0.01;   // 강남 일대 ±500m
  const res = http.post(`${BASE}/api/v1/dispatch/location`,
    JSON.stringify({ latitude: 강남역.lat + jitter(), longitude: 강남역.lng + jitter() }),
    auth(users[OFFSET + i].token, { tags: { name: 'POST /dispatch/location' } }));
  check(res, { '보고 성공': (r) => r.status < 300 });
}

export function teardown() {
  for (let i = 0; i < DRIVERS; i++) http.del(`${BASE}/api/v1/dispatch/online`, null, auth(users[OFFSET + i].token));
}
