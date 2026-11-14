# 소셜 인증 로컬 시뮬레이션

Apple 계정·키 없이 공개키 조회, 코드 교환, 철회 HTTP를 검증하는 로컬 모의 서버다. 실제 Apple 계정 인증 성공을 증명하지 않는다. Postman 로그인 세션이 없어 소유자 workspace mock 검색은 생략하고 공식 계약으로 작성했다.

```sh
postman simulation run postman/simulations/social-auth.sim.yaml --port 4517 --no-history --output ndjson
```

서버의 `moyeota.apple.base-url`을 `http://127.0.0.1:4517/apple`로 설정한다. 개발자 client secret 생성에는 로컬 테스트용 EC 키를 사용한다. 운영 키나 토큰을 테스트에 사용하지 않는다.

- `/apple/__test/identity`: 서버 challenge의 `nonce`를 받아 테스트 identityToken/authorizationCode 생성. Apple 실제 API가 아닌 로컬 테스트 제어 경로다.
- `/apple/__test/mode`: `healthy`, `unavailable`, `invalid-grant`, `revoke-unavailable` 중 선택.
- `/apple/__test/stats`: 원문 토큰 없이 호출 횟수와 활성 모의 토큰 수만 확인.
- 기타 구현하지 않은 경로는 `501`로 명확하게 실패한다.

서명 검증은 실제 RSA 서명을 사용하지만 모의 token endpoint는 developer client_secret의 서명을 검증하지 않는다. 실제 공급자 검증, 서명 권한, 사용자 동의 화면, 기기 인증은 별도 실기 검증이 필요하다.

모의 서버를 띄운 뒤 `node postman/check-apple-mock.mjs`로 모의 서버 자체를 점검한다(2026-11-15 통과). 실행 중인 백엔드를 이 모의 서버에 연결한 종단 검증은 아직 하지 않았다.

결과는 `simulations/social-auth.context.yaml`에 실제 실행 근거와 한계를 기록한다. 이 폴더의 mock을 공개 서버에 배포하지 않는다.
