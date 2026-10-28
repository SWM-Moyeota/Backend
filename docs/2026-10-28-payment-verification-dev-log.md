# 사전결제·취소 검증 기능 개발 기록 — 2026-10-28

## 문서의 목적과 기록 기준

2026-10-28부터 진행하는 결제 검증 기능의 논의, 판단 변경, 구현 및 검증 결과를 기록한다.

- 기준 계획: [payment-verification-plan.md](payment-verification-plan.md)
- 이전 이력: [2026-10-27 개발 기록](2026-10-27-payment-verification-dev-log.md)
- 27일까지의 기록은 이전 파일에 보존하고, 오늘부터의 작업은 이 문서에 추가한다.
- 계획은 최신 설계와 진행 상태를 유지하고, 개발 기록은 판단의 변화와 이유를 남긴다.
- 사용자의 요청과 어시스턴트의 제안을 구분하고, 제안·미결정·확정·구현·검증 상태를 명시한다.
- 의미 있는 작업 단위마다 변경 파일, 관련 계획 번호, 실제 검증 결과와 남은 질문을 기록한다. 과거 판단을 새 판단으로 덮어쓰지 않는다.
- 질문이나 설계 논의를 코드 변경 요청으로 확대하지 않는다. 코드 변경은 사용자가 명시적으로 요청한 범위에서 진행한다.

## 이전 기록에서 이어받은 상태

아래는 기존 계획과 27일 기록을 기준으로 한 인계 사항이다. 오늘 소스 구현이나 실행 결과를 새로 검증한 내용은 아니다.

- 컨트롤러와 응답 DTO를 유지하고, 서비스 세부 구현은 사용자가 직접 작성하는 방식으로 진행한다.
- 기존 계획의 다음 논의는 **08번 후속 설계: `payment_log`를 어떤 사건마다 생성할지 정리**다.
- 기사 배정 이벤트 연동은 계획 **12-1~12-5**에 남아 있으며, 기존 미결정 사항과 체크 상태를 그대로 이어간다.

## 2026-10-28 — 개발 기록을 날짜별 파일로 분리

### 사용자의 요청과 결정

- 어제까지의 결제 검증 개발 이력은 기존 `2026-10-27-payment-verification-dev-log.md`에 보존한다.
- 오늘부터는 `2026-10-28-payment-verification-dev-log.md`를 만들어 기록한다.
- 개발 시 `payment-verification-plan.md`를 계속 참조한다.

### 변경한 내용

- 오늘의 개발 기록 파일을 생성하고 기존 기록 규칙과 다음 논의 항목을 연결했다.
- 계획 문서에 날짜별 기록 안내를 추가했다.
- 계획의 기존 `payment-verification-dev-log.md` 링크를 수정했다. 앞으로의 기록은 28일 파일로, 과거 ERD 자료는 27일 파일로 연결했다.
- 27일 기록의 본문과 애플리케이션 코드는 변경하지 않았다. 기능의 설계나 완료 상태를 새로 확정하지 않았다.

### 검증 결과

- 계획과 개발 기록 사이의 로컬 링크 대상 및 문서 공백 오류를 확인했다.
- 문서 정리 작업이므로 빌드·자동 테스트·외부 결제 호출은 실행하지 않았다.

### 다음 작업

사용자의 후속 요청에 따라 기존 계획을 참조하여 작은 단위로 논의·개발하고, 그 과정과 결과를 이 문서에 누적한다.

## 2026-10-28 — 서비스 중간 결과와 프론트 응답의 역할 이해

### 사용자의 고민과 확인 과정

- `PaymentResultResponse`에 `paymentId`, `pgPaymentId`, `userId`, `amount`, `status`를 둔 이유를 질문했다.
- 이어서 `PaymentVerificationResponse(Long paymentId, boolean verified)`의 구조가 필요한 이유를 질문했다.
- 최종적으로 컨트롤러가 반환하는 값은 프론트에서 필요한 정보라는 점을 확인했다.

### 설명한 내용과 아직 확정하지 않은 제안

- `PaymentResultResponse`는 `prePay()`가 컨트롤러에 전달하는 중간 결과다. 컨트롤러는 내부 `paymentId`를 다음 취소 단계에 전달한다.
- `PaymentVerificationResponse`는 컨트롤러가 프론트에 반환하는 최종 응답이다. 현재 의도는 승인과 전액 취소까지 확인된 검증 결과를 전달하는 것이다.
- `record`는 결과 데이터를 불변 객체로 전달하기 위한 선택이다.
- 어시스턴트는 현재 컨트롤러에서 중간 결과의 `paymentId`만 사용하므로 나머지 필드를 줄일 수 있다고 설명했다. 성공 시에만 최종 결과를 반환한다면 `verified`도 항상 true이므로 생략할 수 있다고 제안했다.
- 최종 응답의 `paymentId` 필요성은 프론트 사용 목적에 따라 판단하며, 처리 중 상태까지 반환하려면 boolean만으로 충분하지 않을 수 있다고 설명했다.
- **필드 축소, 본문 없는 성공 응답, 별도 검증 상태 도입은 모두 제안이다. 사용자가 변경을 확정하거나 코드 수정을 요청한 것은 아니다.**

### 관련 계획과 다음 작업

- 관련 작업: **07번 서비스 입출력**, **23번 컨트롤러 연결**, **24번 최종 응답 DTO**.
- 프론트가 검증 결과로 어떤 화면·후속 동작을 처리하는지 확인한 후 최종 응답 필드를 결정한다. 기존 체크 항목을 새로 완료 처리하지 않는다.

## 2026-10-28 — IntelliJ에서 직접 작성하는 코드의 추적 요청

### 사용자의 요청과 추적 방식

사용자는 IntelliJ에서 직접 코드 작업 중이며, 어떤 작업을 어떻게 진행하는지도 개발 기록에 남겨 달라고 요청했다.

