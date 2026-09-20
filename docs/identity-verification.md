# 포트원 본인인증

## 적용 범위

로그인한 계정에 포트원 V2 휴대폰 본인인증 결과를 연결한다. KCP 운영 채널을 기준으로 연동하고, DI를 제공하는 다날 운영 채널도 동일한 API 계약으로 사용할 수 있다. DI가 없는 통합인증은 현재 정책에서 허용하지 않는다.

매칭방 생성과 참가 시 `IdentityAccess.requireVerified`를 호출한다. 미인증 계정은 `403 IDENTITY001`을 받는다. 조회·퇴장·기존 방 종료는 허용한다. 기존 회원도 자동으로 인증 처리하지 않는다. 별도 우회 플래그는 없다. 기사 자격 검증·금융 실명확인·실제 탑승자 확인을 대신하지 않는다.

가입 시 직접 입력한 `user_profile`과 검증 기록을 분리한다. 기존 이름·전화번호·생년월일을 검증된 값으로 간주하거나 덮어쓰지 않는다. 인증 결과의 이름·전화번호·CI·DI 원문은 저장하지 않는다. `verified_identity`에는 요청 ID, 인증 시각, 반영 시각, DI의 HMAC-SHA256만 저장한다. 본인인증 API는 개인정보를 반환하지 않는다.

## 운영 설정

| 환경변수 | 용도 |
| --- | --- |
| `PORTONE_SECRET` | 서버 전용 V2 API Secret |
| `PORTONE_STORE_ID` | 상점 ID |
| `PORTONE_IDENTITY_CHANNEL_KEY` | 계약 완료된 휴대폰 본인인증 운영 채널 키 |
| `PORTONE_DI_HASH_KEY` | DI HMAC 전용 비밀키. 무작위 생성한 32바이트 이상의 문자열 |

V8 Flyway 마이그레이션을 적용한다. 기본 요청 유효 시간은 `portone.identity.request-validity: 10m`이다. 설정이 누락되면 시작/미완료 요청 처리는 `503 IDENTITY009`로 실패하며 인증 권한이 생기지 않는다. `channel.type=LIVE`만 허용하므로 테스트 채널을 이용한 가짜 인증으로 권한을 얻을 수 없다. 포트원 계약·운영 채널 등록·프론트 SDK 연동은 별도로 필요하다.

HMAC 키를 저장소나 클라이언트에 넣지 않는다. 여러 인스턴스에서 동일한 키를 사용한다. 키를 임의 교체하면 기존 중복 확인이 깨지므로 키 교체와 제공사/사이트 변경은 기존 식별값과의 이행 정책을 먼저 수립해야 한다. DI는 사이트 단위 식별값이므로 다른 제공사·사이트 간 동일인 판별을 자동 보장하지 않는다. 현재 정책은 동일 DI에 한 계정만 허용하며 계정 연결 변경·탈퇴 후 재가입 정책은 별도 구현이 필요하다.

## 프론트 연동 계약

모든 API는 `Authorization: Bearer <accessToken>`이 필요하다.

1. `POST /api/v1/users/me/identity-verifications`로 요청을 생성한다. 유효 시간 안의 반복 시작은 같은 요청을 반환한다.
2. 응답의 `identityVerificationId`, `storeId`, `channelKey`를 V2 SDK에 넘긴다. 만료 시각은 `expiresAt`으로 표시한다.
3. SDK 완료 또는 모바일 redirect 이후 `POST /api/v1/users/me/identity-verifications/{identityVerificationId}/complete`를 호출한다. 이름·DI·성공 여부는 보내지 않는다.
4. 서버의 `{ "verified": true, "verifiedAt": "..." }`를 확인한다. `GET /api/v1/users/me/identity-verifications`로도 상태를 조회할 수 있다.

```javascript
// @portone/browser-sdk/v2. api는 Bearer 토큰과 HTTP 오류 처리를 포함하는 프론트 HTTP 클라이언트.
const request = await api.post('/api/v1/users/me/identity-verifications');
const result = await PortOne.requestIdentityVerification({
  storeId: request.storeId,
  channelKey: request.channelKey,
  identityVerificationId: request.identityVerificationId,
  redirectUrl: `${location.origin}/identity-verification/return`,
});
if (result && result.code === undefined) {
  await api.post(`/api/v1/users/me/identity-verifications/${request.identityVerificationId}/complete`);
}
// redirect 방식에서는 위 반환 코드가 실행되지 않을 수 있다.
// 복귀 페이지에서도 로그인 세션을 복원한 뒤 서버의 complete API를 호출한다.
// URL의 성공 여부는 신뢰하지 않는다. 요청 소유자는 서버가 검증한다.
```

