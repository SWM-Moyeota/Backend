# PR 제목

구글·애플 로그인 수단 연결과 애플 인증·철회 지원

# PR 본문

회원당 소셜 계정 하나만 저장하던 구조를 복수 로그인 수단으로 확장하고, Apple 네이티브 로그인과 등록 휴대폰 재인증 기반 Google/Apple 계정 연결을 추가합니다. 예를 들어 iOS에서 Apple로 가입한 회원이 Google을 추가하면 Android Google 로그인에서도 기존 회원 UUID를 사용합니다. 이메일이나 휴대폰이 같다는 이유로 계정을 병합하거나 이미 연결된 공급자 계정을 교체하지 않습니다.

기존 Google 로그인·휴대폰 인증 후 소셜 가입·로컬 로그인·재발급·탈퇴 API 계약을 유지합니다. 비밀번호 보유 회원은 소셜 수단을 추가해도 탈퇴 때 비밀번호 확인을 유지합니다. API 상세와 오류 코드는 [소셜 로그인·계정연결 문서](소셜-로그인-계정연결.md), 요청 예제는 `http/social-login-methods.http`에 있습니다.

변경 사항:

- 일회성 Apple nonce challenge, JWT 서명/issuer/audience/만료/nonce 검증, 인증 코드 교환과 subject 일치 검증.
- V43 데이터 보존 마이그레이션, 회원별 공급자와 공급자별 subject UNIQUE, ACTIVE/REVOKED 상태.
- 로그인 수단 조회와 연결 전용 SMS. 증표는 목적·회원·등록 번호에 묶인 5분 일회성 값이며 연결 실패 시 소진도 롤백.
- 사용자 행부터 잠그는 재발급·연결·탈퇴·로컬 로그인·비밀번호 재설정 순서.
- 기존 FieldCipher 기반 공급자 토큰 암호화, 가입 중단 정리, 영속 철회 outbox와 재시도. 새 동의가 존재하면 오래된 토큰을 원격 철회하지 않음.
- Apple 알림 서명·시간·중복 검증, 지연 알림 및 PENDING 인증 경합 차단, 해당 수단 비활성화와 기존 세션 무효화.
- Apple 알림의 `event_time` 밀리초 값과 `account-delete` 표기도 처리(공식 문서는 초 단위·`account-deleted`이나 다른 형태로 전달된다는 사례 보고에 대비. 실제 Apple 알림으로는 미검증).

검증(2026-11-15 재실행):

- `./gradlew :test :integrationTest :bootJar --console=plain`: 성공. 본체 1,826개 테스트 통과(실패·건너뜀 0), 실행 JAR 생성.
- `:integrationTest`: localhost:6379/5432 미준비로 기존 Gradle 조건에 따라 **건너뜀**. PostgreSQL 전용 V42→V43 데이터 보존/제약 테스트는 컴파일만 했고 **실행하지 못함**. V43은 실제 PostgreSQL에서 한 번도 적용해 보지 않았다.
- AppleClientTest는 로컬 HTTP 서버와 생성한 RSA/EC 테스트 키로 검증·교환·철회의 성공/실패를 검증.
- 실제 MVC/보안필터/H2 서비스 테스트는 가입→연결→동일 UUID 로그인→재발급→탈퇴, 다른 회원/공급자 계정 충돌, 동시 연결, 탈퇴/재발급 경합, 알림·세션 무효화 확인. 공급자·SMS·Redis 경계는 모의 처리.
- Postman 로컬 Apple 모의 서버 smoke(`node postman/check-apple-mock.mjs`) 통과. 모의 서버 자체 점검이며 백엔드를 연결한 종단 검증은 아님.
- socket-app 테스트는 이번에 실행하지 않음(11월 14일 기록: Redis 부재로 기존 테스트 8개 실패).
- `git diff --check` 통과.

리뷰어가 알아둘 점:

- 인증된 모든 요청이 `user_session_revocation`을 PK로 1회 조회한다(Apple 철회 알림 후 access token 즉시 무효화 목적). PrincipalCache로 줄였던 요청당 DB 조회가 하나 다시 생긴다.
- `POST /auth/apple/challenge`는 비로그인 호출마다 `social_proof`에 행을 넣는다. 만료 행은 1분 주기로 정리하지만 호출량 제한은 없다.
- Apple 코드 교환은 subject 잠금 트랜잭션 안에서 외부 HTTP(최대 10초)를 호출한다. 같은 Apple 계정의 교환·철회 직렬화를 위한 의도된 선택이다.

배포 전 조건과 미검증 범위:

- 사용자가 Apple Developer의 앱 기능·키·알림 URL과 `APPLE_CLIENT_IDS`, `APPLE_TEAM_ID`, `APPLE_KEY_ID`, `APPLE_PRIVATE_KEY`를 설정해야 함. 비밀키를 저장소에 추가하지 않음.
- 로컬 PostgreSQL/Redis가 준비되면 `./gradlew :integrationTest`로 실제 마이그레이션과 통합 suite를 실행해야 함. 기본 docker-compose의 로컬 DB만 대상으로 하는 테스트가 포함됨.
- 실제 Apple/Google 계정 SDK·실기기 교차 로그인은 콘솔 권한/실키가 없어 실행하지 않음.
- 운영 배포·PR 생성·push·병합을 수행하지 않음. 백엔드 배포 후 앱 PR을 적용하는 순서를 권장.

# 산출물

- 실행 JAR: `build/libs/app.jar` (빌드 산출물, 커밋하지 않음)
- 본체 테스트 보고서: `build/reports/tests/test/index.html`
- PostgreSQL 마이그레이션 테스트: `src/integrationTest/java/team/codingforest/moyeota/user/social/SocialLoginMigrationPostgresTest.java`
- root가 관리하는 Postman 모의 서버/시뮬레이션: `postman/`