- 후속 대화와 작업 시 저장소에 저장된 관련 코드 및 Git 변경 사항을 확인하고, 이전에 확인한 상태와의 차이를 기록한다.
- 사용자가 설명한 의도, 코드에서 확인한 사실, 어시스턴트의 해석을 구분한다. 변경 시각이나 작성자가 확인되지 않는 코드를 오늘 사용자가 새로 작성했다고 단정하지 않는다.
- Git 차이는 커밋·스테이징 상태와의 차이이며, 그 자체로 오늘 작업한 범위를 뜻하지 않는다.
- IntelliJ의 저장되지 않은 편집 내용이나 편집 순서·키 입력은 현재 저장소 확인으로 알 수 없다. 상시 백그라운드 감시를 설정한 것은 아니며, 대화 중 확인한 시점의 상태를 기록한다.

### 이번에 확인한 저장된 코드

| 파일 | 확인한 상태 |
|---|---|
| `src/main/java/team/codingforest/moyeota/payment/controller/PaymentController.java` | `prePay(userId, partyId)` → `cancelPrepayment(userId, payment.paymentId())` → `ResponseEntity.ok(verification)` 순서로 연결되어 있다. |
| `src/main/java/team/codingforest/moyeota/payment/dto/PaymentResultResponse.java` | 기존 5개 필드를 유지한다. 필드 축소 제안은 적용되지 않았다. |
| `src/main/java/team/codingforest/moyeota/payment/dto/PaymentVerificationResponse.java` | `paymentId`, `verified` 두 필드를 유지한다. |
| `src/main/java/team/codingforest/moyeota/payment/service/PaymentService.java` | 빈 클래스다. 컨트롤러가 호출하는 두 메서드는 아직 선언되지 않았다. |

Git 상태에는 결제수단, 포트원 클라이언트·DTO, 결제 엔티티·enum 및 기존 테스트 등의 변경도 있다. 이번에는 위 네 파일의 내용을 확인했으며, 나머지 변경을 이번 세션의 신규 구현이나 검증 완료로 분류하지 않았다.

### 변경 및 검증 범위

- 오늘 개발 기록에 DTO 논의와 저장된 코드 상태, 추적 규칙을 추가했다.
- 애플리케이션 코드는 수정하지 않았다. 빌드·테스트·실제 결제 호출은 실행하지 않았다.
- 컨트롤러 호출에 대응하는 서비스 메서드가 없는 상태를 소스에서 확인했다. 실제 컴파일 실행 결과는 아니다.

## 2026-10-28 — 다섯 결제 테이블의 엔티티·레포지터리 작성

### 사용자의 요청과 최신 명세

사용자는 아래 다섯 테이블에 해당하는 엔티티와 레포지터리 작성을 명시적으로 요청했다. 기존 네 테이블 범위에 정산 테이블이 추가됐다.

| 테이블 | 제공된 컬럼과 타입 |
|---|---|
| `payment` | `id` Long PK, `pg_payment_id` VarChar, `user_id` Long, `payment_group_id` Long, `payment_method_id` Long, `amount` Integer, `status` ENUM, `type` ENUM(VERIFICATION, MAIN), `order_name` VarChar, `currency` ENUM(KRW, USD, JPY), `fail_code` VarChar, `created_at`·`updated_at` LocalDateTime |
| `payment_log` | `id` Long PK, `payment_id` Long, `amount` Integer, `status` ENUM(PAID, FAILED, CANCELED), `type` ENUM(VERIFICATION, MAIN), `fail_code` VarChar, `created_at`·`updated_at` LocalDateTime |
| `payment_cancel` | `payment_id` Long PK, `cancel_at` LocalDateTime, `cancel_reason` VarChar, `created_at`·`updated_at` LocalDateTime |
| `payment_group` | `id` Long PK, `match_id` Long, `taxi_driver_id` Long, `passenger_count` Integer, `status` ENUM(PENDING, PARTIALLY_FAILED, COMPLETED, CANCELLED), `created_at`·`updated_at` LocalDateTime |
| `payment_settlement` | `payment_group_id` Long PK, `total_fare` Integer, `fare` Integer, `remaining_balance` Integer, `platform_charge` Integer, `created_at` LocalDateTime, `taxi_driver_id` Long |

### 작업 시작 시 확인한 사용자 코드

- `PaymentLog`, `PaymentCancel`, `PaymentSettlement`와 다섯 레포지터리의 빈 파일이 저장되어 있었다. 이 골격을 채웠다.
- 이전 확인 이후 `PaymentService`에는 `@Service`, `@Slf4j`, `@RequiredArgsConstructor`와 `PaymentRepository` 주입 필드가 추가되어 있었다. 생성자 주입 필요 여부를 묻는 주석도 있었다. 해당 서비스는 이번 작업에서 수정하지 않았다.

### 구현 내용과 판단

- 관련 계획: **08번 엔티티 설계**, **12번 저장소 작성**. `payment/entity` 아래 다섯 엔티티와 `payment/repository` 아래 동명의 다섯 레포지터리를 작성·수정했다.
- `Payment`의 취소 시각·사유를 제거하고 `PaymentCancel`에 매핑했다. 그룹의 기존 `partnerId`를 `taxiDriverId`로 바꾸고 요금·수수료 필드는 정산 엔티티로 옮겼다.
- `Payment`, `PaymentGroup`, `PaymentLog`는 기존 `BaseEntity`의 자동 생성 Long ID를 사용한다. 취소와 정산은 각각 `paymentId`, `paymentGroupId`를 직접 지정하는 PK이므로 `BaseEntity`를 상속하지 않는다.
- 기존 방식대로 다른 엔티티는 Long ID로 참조한다. JPA 연관관계와 DB FK를 임의로 추가하지 않았다.
- `Integer`와 `LocalDateTime`을 명세대로 사용했다. 기존 `BaseTimeEntity`는 Instant를 사용하므로 상속하지 않고 생명주기 콜백으로 시각을 기록한다. 정산에는 명세에 없는 `updated_at`을 추가하지 않았다.
- `payment/entity/enums/PaymentLogStatus.java`를 추가했다. 이력에는 PENDING을 포함하지 않고 PAID·FAILED·CANCELED만 허용한다. 기존 결제·유형·통화·그룹 enum은 재사용한다.
- 생성은 `create(...)` 정적 메서드를 사용하며, 결제·그룹의 초기 상태는 기존 방향대로 PENDING이다. JPA 기본 생성자는 protected다.
- 다섯 레포지터리는 `JpaRepository<엔티티, Long>`의 저장·ID 조회·삭제 등 기본 기능을 제공한다. 업무별 검색 메서드는 아직 추가하지 않았다.
- 제공되지 않은 필수값·문자열 길이·유일성·FK 정책은 이번에 확정하지 않았다. 기사 ID는 초기 null을 허용한다.

