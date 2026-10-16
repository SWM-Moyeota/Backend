# SDK 카드 등록 테스트 화면

이 저장소의 백엔드 API와 연결하는 로컬 웹 화면이다. 실제 카드 입력 창은 포트원 SDK가 PG에 맞게 연다. 운영 앱의 로그인 화면을 대체하지 않는다.

## 실행

로컬 테스트 회원이 없다면 Redis를 켜고 `./gradlew bootRun --args='--spring.profiles.active=test-member'`로 백엔드를 실행한다. IntelliJ에서는 실행 구성의 활성 프로필에 `test-member`를 지정한다. 이 프로필은 H2 메모리 DB에서만 `portonetest` / `PortoneTest1!` 회원을 생성한다. 일반 회원가입의 휴대폰 인증은 그대로 유지된다. `http/auth.http`에서 로그인하여 accessToken을 복사한다. H2 데이터는 프로세스 종료 시 사라지지만 이 프로필로 다시 실행하면 테스트 회원이 재생성된다. dev/prod에서는 초기화 코드가 실행되지 않는다.

1. DB·Redis와 백엔드를 실행한다. 백엔드에는 포트원 API Secret이 설정되어 있어야 한다.
2. 저장소 루트에서 `python3 http/card-registration/serve.py`를 실행한다. 백엔드 포트가 다르면 `--backend-port 8081`처럼 지정한다.
3. `http://127.0.0.1:8090`에 접속한다. 파일을 직접 열거나 다른 호스트명으로 접속하지 않는다.
4. 우리 서비스 로그인 응답의 **액세스 토큰**을 입력한다. 상점 ID와 채널 키는 `app.js` 상단의 `PORTONE_CONFIG`에 지정되어 자동으로 사용된다. 상점이나 채널을 바꾸려면 이 설정을 수정한다. 이 화면은 YAML 설정을 읽지 않는다. API Secret/웹훅 시크릿은 프론트 설정에 넣지 않는다.
5. 로그인 사용자 확인을 누른다. `/api/v1/local/users/billing-customer` 응답의 `uuid`가 SDK의 `customer.customerId`에 사용된다.
6. 카드 등록 창을 연다. 이름·전화번호·이메일은 로그인 회원의 DB 프로필에서 조회하여 자동 전달한다. 프로필이 없거나 정보가 누락되면 진행하지 않는다. PG별 지원 방식·필수 항목·등록 도메인 설정에 따라 실제 창 동작이 달라질 수 있다. 테스트 채널 사용을 권장한다.
7. SDK 발급 성공 후 `{ "billingKey": "…" }`만 우리 서버의 `POST /api/v1/payments/methods`로 전달한다. 서버가 포트원에서 검증하고 저장한 카드 정보를 화면에 표시한다.

로그인 토큰과 빌링키는 메모리에만 보관한다. 리디렉션 요청 확인을 위한 일회성 값과 사용자 ID만 sessionStorage에 보관한다. 리디렉션으로 돌아오면 같은 계정의 토큰을 다시 입력한다. 발급 후 저장 실패 시 페이지를 유지하고 **저장 재시도**를 사용한다. 새로고침하면 메모리의 빌링키가 사라지므로 발급된 키의 정리가 필요할 수 있다.

기존 카드가 있으면 서버 정책에 따라 교체된다. 이 화면에서는 결제 요청을 보내지 않는다. 수동 승인 빌링키는 지원하지 않는다. 모바일 실기기는 로컬 PC의 127.0.0.1에 접근할 수 없으므로 이 서버는 PC 로컬 테스트 용도이며, 실제 앱에서는 HTTPS 호스트와 리디렉션/딥링크를 별도로 연결해야 한다.

`serve.py`는 화면 파일 두 개와 API 경로 두 개만 제공하는 로컬 프록시다. 백엔드의 인증/CORS 설정을 바꾸지 않고 같은 출처에서 테스트하기 위해 사용한다. 운영 배포용 서버가 아니다.

공식 문서: [SDK 로드](https://developers.portone.io/sdk/ko/v2-sdk/readme), [빌링키 발급 요청](https://developers.portone.io/sdk/ko/v2-sdk/billing-key-request), [응답](https://developers.portone.io/sdk/ko/v2-sdk/billing-key-response).