## 검증과 복구

- 서버가 생성한 UUID 요청 ID를 사용자와 연결한다. 다른 계정의 요청과 없는 요청은 모두 `404 IDENTITY002`로 처리하고 외부 조회도 하지 않는다.
- 포트원 조회에 상점 ID와 서버 Secret을 사용한다. 응답의 요청 ID, `VERIFIED`, V2, 운영 채널과 채널 키, DI 존재 여부, 인증 완료 시각을 검증한다.
- 인증 완료 시각은 `[요청 생성 시각, 만료 시각)` 안에 있어야 하며 미래 시각은 허용하지 않는다. 서버 시각은 동기화되어 있어야 한다.
- 서버 처리 시각이 만료 후라도 외부 인증이 기간 안에 끝났다면 같은 요청으로 복구한다. 새 요청을 만든 후에도 이전 요청의 유효한 인증 결과는 복구 가능하다. 계정의 첫 완료 결과만 연결하고 다른 요청의 덮어쓰기는 막는다.
- 포트원 조회는 DB 트랜잭션 밖에서 수행한다. 연결 3초·응답 5초 제한을 적용하며 외부 오류 원문은 로그/응답에 포함하지 않는다.
- DB 저장 중에만 사용자 행을 잠근다. 같은 요청의 동시 완료는 하나의 결과를 반환한다. 다른 계정의 동일 DI는 unique 제약조건으로 최종 차단한다. 인증 기록과 권한은 같은 저장 결과를 사용한다.
- 조회 장애는 `503 IDENTITY008`이며 인증을 부여하지 않는다. 완료 요청을 제한된 횟수로 지수 백오프 재시도한다. 자동 배치 복구는 없고 앱의 동일 요청 재호출로 복구한다.
- 오류 본문·이름·전화번호·CI·DI는 로그에 기록하지 않는다. 요청 ID와 고정 오류 코드로 추적한다. 요청 테이블과 완료 테이블에 생성·인증·반영 시각이 남는다. 운영 로그 접근 및 보관 정책은 배포 환경에서 설정한다.

| 오류 | 처리 |
| --- | --- |
| `IDENTITY001` | 본인인증을 먼저 진행 |
| `IDENTITY002` | 현재 로그인 계정의 요청인지 확인 |
| `IDENTITY003` | 아직 미완료/실패/외부 미생성. 인증 진행 상태 확인 |
| `IDENTITY004` | 채널·버전·시각·필수 반환값 검증 실패. 운영 설정 확인 |
| `IDENTITY005` | 인증이 제한 시간 후 완료됨. 새 요청으로 인증 |
| `IDENTITY006` | 계정이 다른 요청으로 이미 인증됨. 상태 조회 |
| `IDENTITY007` | 동일 신원이 다른 계정에 연결됨 |
| `IDENTITY008` | 외부 조회 장애. 동일 요청으로 재시도 |
| `IDENTITY009` | 서버 운영 설정 미완료 |

## 재현 테스트와 면접 설명

`IdentityVerificationServiceTest`: 위조/누락/테스트 채널/기간 초과 결과 차단, 외부 장애 재시도, 만료 후 복구.

`IdentityVerificationPersistenceTest`: 실제 JPA 트랜잭션과 병렬 스레드로 동시 시작·완료, 동일 신원의 계정 간 충돌, 저장 롤백 후 재시도, 외부 조회 중 트랜잭션 미유지를 검증한다. 기본 실행은 H2이며 PostgreSQL 검증은 별도 프로필 실행을 이용한다.

`PortOneIdentityClientTest`: HTTP 인증 헤더·상점 범위·응답 파싱, 404·외부 장애·타임아웃·비정상 JSON을 검증한다.

`IdentityVerificationControllerTest`: 실제 JWT 필터를 통과하며 익명 접근 차단, 계정 소유권, 미인증 매칭 참가 차단을 검증한다.

테스트로 재현한 장애를 운영 장애 경험으로 표현하지 않는다. 실제 포트원 계약 계정으로 완료한 수동 검증은 별도 기록한다.

```sh
# JPA/단위/모듈 경계 검증
./gradlew test --tests '*IdentityVerificationServiceTest' --tests '*IdentityVerificationPersistenceTest' --tests '*PortOneIdentityClientTest' --tests '*PartyApplicationServiceTest' --tests '*ModularityTest'
# 전체 검증: 로컬 Redis/PostgreSQL 필요
docker compose up -d redis postgres
./gradlew test
```

참고: [포트원 V2 본인인증 가이드](https://developers.portone.io/opi/ko/extra/identity-verification/readme-v2), [본인인증 REST API](https://developers.portone.io/api/rest-v2/identityVerification).