### 검증 결과와 남은 작업

- `./gradlew compileJava --offline`: 작성 중인 `PaymentService`의 `PaymentRepository` import 누락으로 실패했다. 서비스의 결제·취소 메서드도 아직 구현되어 있지 않다.
- 임시 Gradle 작업 `compilePaymentModel`로 다섯 엔티티·레포지터리, 관련 enum, `BaseEntity`만 분리 컴파일했다. **BUILD SUCCESSFUL**로 통과했다. 이는 해당 소스의 컴파일 검증이며 DB 연동 검증은 아니다. `git diff --check`도 통과했다.
- 실제 DB 저장·조회, 마이그레이션 적용, 결제 호출은 수행하지 않았다. DB 제약·업무 상태 변경 메서드·그룹 배정 연동·결제 이력 생성 시점·정산 계산 로직은 후속 작업이다.

## 2026-10-28 — 기존 @CurrentUser 리졸버 사용 방법 확인

- 사용자는 새 리졸버 구현 요청이 아니라 기존 리졸버 유무와 `@CurrentUser` 사용 방법을 질문한 것이라고 정정했다. 애플리케이션 코드는 변경하지 않았다.
- `CurrentUserArgumentResolver`는 인증 정보의 `AuthenticatedPrincipal`에서 `@CurrentUser Long`에는 userId, `@CurrentUser UUID`에는 publicId를 주입한다. 인증 정보가 없으면 UNAUTHORIZED 예외를 던진다.
- `JwtAuthenticationFilter` → `AuthService.authenticate()`에서 JWT 서명·만료를 검사하고 publicId에 해당하는 userId를 캐시 또는 DB에서 조회한다. 따라서 컨트롤러는 두 ID를 각각 주입받을 수 있다.
- 리졸버 자체가 DB를 다시 조회하는 것은 아니다. 기존 `PaymentMethodService.publicIdOf()`의 사용자 조회와 역할이 다르다. 방 참여 및 결제 소유권 검증은 별도로 필요하다.
- 두 ID를 서비스에 전달하는 것은 사용 예시이며, 실제 서비스 계약 변경이나 기존 사용자 조회 제거는 아직 적용하지 않았다. 관련 계획 07·13번의 완료 상태는 유지한다.
- 검증 범위: 인증 필터·서비스·리졸버 소스 확인. 실행 테스트는 수행하지 않았다.

## 2026-10-28 — 사전결제의 사용자·방 참여·예상 요금 검증 코드 검토

- 사용자는 `validateUserExist(userId)` → 방 참여 확인 → `PartyAccess`의 예상 요금 조회·검증 순서로 작성하는 방법을 질문했다.
- 저장된 `PaymentService.prePay()`에는 해당 호출과 단계별 주석이 추가돼 있다. 반환문과 취소 메서드는 아직 작성 중이다.
- 기존 `PartyAccess.findSummary()`는 Optional<PartySummary>, `estimatedFare()`는 Integer를 반환하며 `hasMemberOnParty(partyId, userId)`로 참여 여부를 확인할 수 있다.
- `hasMemberOnParty()`는 방이 없으면 매칭 모듈의 PARTY_NOT_FOUND 예외를 던진다. 설명 예시에서는 방 요약을 먼저 조회해 결제 모듈의 PAYMENT_PARTY_NOT_FOUND로 처리한 다음 참여 여부와 금액을 검증하는 순서를 제안한다.
- `UserAccess.findMemberSummaries()`는 탈퇴자를 제외하지 않고 표시 정보를 바꿔 반환한다. 따라서 이를 사용한 존재 검사는 활성 사용자 검사와 다르다는 점을 명시한다.
- null 또는 0 이하 예상 요금을 INVALID_PAYMENT_AMOUNT로 거절하는 코드를 제안했다. 금액의 배분·계산 정책이나 서비스 전체 구현을 확정한 것은 아니다.
- 관련 계획 13·14번. 소스만 확인했으며 애플리케이션 코드 수정·실행 테스트는 하지 않았다. 체크 상태는 유지한다.

## 2026-10-28 — 사용자·방·예상 요금 검증 구현

- 사용자가 앞서 제안한 코드대로 구현하도록 요청했다. `PaymentService.java`에만 애플리케이션 코드 변경을 적용했다.
- `PartyAccess` 생성자 주입 필드와 필요한 import를 추가했다.
- `validateUserExist()`에서 null ID와 사용자 조회 결과 부재를 `PAYMENT_USER_NOT_FOUND`로 처리한다. 기존 조회 계약상 탈퇴 여부 검증은 포함하지 않는다.
- `prePay()`에 방 ID·방 존재 확인 → 방 참여 확인 → 예상 요금 null·0 이하 검증을 연결했다. 각각 `PAYMENT_PARTY_NOT_FOUND`, `NOT_PARTY_MEMBER`, `INVALID_PAYMENT_AMOUNT`를 사용한다.
- 사용자가 작성 중인 결제수단 조회·결제 그룹 생성 이후 단계, 반환문, 취소 메서드는 그대로 유지했다. 외부 호출과 금액 계산을 추가하지 않았다.
- 계획 13·14번의 실제 작성된 항목만 갱신했다. 전체 기능 완료나 실행 검증 통과로 표시하지 않았다.
- 검증: `./gradlew compileJava --offline` 실행 결과 기존 `return PaymentResultRespons;`, `return PaymentVerificationResponse;`에서 cannot find symbol 오류 2건으로 실패했다. 자동 테스트는 실행하지 못했다. `git diff --check`는 통과했다.
- 다음 작업은 기본 결제수단·빌링키 조회 연결이며, 미완성 반환문과 서비스 후속 구현은 사용자 요청 범위에 맞춰 진행한다.

## 2026-10-28 — 사용자 요청에 따른 컴파일 재실행

- `./gradlew compileJava --offline`을 다시 실행했으며 오류 3건으로 실패했다.
- 현재 저장된 `PaymentService`에는 `validateUserExist(userId)` 호출만 있고 메서드 선언은 없다. `PaymentResultRespons`, `PaymentVerificationResponse` 반환문도 유효한 반환값이 없어 cannot find symbol 오류가 발생했다.
- 파일 재확인 결과 직전 작업에서 추가했던 PartyAccess 주입 및 검증 코드도 현재 파일에 없다. 변경 원인이나 작성자는 단정하지 않는다. 앞선 구현 기록은 당시 기록으로 보존한다.
- 이번 요청은 재실행이므로 현재 소스를 덮어쓰거나 제거된 코드를 자동 복원하지 않았다. 계획의 현재 체크 상태를 수정했다. 테스트 실행은 하지 않았다.

## 2026-10-28 — 사용자 존재 검증 추가 및 잘못된 반환문 제거

- 사용자 요청: 사용자 검증 메서드를 작성하고 잘못된 두 반환문을 제거한다.
- `PaymentService.validateUserExist()`를 private 메서드로 추가했다. userId가 null이거나 기존 `UserAccess.findMemberSummaries()` 조회 결과에 해당 사용자가 없으면 PAYMENT_USER_NOT_FOUND 예외를 던진다.
- 사용자 데이터 존재만 검사하며, 기존 조회 계약상 탈퇴자는 제외하지 않는다. 방 참여·예상 요금 검증은 이번 요청 범위에 추가하지 않았다.
- 잘못된 반환문 두 개를 제거했다. 반환 타입을 유지하면서 작성 중인 메서드가 컴파일되도록 각각 명시적인 UnsupportedOperationException을 두었다. 따라서 사용자가 존재해도 사전결제는 아직 성공 응답을 반환하지 않으며, 취소 메서드도 미구현 예외를 던진다.
- 검증: `./gradlew compileJava --offline` BUILD SUCCESSFUL. `git diff --check` 통과. 자동 테스트·실제 결제 호출은 수행하지 않았다.
- 계획 13번의 사용자 검증 항목과 현재 구현 상태를 갱신했다. 다음 결제·취소 구현 시 미구현 예외를 실제 처리 결과로 교체해야 한다.

## 2026-10-28 — prePay 내부에 방 참여·예상 요금 검증 추가

- 사용자가 두 검증을 `prePay(userId, partyId)` 본문에 작성하도록 요청했다.
- `PaymentService`에 `PartyAccess` 주입 필드와 `PartySummary` import를 추가했다.
- 사용자 검증 다음에 partyId null·방 존재 확인, `hasMemberOnParty()`로 참여 확인, `estimatedFare()`의 null·0 이하 검증을 순서대로 추가했다.
- 방이 없으면 PAYMENT_PARTY_NOT_FOUND, 미참여자는 NOT_PARTY_MEMBER, 유효하지 않은 요금은 INVALID_PAYMENT_AMOUNT로 처리한다. 요금은 매칭 모듈의 조회값을 사용하며 계산하지 않는다.
- 이후 결제수단 조회·결제 요청·취소는 아직 미구현이다. 정상 입력도 마지막의 기존 UnsupportedOperationException에 도달한다.
- 관련 계획 13·14번을 갱신했다. `./gradlew compileJava --offline` BUILD SUCCESSFUL, `git diff --check` 통과. 자동 테스트 및 외부 결제 호출은 실행하지 않았다.

## 2026-10-28 — 기본 결제수단·빌링키 조회와 결제 그룹 조회·생성

- 사용자 요청에 따라 `PaymentService.prePay()`의 다음 두 단계를 구현했다. 관련 계획 08·12·15번.
- `PaymentMethodRepository`, `PaymentGroupRepository`를 생성자로 주입한다.
- 기존 `findDefaultByUserId()`는 해당 사용자의 삭제되지 않은 기본 결제수단만 조회한다. 결과가 없으면 PAYMENT_METHOD_NOT_FOUND, 빌링키가 null·빈 문자열·공백이면 BILLING_KEY_REQUIRED로 거절한다. 그룹 조회·저장 전에 검사한다.
- `PaymentGroupRepository.findByMatchId(Long)`를 추가했다. 기존 그룹이 있으면 재사용하며 `orElseGet()`으로 없을 때만 생성·저장한다.
- 새 그룹은 방 ID, 기사 ID null, 조회 시점의 `party.memberCount()`로 생성하며 상태는 PENDING이다. 현재 인원은 초기 스냅샷이라는 구현 선택이며, 인원 변경 시 갱신 정책과 최종 인원 확정은 별도다.
- 서비스 마지막 UnsupportedOperationException은 유지했다. 따라서 실제 요청은 아직 성공하지 않고 같은 트랜잭션의 그룹 저장도 롤백된다. 별도 커밋이나 외부 결제 호출은 추가하지 않았다.
- 동시 요청의 조회 후 생성 경쟁은 아직 처리하지 않았다. 방당 그룹 하나를 보장하는 DB 제약·충돌 처리·마이그레이션은 기존 후속 과제로 유지한다.
- `PaymentPreparationTest`를 추가해 기본 결제수단 부재, null·빈·공백 빌링키, 기존 그룹 재사용, 신규 그룹의 필드를 검증했다. 외부 결제 호출이 없는 것도 확인했다.
- 검증: `./gradlew test --tests '*PaymentPreparationTest' --offline` 통과(6개 사례), 메인·테스트 컴파일 통과. `git diff --check` 통과. 테스트는 저장소 모의 객체를 사용한 분기 검증이며 실제 DB 커밋·동시성·외부 결제 연동 검증은 아니다.

## 2026-10-28 — JPA 저장과 생성·수정 시각의 동작 이해

### 사용자의 질문과 이해 과정

- `@PrePersist`, `@PreUpdate`의 의미와 `initializeTimestamps()`가 언제 실행되는지 질문했다.
- 그룹 생성 시 생성시간·변경시간이 기본값으로 들어가는지, 직접 호출하지 않은 `save()`가 어디에서 실행되는지 확인했다.
- `save()`를 직접 구현해야 하는지, 최초 저장 때 콜백이 실행된다는 이해가 맞는지 차례로 질문했다.

### 설명한 내용

- `PaymentGroup.create()`는 자바 객체를 만들며, 이때 시각 필드는 아직 null이다.
- `findByMatchId(...).orElseGet(...)`는 그룹이 없을 때만 내부의 `paymentGroupRepository.save(...)`를 실행한다. 기존 그룹 조회만으로 시각이 갱신되지는 않는다.
- `save()`는 상속한 `JpaRepository`의 기능이며 Spring Data JPA가 구현체를 제공한다. 사용자가 레포지터리에 직접 구현하는 메서드는 아니다.
- 새 엔티티를 영속화할 때 JPA가 `@PrePersist` 콜백을 호출한다. `initializeTimestamps`라는 이름이 특별한 것이 아니라 애너테이션 때문에 자동 호출된다.
- 콜백 안에서 LocalDateTime.now()를 구하고 null인 createdAt·updatedAt에 대입한다. DB DEFAULT 설정이 아니라 자바 코드가 값을 채우는 방식이다.
- 이후 엔티티 변경을 DB에 반영할 때 `@PreUpdate` 콜백이 updatedAt을 갱신한다. 단순 조회나 변경 없는 save마다 실행되는 것은 아니며 직접 SQL·벌크 UPDATE에는 적용되지 않는다.
- "처음"은 애플리케이션 전체의 첫 save가 아니라 해당 새 엔티티의 최초 영속화를 뜻한다. 현재 prePay() 마지막 미구현 예외 때문에 생성 시각이 채워져도 그룹 저장은 롤백된다.

## 2026-10-28 — 결제 이력에 대기 상태를 남길지에 관한 논의

### 질문의 범위와 설명 수정

- 사용자는 결제 이력에 대기 상태를 넣어야 하는지 질문했다. 어시스턴트는 처음에 현재 프로젝트의 payment와 payment_log 역할 분리를 기준으로 설명했다.
- 사용자는 프로젝트에 한정된 질문이 아니라 일반적인 결제 이력 설계 관행을 묻는 것이라고 정정했다.
- 이후 쉬운 설명과 명확한 결론을 요청했다. "있어도 되고 없어도 된다는 말인가"라고 되물은 뒤 중단을 요청했다. 이 과정에서 프로젝트의 이력 정책을 확정하거나 변경하라고 요청하지 않았다.

### 설명과 제안의 상태

- 운영·장애 추적용 상태 변경 이력이라면 결제 시작·대기 상태도 남기는 방향을 권장했다. 시작 시점과 결과가 없는 시도를 추적하는 데 도움이 되기 때문이다.
- 결과만 보관하는 이력은 별도의 요청·시도 기록이 있으면 대기 행을 중복 저장하지 않을 수 있다고 설명했다. 이는 모든 시스템에 적용되는 단일 테이블 규칙이 있다는 뜻은 아니다.
- 대기 기록은 외부 결제 요청이 실제 전달되었다는 증거와 구분해야 한다고 설명했다.
- 일반 사례로 [Stripe 결제 생명주기 문서](https://docs.stripe.com/payments/paymentintents/lifecycle)를 확인했다. 결제의 중간 상태와 시도 추적 사례이며 특정 내부 DB 구조를 강제하는 근거는 아니다.
- **PENDING을 payment_log에 추가할지는 미결정이다.** enum·엔티티·이력 생성 코드는 변경하지 않았으며 관련 계획 08·09·19번의 완료 상태도 변경하지 않는다.

### 기록 보완

- 구현·검증 기록은 누적했으나 위 설명 대화들은 즉시 기록하지 못했다. 사용자가 기록 여부를 확인한 시점에 빠진 질문·설명·미결정 사항을 보완했다.
- 이번에는 개발 기록만 수정했다. 코드 변경이나 실행 테스트는 수행하지 않았다.

## 2026-10-28 — 논의 기록 유지 요청과 사전결제 계획 동기화

### 사용자의 요청

- 여기서 작성하고 고민한 내용을 오늘 개발 기록에 누적하고, 사전결제 흐름 관련 변경이 생기면 `payment-verification-plan.md`에도 반영한다.
- 기존 질문·설명·판단 변경 이력은 보존하고, 계획에는 최신 설계와 구현 상태를 반영한다. 확인할 수 없는 과거 대화나 작성 의도는 추정해 추가하지 않는다.

### 저장된 코드에서 새로 확인한 사실

- 마지막 이력 논의에는 PENDING 추가가 미결정이고 enum을 변경하지 않았다고 기록되어 있지만, 이번 확인 시 `PaymentLogStatus`에는 `PENDING`, `PAID`, `FAILED`, `CANCELED`가 있다. 앞선 기록은 당시 상태로 보존한다. 변경 시각·작성자·정책 확정 의도는 단정하지 않는다.
- enum의 주석은 여전히 “처리 중인 PENDING은 포함하지 않는다”고 되어 있어 선언과 불일치한다. 이번 요청은 기록 정리이므로 소스 주석을 수정하지 않았으며 후속 정리 대상으로 남긴다.
- `prePay()`에는 사용자·방·참여·예상 요금 검증 → 기본 결제수단·빌링키 확인 → 그룹 조회·없을 때 생성까지 있다. `PaymentGroupRepository.findByMatchId()`도 유지되어 있다.
- 결제 및 이력 저장·외부 호출은 아직 연결되지 않았다. 마지막 미구현 예외로 신규 그룹 저장도 롤백된다. `cancelPrepayment()`도 미구현 예외를 던진다.
- 컨트롤러는 `prePay()`가 정상 반환한 경우 그 결과의 내부 `paymentId`를 취소 메서드에 전달하도록 작성되어 있다. 현재 서비스 구현이 정상 결제·취소까지 수행한다는 뜻은 아니다.

### 계획에 반영한 내용과 남은 고민

- 상단 구현 현황을 기본 결제수단·빌링키 및 그룹 조회·생성 단계까지 갱신하고, 요약 흐름에 그룹 조회·생성과 결제 기록 커밋을 명시했다.
- 08번에 PENDING enum 추가 사실과 주석 불일치를 남겼다. 대기 이력을 실제로 언제 생성할지, 상태 변경마다 기록할지 시도별로 기록할지는 계속 미결정이다. 19번 이력 저장 구현도 미완료 상태를 유지한다.
- 11번 마이그레이션 범위를 최신 명세의 다섯 테이블로 맞추고, 12번의 이미 작성된 저장소를 새로 작성한다는 표현을 업무별 조회·서비스 연결 후속 작업으로 정리했다.
- 과거 테스트 통과 기록과 이번 소스 확인을 구분했다. 새로운 결제 정책이나 완료 여부를 임의로 확정하지 않았다.

### 변경 및 검증 범위

- 오늘 개발 기록과 기준 계획 두 문서만 수정했다. 애플리케이션 코드와 이전 날짜 기록은 변경하지 않았다.
- 문서의 로컬 링크 대상, 변경 문서의 공백 오류와 반영 내용을 확인했다. 빌드·자동 테스트·DB 연동·실제 결제 호출은 실행하지 않았다.

## 2026-10-28 — payment_log에 PENDING을 포함하기로 결정

### 사용자의 결정과 고민

- 사용자는 `payment_log`에도 `PENDING`을 넣기로 결정했다. 결제 상태를 일관되게 표현하고, 결과뿐 아니라 시작 시점과 처리 시간을 확인하려는 목적이다.
- `payment`에 이미 상태와 생성 시각이 있으므로 중복 기록이 꼭 필요한지 고민했고, 어시스턴트의 의견을 요청했다.
- 논의 후 시작 기록은 있지만 결과 기록이 없는 건을 이력에서 조회할 수 있다는 이점도 확인했으며, 이 근거들을 문서에 남기도록 요청했다. 앞선 “PENDING 포함 여부 미결정” 기록은 당시 논의로 보존하고, 현재 결정은 포함하는 것으로 갱신한다.

### PENDING 이력을 남기는 이유

1. **결제 시작부터 결과까지 한곳에서 조회한다.** 성공·실패·취소 결과만 저장하면 이력만으로 시작 시점을 알 수 없다. PENDING을 남기면 다른 테이블의 생성 시각과 조합하지 않고도 전체 흐름을 읽을 수 있다.
2. **시작했지만 결과가 기록되지 않은 결제를 추적한다.** PENDING 이후 PAID·FAILED 같은 승인 결과 이력이 없는 건을 후속 확인 대상으로 찾을 수 있다. 다만 아직 처리 중이거나 응답·저장이 유실된 경우도 있으므로 결과 이력 부재만으로 결제 실패를 확정하지 않는다. 조회·복구 로직은 별도로 필요하다.
3. **처리 시간을 확인한다.** PENDING 기록과 승인 결과 기록 사이의 시간 차이로 서버가 관측한 처리 시간을 계산할 수 있다. 취소 완료까지의 시간은 별도로 구분한다.
4. **현재 상태와 과거 상태의 역할을 분리하면서 같은 상태 용어를 사용한다.** `payment.status`는 현재 상태를 나타내고, `payment_log`는 그 상태에 이르기까지의 이력을 나타낸다. 모든 과거 로그를 현재 payment 상태와 같게 수정한다는 뜻은 아니다.

`payment.createdAt`도 시작 시각의 단서가 되므로 PENDING 로그는 결제 기능 자체의 필수 조건은 아니다. 이 프로젝트에서는 로그 한 행의 중복보다 이력 조회와 미완료 건 추적의 편의가 더 크다고 판단해 포함한다.

### 설명 예시와 시각의 의미

아래는 기존 로그를 덮어쓰지 않고 상태별 행을 추가하는 방식의 제안 예시다.

```text
10:00:00  PENDING   서버의 결제 처리 시작 기록
10:00:02  PAID      승인 결과 기록
10:00:03  CANCELED  취소 완료 결과 기록
```

- PENDING 시각은 서버가 결제 처리를 시작해 기록한 시각이다. 실제 포트원 요청 송신·수신 시각이나 외부 요청 전달 성공을 보장하지 않는다.
- 현재 `PaymentLog.createdAt`은 `@PrePersist`에서 설정되므로 로그 영속화 시점의 서버 시각이다. 순수 PG 승인 소요 시간이나 실제 포트원 요청 시각이 필요하면 별도 계측을 설계해야 한다.
- PENDING부터 PAID 로그까지의 차이는 서버의 저장·통신·결과 반영 지연을 포함할 수 있는 시간이다. 재시도가 생기면 어느 시작과 결과를 연결할지도 별도로 정해야 한다.

### 확정한 범위와 다음 구현 제안

- **확정:** `payment_log`의 상태에 PENDING을 포함하고 결제 시작 이력을 남긴다.
- **어시스턴트 제안:** 결제 행과 PENDING 로그를 같은 DB 트랜잭션으로 저장·커밋한 뒤 포트원을 호출한다. 승인 결과가 확인되면 현재 결제 상태 변경과 결과 로그 추가도 함께 저장한다.
- **후속 설계·구현:** 정확한 기록 지점과 트랜잭션 분리, 상태별 로그 추가·중복 방지, 재시도 이력의 연결 기준을 구체화한다. 이 제안 전체가 구현되거나 검증됐다는 뜻은 아니다.
- 주석의 “테이블을 만든다”는 다음 서비스 단계에서는 기존 테이블에 결제·이력 **행을 저장한다**는 의미로 구분한다.
- 관련 계획: 08·09번 이력·상태 설계, 16번 요청 기록 저장, 19번 결과 저장.

### 변경 및 검증 범위

- 오늘 개발 기록과 `payment-verification-plan.md`에 결정 및 근거를 반영했다. 애플리케이션 코드는 변경하지 않았다.
- 문서의 공백과 로컬 링크를 검사했다. 빌드·자동 테스트·외부 결제 호출은 실행하지 않았다.

## 2026-10-28 — PENDING 결정에 따른 코드 점검 및 주석 수정

- 사용자 요청에 따라 PENDING 결정을 코드에도 반영할 부분이 있는지 확인했다.
- `PaymentLogStatus.PENDING`은 이미 선언되어 있고, `PaymentLog.status`는 문자열 enum 매핑이며 `PaymentLog.create()`에서 PENDING을 받을 수 있다. 기본 저장소도 존재한다.
- enum의 “PENDING은 포함하지 않는다”는 주석을 수정했다. PENDING이 서버의 처리 시작을 뜻하며 외부 요청 전달을 보장하지 않는다는 설명을 추가했다.
- `PaymentService.prePay()`의 다음 단계 주석을 결제·시작 이력 행 저장 → 외부 호출 전 커밋 → 빌링키 결제 요청 → 현재 상태 변경·결과 이력 추가로 구체화했다. 이 주석은 후속 구현 안내다.
- 실제 결제 행·시작 로그 저장과 payment_log 마이그레이션은 아직 없다. 현재 서비스 끝의 미구현 예외로 저장이 롤백되므로, 저장 호출만 추가하지 않았다. 트랜잭션 분리와 실제 결제 연결은 후속 구현으로 유지한다.
- 이번 변경은 코드 주석과 두 문서에 한정된다. 동작 변경이 없어 빌드·자동 테스트는 실행하지 않았다. 수정한 파일의 공백 검사를 수행했다.

## 2026-10-28 — 구현 상태에 따른 커밋 분리와 검증

### 사용자의 요청과 분리 기준

- 사용자는 하나의 브랜치에서 결제 검증 API를 작성하는 동안, 서비스가 미완성이어도 커밋할 수 있는 단위를 문의했다. 파일과 코드 변경 범위를 제안한 뒤 지금 커밋할 수 있는 단위로 실제 커밋하도록 요청했다.
- 후속 요청에 따라 어떤 코드가 어느 커밋에 포함됐는지, 왜 분리했는지도 이 문서에 남긴다.
- 작업 브랜치는 `feature/payment-billing-key-charge`다. 하나의 API 개발을 패키지 정리 → 데이터 모델 → 서비스 준비 단계 → 문서 기록의 목적별 변경으로 나눴다.
- API 전체 완성 여부와 커밋 가능한 중간 상태를 구분한다. 각 코드 커밋에 필요한 의존 파일을 함께 포함하고, 미완료 기능을 완료했다고 표현하지 않는다.
- 기존 스테이징에는 생성 직후의 빈 클래스가 포함되어 있었다. 기존 스테이징 차이를 임시 패치로 보관한 뒤 스테이징만 해제하고 최신 작업 파일을 단위별로 다시 추가했다. 작업 파일 내용은 유지했다.

### 커밋 1 — c9d46bf: 결제수단 유형 enum 패키지 이동

**커밋 제목:** `refactor: 결제수단 유형 enum 패키지 이동`

**포함 파일과 변경 코드:** 아래 경로는 `src/main/java/team/codingforest/moyeota/payment/` 기준이며 테스트는 `src/unitTest/java/team/codingforest/moyeota/payment/` 기준이다.

| 파일 | 실제 포함한 변경 |
|---|---|
| `entity/PaymentMethodType.java` → `entity/enums/PaymentMethodType.java` | package를 `payment.entity.enums`로 변경하고 파일을 이동했다. CARD 값은 유지한다. |
| `entity/PaymentMethod.java` | 이동된 enum의 import를 추가했다. |
| `dto/PaymentMethodResponse.java` | enum import 경로를 변경했다. |
| 테스트 `controller/PaymentMethodControllerTest.java` | enum import 경로를 변경했다. |
| 테스트 `entity/PaymentMethodTest.java` | enum import를 추가했다. |
| 테스트 `service/PaymentMethodServiceTest.java` | enum import 경로를 변경했다. |

**분리 이유:** 동작 변경 없는 패키지 정리를 신규 결제 기능과 분리하면 검토와 변경 추적이 쉽다. 이동 파일과 모든 변경된 참조를 함께 커밋해 import가 깨지는 중간 상태를 방지한다.

**검증:** 스테이징된 파일만 임시 디렉터리에 내보낸 뒤 `./gradlew test --tests '*PaymentMethodTest' --tests '*PaymentMethodServiceTest' --tests '*PaymentMethodControllerTest' --offline` 실행. 메인·테스트 컴파일 및 지정 테스트가 BUILD SUCCESSFUL로 통과했다. 미커밋 포트원 초안이나 서비스 파일은 이 검증에 포함하지 않았다.

### 커밋 2 — 1a1658f: 결제 및 이력 데이터 모델 추가

**커밋 제목:** `feat: 결제 및 이력 데이터 모델 추가`

**포함 파일과 변경 코드:** 경로는 동일한 main 결제 패키지 기준이다.

| 파일 | 실제 포함한 변경 |
|---|---|
| `entity/Payment.java` | 결제 식별자·사용자·그룹·결제수단·금액·상태·유형·통화·실패 코드 매핑, create(), 초기 PENDING, 생성·수정 시각 콜백 |
| `entity/PaymentGroup.java` | 방·기사·인원·그룹 상태 매핑, create(), 초기 PENDING, 시각 콜백 |
| `entity/PaymentLog.java` | 결제 ID·금액·상태·유형·실패 코드 매핑, 상태를 인자로 받는 create(), 시각 콜백 |
| `entity/PaymentCancel.java` | 결제 ID를 PK로 사용, 취소 시각·사유 및 생성·수정 시각 매핑 |
| `entity/PaymentSettlement.java` | 그룹 ID를 PK로 사용, 요금·잔액·수수료·기사·생성 시각 매핑 |
| `entity/enums/PaymentCurrency.java` | KRW·USD·JPY |
| `entity/enums/PaymentGroupStatus.java` | PENDING·PARTIALLY_FAILED·COMPLETED·CANCELLED |
| `entity/enums/PaymentLogStatus.java` | PENDING·PAID·FAILED·CANCELED. PENDING이 서버의 처리 시작이며 외부 전달을 보장하지 않는다는 주석 포함 |
| `entity/enums/PaymentStatus.java` | PENDING·PAID·FAILED·CANCELED |
| `entity/enums/PaymentType.java` | VERIFICATION·MAIN |
| `repository/PaymentRepository.java` | Payment의 JpaRepository |
| `repository/PaymentGroupRepository.java` | 기본 CRUD 및 `Optional<PaymentGroup> findByMatchId(Long matchId)` |
| `repository/PaymentLogRepository.java` | PaymentLog의 JpaRepository |
| `repository/PaymentCancelRepository.java` | PaymentCancel의 JpaRepository |
| `repository/PaymentSettlementRepository.java` | PaymentSettlement의 JpaRepository |

**분리 이유:** 서비스가 사용할 데이터 구조를 먼저 확보한다. 다섯 테이블의 최신 명세를 하나의 모델 변경으로 묶되, 실제 결제 실행·결과 저장과 분리한다. 이 커밋의 PENDING은 표현 가능한 상태를 추가한 것이며 시작 로그가 자동 저장된다는 뜻은 아니다.

**검증:** 앞선 커밋과 이번 스테이징만 포함한 임시 소스에서 `./gradlew compileJava --offline` BUILD SUCCESSFUL. DB 마이그레이션, 실제 DB 연동, 엔티티 상태 전이 검증은 수행하지 않았다. 마이그레이션은 이번 커밋에 없다.

### 커밋 3 — c8e26a8: 사전결제 준비 단계 검증과 API 골격 추가

**커밋 제목:** `feat: 사전결제 준비 단계 검증과 API 골격 추가`

| 파일 | 실제 포함한 변경 |
|---|---|
| `service/PaymentService.java` | 사용자 존재 → 방 존재·참여 → 예상 요금 → 기본 결제수단·빌링키 검증, 그룹 조회·없을 때 생성. 결제 및 취소 메서드의 미구현 예외와 후속 구현 TODO 포함 |
| `controller/PaymentController.java` | `POST /api/v1/payments/parties/{partyId}/verifications`, prePay() 결과의 내부 paymentId를 cancelPrepayment()에 전달하는 골격 |
| `dto/PaymentResultResponse.java` | 내부 결제 ID·PG 결제 ID·사용자 ID·금액·상태를 담는 중간 응답 record |
| `dto/PaymentVerificationResponse.java` | paymentId·verified를 담는 최종 응답 record |
| `exception/PaymentErrorCode.java` | NOT_PARTY_MEMBER·PAYMENT_PARTY_NOT_FOUND·INVALID_PAYMENT_AMOUNT 추가 |
| 테스트 `service/PaymentPreparationTest.java` | 기본 결제수단 부재, null·빈·공백 빌링키, 기존 그룹 재사용, 신규 그룹의 인원·기사·상태 및 외부 호출 부재 검사 |

**분리 이유:** 외부 승인·취소가 없어도 준비 단계의 입력 검증과 그룹 조회 분기는 독립적으로 검토·검증할 수 있다. 컨트롤러와 응답 DTO는 서비스 메서드 계약을 함께 참조하므로 같은 커밋에 포함했다. 제목에 API 골격이라고 표시하고 커밋 본문에도 미구현 범위를 명시했다.

**기존 제안과 달라진 점:** 포트원 연동 커밋을 보류했으므로 INVALID_PAYMENT_AMOUNT는 이를 사용하는 준비 단계 서비스와 함께 커밋했다. 현재 서비스는 기존 PortOneClient 타입을 주입받지만 신규 payWithBillingKey()를 호출하지 않는다. 따라서 포트원 초안 없이도 컴파일·준비 단계 테스트가 가능하다.

**검증:** 스테이징된 소스만으로 `./gradlew test --tests '*PaymentPreparationTest' --offline` BUILD SUCCESSFUL. 메인·테스트 컴파일 통과. XML 결과에서 테스트 6개, 실패 0개, 오류 0개, 건너뜀 0개를 확인했다.

**현재 한계:** 정상 입력도 UnsupportedOperationException으로 종료된다. 실제 트랜잭션에서는 신규 그룹 저장도 롤백된다. 테스트는 모의 저장소 기반 분기 검증으로 실제 DB 커밋·포트원 연동·결제 취소 성공을 보장하지 않는다. 결제 검증 API 완성이나 운영 반영 완료를 의미하지 않는다.

### 문서 커밋 — 사전결제 설계 및 개발 이력 정리

**커밋 제목:** `docs: 사전결제 설계 및 개발 이력 정리`

- 포함 파일: `docs/payment-verification-plan.md`, `docs/2026-10-27-payment-verification-dev-log.md`, `docs/2026-10-28-payment-verification-dev-log.md`.
- 계획에는 최신 설계·진행 상태·미완료 작업을, 날짜별 기록에는 질문·판단 변경·구현·검증 이력을 담는다. 이 절의 코드 커밋별 변경과 분리 이유도 포함한다.
- 세 문서가 서로 참조하므로 함께 추적한다. 코드와 문서 정리를 분리해 문서 변경을 따로 검토할 수 있게 한다.
- 문서 커밋은 이 기록 자체를 포함하므로 자기 커밋 해시를 본문에 넣지 않는다. 커밋 제목과 Git 이력으로 식별한다.

### 커밋하지 않고 작업 파일로 남긴 변경

| 파일 또는 변경 | 보류 이유 |
|---|---|
| `client/PortOneClient.java`, `dto/portone/PaymentAmount.java`, `PortOnePaymentRequest.java`, `PortOnePaymentResponse.java` | 외부 API 명세와 요청·응답 구조 및 오류 처리 검증이 남은 연동 초안이다. 이번에는 검증된 준비 단계와 분리해 남겼다. |
| `dto/portone/BillingKeyPayment.java` | 현재 사용하지 않으며 응답 구조 검증 후 필요 여부를 판단한다. |
| `client/PaymentCanellationResonse.java`, `dto/portone/PortOnePaymentCancelReqeust.java` | 취소 구현 시 이름 오타·필드 명세를 확인하고 함께 반영할 초안이다. |
| `controller/PaymentMethodController.java`, `service/PaymentMethodService.java` | 이번 기능과 무관한 빈 줄 변경이다. |
| `docs/payment-verification-plan 1.md` | 기준 계획과의 중복 여부를 확인하지 않은 별도 파일이다. |
| `.claude/`, `.obsidian/` | 이번 결제 기능 커밋의 대상이 아니다. |

작업 파일은 삭제하거나 되돌리지 않았다. 로컬 커밋만 수행하며 원격 push는 포함하지 않는다. 각 코드 커밋은 staged diff의 공백 검사도 통과했다.

## 이후 작업 기록 양식

```markdown
## YYYY-MM-DD — 작업 번호 및 제목

### 시작한 고민과 요청

### 확인한 사실

### 검토한 선택지와 판단

제안·확정·미결정 사항, 이전 방향에서 바뀐 이유와 관련 계획 번호를 구분한다.

### 변경한 내용

변경 파일과 구현 범위를 기록한다. 코드 변경이 없으면 그 사실을 명시한다.

### 검증 결과

실제로 수행한 검사와 결과, 실행하지 않은 검사와 한계를 구분한다.

### 남은 고민과 다음 작업
```
