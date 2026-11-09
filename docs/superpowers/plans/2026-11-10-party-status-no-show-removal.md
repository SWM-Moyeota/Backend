# 매칭 방 상태 정의 변경·노쇼 제거 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `COMPLETED` 를 "약속 확정" 하나의 뜻으로 고정하고, 노쇼 기능을 전부 걷어내며, Party 애그리거트를 같은 검사 순서·같은 헬퍼로 다시 짠다.

**Architecture:** 도메인(`Party`·`PartyMember`·`PartyStatus`)을 먼저 새 규칙으로 바꾸고 유닛 테스트를 맞춘 뒤, 서비스·리포지토리·컨트롤러·알림 순으로 따라간다. 노쇼는 매칭 → user 모듈 → DB 순으로 지운다. 마지막에 Swagger·http·문서를 새 규칙으로 고친다.

**Tech Stack:** Spring Boot 4.1, Spring Modulith, JPA(PostgreSQL 17 + PostGIS), Flyway, JUnit 5, Mockito, AssertJ. 유닛 `./gradlew test`, 통합 `./gradlew integrationTest`(localhost 5432·6379 필요).

**Spec:** `docs/superpowers/specs/2026-11-10-party-status-no-show-removal-design.md`

## Global Constraints

- 작업 위치: `/Users/sungyoon/Desktop/moyeota/backend` 작업 트리(브랜치 `feature/sweeper-cron`, 커밋 안 된 수정 위에서 이어감). 워크트리로 cd 하지 않는다.
- 모든 주석·테스트 이름·커밋 메시지는 한국어. 커밋 메시지 끝에 `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- 들여쓰기는 스페이스 4칸. 탭 금지.
- 도메인 안에서 `Instant.now()` 금지. `now` 는 인자.
- 예외는 규칙 위반에만. 스케줄러가 건너뛰는 정상 흐름은 결과 객체.
- 지울 코드는 지운다. 주석 처리나 `@Deprecated` 로 남기지 않는다(앱 호환 필드 2곳만 예외, 삭제 조건을 적는다).
- 마이그레이션 V26·V27·V34 파일은 손대지 않는다. 새 변경은 V37 하나.
- `confirm` 시간 규칙: `now + 5분 <= departureAt <= now + 1시간` (경계 포함).

## Review Focus

1. 정원 3명이 찬 ACTIVE 방 — 근처 방·지도·예약 목록에서 보이지 않아야 한다 (Task 6 테스트).
2. 확정 방에서 2명 중 1명이 나감 — 방은 CANCELED, 남은 사람은 SSE `closed` 를 받는다 (Task 2·5 테스트).
3. 출시 앱이 장소 없이 `{departureAt}` 만 보냄 — 기존 출발지 유지하고 확정된다 (Task 4·7 테스트).
4. 확정 방 약속 1일 뒤 아무도 출발 안 누름 — 자동 정리가 약속 시간 기준으로 고른다. 만든 시각 기준이 아니다 (Task 6 테스트).
5. V37 이 "정원 차서 COMPLETED, departure_at NULL" 인 실시간 방을 ACTIVE 로 되돌린다 (Task 9 테스트).

---

### Task 0: 컴파일 복구와 작업 시작점

**Files:**
- Modify: `src/main/java/team/codingforest/moyeota/faresplit/domain/FareSplit.java` (두 번째 `create` → `load` 로 되돌림. 검증 없는 DB 복원용이라 생성용과 이름이 같으면 안 된다)
- Modify: `src/main/java/team/codingforest/moyeota/faresplit/domain/FareSplitTransfer.java` (`setXxxAt` → 원래 동사 `markSent`·`confirm`·`markRequested`·`markConfirmRequested`. 도메인 동작이 setter 로 읽히지 않게)
- Modify: `src/main/java/team/codingforest/moyeota/faresplit/domain/FareSplit.java` 호출부, `FareSplitService.java` 의 `// 누가 누구한테…` TODO 주석은 그대로 둔다(사용자 메모)

- [ ] Step 1: `./gradlew compileJava compileTestJava compileIntegrationTestJava -q` → `FareSplitEntity.java:113 cannot find symbol` 확인
- [ ] Step 2: 위 이름 되돌리기
- [ ] Step 3: 같은 명령 → BUILD SUCCESSFUL
- [ ] Step 4: `./gradlew test --tests 'team.codingforest.moyeota.faresplit.*' -q` → 통과
- [ ] Step 5: 커밋 `refactor: 정산 enum 패키지 분리와 생성 메서드 이름 정리`

### Task 1: PartyStatus·PartyMember — 상태 정의와 노쇼 필드 제거

**Files:**
- Modify: `matching/party/domain/PartyStatus.java`
- Modify: `matching/party/domain/PartyMember.java`
- Test: `src/unitTest/.../matching/party/domain/PartyStatusUnitTest.java`, `PartyMemberUnitTest.java`
- Delete: `src/unitTest/.../matching/party/domain/PartyNoShowUnitTest.java`

**Interfaces:**
- Produces: `PartyStatus.isOpen()`(ACTIVE·COMPLETED), `PartyStatus.isClosed()`. `isRecruiting()`·`isOngoing()` 삭제.
- Produces: `PartyMember(memberId, joinedAt)`, `PartyMember.load(memberId, joinedAt, arrivedAt)`, `arrive(now)`, `isArrived()`, getter 3개. 노쇼 필드·메서드 전부 삭제.

- [ ] Step 1: `PartyStatusUnitTest` 를 `isOpen`/`isClosed` 4값 표로 다시 쓴다. `PartyMemberUnitTest` 에서 노쇼 테스트 삭제, `load` 3인자로.
- [ ] Step 2: 테스트 컴파일 실패 확인
- [ ] Step 3: `PartyStatus` enum 주석에 전이 표(spec "방 상태" 표) 추가. `isOpen`/`isClosed` 만 남김.
- [ ] Step 4: `PartyMember` 에서 `noShowAt`·`noShowVoidedAt`·`markNoShow`·`voidNoShow`·`isNoShow` 삭제.
- [ ] Step 5: 두 테스트 통과 (다른 컴파일 오류는 Task 2~3 에서)
- [ ] Step 6: 커밋은 Task 3 과 함께

### Task 2: Party 애그리거트 다시 쓰기

**Files:**
- Rewrite: `matching/party/domain/Party.java`
- Modify: `matching/party/domain/AutoCloseResult.java`
- Create: `matching/party/domain/MeetingPlan.java`
- Test: `src/unitTest/.../matching/party/domain/` 의 `PartyTest`, `PartyConfirmUnitTest`, `PartyAttendanceUnitTest`, `PartyAutoCloseUnitTest`, `PartyHostUnitTest`, `PartyReservationTest`, `PartyMeetingNotificationUnitTest`

**Interfaces (Produces):**
```java
// 상수
public static final Duration MIN_CONFIRM_AHEAD = Duration.ofMinutes(5);
public static final Duration MAX_CONFIRM_AHEAD = Duration.ofHours(1);
public static final Duration MAX_RESERVATION_AHEAD = Duration.ofDays(7);
public static final double ARRIVAL_RADIUS_METERS = 100;
public static final Duration ARRIVAL_OPENS_BEFORE = Duration.ofMinutes(10);
public static final int MIN_MEMBERS_TO_DEPART = 2;
static final Duration MEETING_NOTICE_STALE_AFTER = Duration.ofMinutes(30);
public static final List<Duration> DEPARTURE_REMINDER_OFFSETS = …(그대로);

// 생성·복원
public static Party create(Long creatorId, Location departure, Location destination, String departurePlace, String destinationPlace, Capacity capacity, Instant now, Integer estimatedFare, Integer estimatedTime, String route);
public static Party reserve(Long creatorId, Location departure, Location destination, String departurePlace, String destinationPlace, Capacity capacity, Instant now, Instant departureAt);
public static Party load(Long id, Location departure, Location destination, String departurePlace, String destinationPlace, Capacity capacity, List<PartyMember> members, Long hostId, Instant createdAt, PartyStatus status, Integer estimatedFare, Integer estimatedTime, String route, long version, RoomType roomType, Instant departureAt, Instant reminderSentAt, Instant meetingNotifiedAt, int departureReminderCount);   // 하나만

// 행위 (검사 순서: 멤버 → 방장 → 열림 → 종류·확정 → 인원 → 시간 → 행위자 상태)
public void join(Long memberId, Instant now);
public void leave(Long memberId);                       // 열린 방이면 언제든. 마지막이거나 확정 방에서 혼자 남으면 CANCELED
public void confirm(Long memberId, MeetingPlan plan, Instant now);   // 실시간·방장·ACTIVE·2명↑·시간 범위. plan.departure() 가 null 이면 출발지 유지
public boolean arrive(Long memberId, boolean nearDeparture, Instant now);
public void depart(Long memberId, Instant now);         // 방장·열림·확정·2명↑·약속 뒤·방장 도착 → FINISHED
public ExpireResult expireRecruiting();                 // ACTIVE 이고 혼자면 CANCELED, 아니면 SKIPPED
public AutoCloseResult autoClose(Instant now);          // 닫혔으면 skipped; 미확정·혼자 → CANCELED; 도착자 있음 → FINISHED, 없음 → CANCELED
public PartyNotice markMeetingNotified(Instant now);
public PartyNotice advanceDepartureReminder(Instant now);   // 받는 사람: 방장 1명 (hostId)
public void markReminderSent(Instant now);

// 조회
public boolean isConfirmed();      // isReservation() || departureAt != null
public boolean isReservation();
public boolean isFull();           // members.size() >= capacity
public boolean hasCompanion();     // members.size() >= MIN_MEMBERS_TO_DEPART
public boolean isHost(Long memberId);
public boolean isMember(Long memberId);
public List<Long> riderIds();      // 도착한 멤버 (합류 순)
public List<Long> memberIds();
public List<PartyMember> getMembers();   // unmodifiable
// getter 들: getHostId() 로 통일(hostId() 삭제)

// private 헬퍼
private PartyMember findMember(Long memberId);   // NOT_PARTY_MEMBER
private void ensureHost(Long memberId);          // findMember 후 NOT_PARTY_HOST
private void ensureOpen();                       // PARTY_CLOSED
private void ensureConfirmed();                  // PARTY_NOT_CONFIRMED
private void ensureCompanion();                  // NOT_ENOUGH_MEMBERS
private List<PartyMember> arrivedMembers();
private void close(PartyStatus next);            // FINISHED/CANCELED 대입 + version++
private static void ensureDifferentPlaces(Location departure, Location destination);   // SAME_DEPARTURE_DESTINATION
```
```java
public record MeetingPlan(Instant departureAt, Location departure, String departurePlace) {
    public static MeetingPlan at(Instant departureAt)   // 장소 유지
}
public record AutoCloseResult(boolean handled, PartyStatus status, List<Long> riderIds) {
    static skipped(PartyStatus), closed(PartyStatus, List<Long>);  boolean hasRiders()
}
public enum ExpireResult { CANCELED, SKIPPED }
```
- 에러 코드 통일: 닫힌 방은 어디서나 `PARTY_CLOSED`. `PARTY_NOT_RECRUITING` 은 "ACTIVE 가 아닌 방에 참여·확정"에만 쓴다(join: 확정 방은 `PARTY_CLOSED`? → **join 은 `status != ACTIVE` 이면 `PARTY_CLOSED`** 로 통일, `PARTY_NOT_RECRUITING` 은 confirm 에서 COMPLETED 방은 `ALREADY_CONFIRMED` 가 먼저 잡히므로 쓰이지 않게 된다 → 코드 삭제).
- `members` 는 `List.copyOf` 가 아니라 `Collections.unmodifiableList` 로 내보낸다.

- [ ] Step 1: 유닛 테스트를 새 규칙으로 다시 쓴다. 파일별 핵심 단언:
  - `PartyTest`: 정원 3 에 3명 참여 → status ACTIVE, `isFull()` true, 4번째 join → `PARTY_FULL`. 닫힌 방 join → `PARTY_CLOSED`.
  - `PartyConfirmUnitTest`: `NOW+5분` 통과·`NOW+5분-1초` 거절·`NOW+1시간` 통과·`+1초` 거절; `MeetingPlan.at` 이면 출발지 그대로; 장소 넣으면 `getDepartureLocation()`·`getDeparturePlace()` 바뀜; 장소가 도착지와 같으면 `SAME_DEPARTURE_DESTINATION`; 예약 방 `NOT_REALTIME_PARTY`; 비멤버 `NOT_PARTY_MEMBER`(방장 검사보다 먼저); 확정 뒤 leave 가능; 확정 방 2명 중 1명 leave → CANCELED.
  - `PartyAttendanceUnitTest`: depart 비방장 `NOT_PARTY_HOST`; 방장 미도착 `NOT_ARRIVED`; 성공 시 FINISHED·`riderIds()` = 도착자만; 노쇼 단언 삭제; `finishUnconfirmed` 테스트 삭제.
  - `PartyAutoCloseUnitTest`: 닫힌 방 → `handled=false`; 미확정 ACTIVE(2명) → CANCELED; 확정 혼자 → CANCELED; 도착 1명 → FINISHED·riderIds; 도착 0 → CANCELED; 약속 전 방도 그대로 처리(가르는 건 쿼리).
  - `PartyMeetingNotificationUnitTest`: 재촉 받는 사람이 `[hostId]`.
  - `PartyReservationTest`: 15분 뒤 2명이면 `ExpireResult.SKIPPED`·ACTIVE 유지; 정원 찬 ACTIVE 방도 혼자가 아니면 SKIPPED.
  - `PartyHostUnitTest`: 13인자 `load` 검증 테스트 삭제. 저장된 hostId 를 그대로 복원한다는 테스트로 대체.
  - 13·17인자 `Party.load` 와 5인자 `PartyMember.load` 를 부르는 유닛 테스트 헬퍼(조사 결과 약 20개 파일: `PartyServiceUnitTest`, `PartyServiceTest`, `PartyAccessServiceUnitTest`, `PartyOpenerUnitTest`, `PartyDetailResultTest`, `PartySseServiceUnitTest`, `RideRecord*Test`, `MeetingNotificationServiceUnitTest`, `PartyMeetingNotificationUnitTest`, `PartyJpaPersistenceTest`, `PartyOngoingCheckerTest` 등)는 `src/unitTest/java/team/codingforest/moyeota/matching/party/PartyFixtures.java` 의 `realtime(...)`, `reserved(...)`, `restored(...)`, `member(id)`, `arrivedMember(id, at)` 로 바꾼다. 통합 테스트는 소스 세트가 달라 각 파일의 private 헬퍼를 19인자·3인자로 고친다.
- [ ] Step 2: 컴파일 실패 확인
- [ ] Step 3: `Party.java` 를 Interfaces 대로 다시 쓴다. 주석은 규칙의 이유와 연동 지점만.
- [ ] Step 4: `./gradlew test --tests 'team.codingforest.moyeota.matching.party.domain.*' -q` 통과
- [ ] Step 5: 커밋 `refactor: 매칭 방 상태를 모집 중·확정으로 나누고 노쇼 없이 Party 를 다시 짠다`

### Task 3: 에러 코드·노쇼 코드 삭제 (matching)

**Files:**
- Delete: `matching/noshow/**`(main 15개), `src/unitTest/.../matching/noshow/**`, `src/integrationTest/.../matching/noshow/**`, `src/unitTest/.../matching/party/FakeNoShowPenalty.java`
- Modify: `matching/exception/MatchingErrorCode.java` — 삭제: `CANNOT_LEAVE_CONFIRMED_PARTY`, `USE_DEPART_FOR_CONFIRMED_PARTY`, `NO_SHOW_RESTRICTED`, `NOT_NO_SHOW`, `ALREADY_OBJECTED`, `OBJECTION_NOT_FOUND`, `OBJECTION_ALREADY_RESOLVED`, `PARTY_NOT_COMPLETED`, `PARTY_NOT_RECRUITING`. 문구 수정: `INVALID_MEETING_TIME` → "약속 시간은 지금부터 5분 뒤에서 1시간 사이여야 합니다."
- Modify: `matching/party/domain/MeetingNotifier.java`(notifyNoShow 삭제), `infrastructure/FcmMeetingNotifier.java`(NO_SHOW Push 삭제, remindDeparture 문구 "출발하셨나요? 모두 모였으면 출발 버튼을 눌러 주세요."), `LoggingMeetingNotifier.java`
- Modify: `matching/party/MeetingNotificationService.java`(notifyNoShows 삭제, 클래스 주석)
- Modify: `matching/party/infrastructure/PartyMemberEntity.java`(컬럼 2개·update·toDomain), `PartyJpaRepository.java:54` 조건 삭제
- Modify: `matching/record/infrastructure/RideRecordJpaRepository.java` 18·27·38 조건 삭제, `RideRecordController.java` 설명, `dto/RideRecordResponse.java` 설명, `matching/api/dto/PartyRiders.java` 주석
- Modify: `matching/party/PartyAccessService.java`(`hostId()`→`getHostId()` 등 이름 변경 따라감)
- Test: `MeetingNotificationServiceUnitTest`(notifyNoShows 3개 삭제), `FcmMeetingNotifierUnitTest`(2개 삭제), `PartyAccessServiceUnitTest`(노쇼 제외 2개 삭제), `PartyJpaTest`, `record/FakeRideRecords`, `PartyJpaPersistenceTest`(도착·노쇼 섹션에서 노쇼 부분 삭제, `confirmedJustNow` 는 `now` 를 6분 전으로 넘겨 유지)

- [ ] Step 1: 삭제·수정 후 `./gradlew compileJava -q` 통과(PartyService 는 Task 4 에서 — 컴파일 오류가 남으면 Task 4 와 한 커밋)
- [ ] Step 2: `./gradlew test --tests 'team.codingforest.moyeota.matching.*' -q` 는 Task 4 뒤에
- [ ] Step 3: 커밋(Task 4 와 함께) `feat: 노쇼 기록·제재·이의 신청을 없앤다`

### Task 4: PartyService·PartyOpener·Controller·DTO

**Files:**
- Modify: `matching/party/PartyService.java`
  - 삭제: `NoShowPenalty`, `PartyCompletionPolicy` 의존, `ensureNotRestricted`, `penalizeNoShows`, `finish`, `notifyRecruitingClosed`
  - `join`: `party.join(memberId, now)`; `isFull` 분기 삭제
  - `confirm(Long partyId, Long memberId, MeetingPlan plan)`; SSE `PartyConfirmed.from(party)` 에 출발지 좌표·이름 포함
  - `depart`: 반환 없음. `rideRecords.save(...)` 그대로
  - `expireRecruiting`: `ExpireResult.SKIPPED` 면 로그만; CANCELED 면 저장·closed 이벤트
  - `autoClose`: `result.handled()` false 면 로그만 반환; `hasRiders()` 면 탑승 기록
- Modify: `matching/party/PartyOpener.java`(`PartyCompletionPolicy` 삭제, `Party.create(..., now, ...)`)
- Delete: `matching/party/completion/**`, `matching/party/dto/RecruitingClosed.java`
- Modify: `matching/party/domain/PartyChangeNotifier.java`(recruitingClosed 삭제), `matching/sse/infrastructure/RedisPartyChangeNotifier.java`
- Modify: `matching/party/dto/ConfirmPartyRequest.java`:
  ```java
  public record ConfirmPartyRequest(@NotNull Instant departureAt, Double departureLat, Double departureLng, @Size(max = 100) String departure) {
      public MeetingPlan toPlan()   // lat·lng 둘 중 하나만 오면 INCOMPLETE_DEPARTURE(400, 새 코드) — Location 은 둘 다 있을 때만
  }
  ```
- Modify: `matching/party/dto/PartyConfirmed.java` → `(String status, Instant departureAt, Double departureLat, Double departureLng, String departure, long version)` + `from(Party)`
- Modify: `matching/party/PartyController.java` — `/finish` 삭제, Swagger 설명을 spec 규칙으로 전부 다시 씀(정원 차도 ACTIVE, 나가기 언제든, 방장만 출발, 노쇼 문장 삭제, confirm 5분~1시간·출발지 선택)
- Modify: `matching/party/ReservationReminderService.java`: `isOpen()` + `hasCompanion()` 아니면 건너뜀
- Modify: `matching/party/CompletedPartySweeper.java`·`PartyRecruitingSweeper.java`: `catch(BusinessException)` 는 PARTY_NOT_FOUND 등 진짜 예외용으로만 남기고 주석 갱신
- Modify: `matching/sse/PartySseController.java` 설명(recruiting-closed 삭제, confirmed 필드 추가, 정원 차면 ACTIVE)
- Test: `PartyServiceUnitTest`, `PartyServiceTest`, `PartyControllerUnitTest`, `PartyControllerTest`, `PartyOpenerUnitTest`, `CompletedPartySweeperUnitTest`, `PartyRecruitingSweeperUnitTest`, `ReservationReminderServiceTest`, `sse/*` 테스트, `integrationTest/common/PassengerContextTest`

- [ ] Step 1: 위 테스트에서 노쇼·finish·recruitingClosed·CompletionPolicy·CANNOT_LEAVE 단언 삭제, confirm 호출을 `MeetingPlan.at(...)` 으로, depart 방장 전제로 고친다. 새 단언: confirm 요청에 lat·lng·departure 가 오면 서비스에 `MeetingPlan(…, new Location(lat,lng), departure)` 가 전달된다; `ReservationReminderService` 는 혼자인 예약 방에 보내지 않는다; `expireRecruiting` SKIPPED 면 저장하지 않는다.
- [ ] Step 2: 컴파일 실패 확인
- [ ] Step 3: 구현
- [ ] Step 4: `./gradlew test --tests 'team.codingforest.moyeota.matching.*' -q` 통과
- [ ] Step 5: 커밋 `feat: 노쇼 기록·제재·이의 신청을 없애고 약속 확정에 만남 장소를 더한다`

### Task 5: 나가기·출발 흐름 통합 테스트

**Files:**
- Modify: `src/integrationTest/.../matching/party/PartyAttendancePostgresTest.java`, `PartyAutoClosePostgresTest.java`, `PartyAccessPostgresTest.java`, `infrastructure/PartyConfirmPostgresTest.java`, `infrastructure/PartyUnmappedColumnPostgresTest.java`(100: 정원 차도 ACTIVE / 162: 확정 방 1명 남으면 CANCELED), `infrastructure/PartyLockQueryPostgresTest.java:93`, `record/infrastructure/RideRecordQueryPostgresTest.java`(65 finish → depart, 120 노쇼 삭제), `faresplit/FareSplitPostgresTest.java`·`FareSplitSweepPostgresTest.java`(`finishedRoom` 헬퍼 19인자·3인자), `chat/room/application/*IntegrationTest`(Party.create 이름), `ReservationSearchQueryPostgresTest`(헬퍼)
- `partyService.confirm(id, 방장, Instant.now().minusSeconds(30))` 패턴 → `confirm(id, 방장, MeetingPlan.at(now+10분))` 뒤 `jdbc.update("UPDATE match_room SET departure_at = now() - interval '30 seconds' WHERE id = ?", id)`

- [ ] Step 1: 노쇼 컬럼 검증·무효 처리 테스트 삭제, 위 패턴으로 바꿈. 새 테스트: 확정 방에서 1명 나가면 `status = CANCELED`; 확정 후 `departure_lat/lng/place` 가 갱신돼 다시 읽힌다.
- [ ] Step 2: `./gradlew integrationTest --tests 'team.codingforest.moyeota.matching.*' -q` 통과
- [ ] Step 3: 커밋 `test: 바뀐 방 규칙에 맞춰 매칭 통합 테스트를 고친다`

### Task 6: 조회 쿼리 — 정원 찬 방 제외, 자동 정리 기준, 모집 만료 대상

**Files:**
- Modify: `matching/party/infrastructure/PartyJpaRepository.java`
  - `findSummariesWithinBounds`, `findReservations`, `findReservationsWithinBounds`: `and (select count(m) from PartyMemberEntity m where m.party = p) < p.capacity`
  - `findNearbyParties`, `findReservationIdsNearRoute`, `findReservationIdsNearDeparture`(native): `AND (SELECT COUNT(*) FROM user_match_room um WHERE um.match_id = m.id) < m.capacity`
  - `findAutoCloseTargetIds(statuses, cutoff)`: `(p.departureAt is null and p.createdAt < :cutoff) or (p.departureAt is not null and p.departureAt < :cutoff)`
  - `findRecruitingExpiredIds`: `and (select count(m) …) < 2`
- Modify: `PartyJpa.java`(`recruitingStatuses()`·`ongoingStatuses()` → `openStatuses()` 하나)
- Test: `integrationTest/.../infrastructure/NearbyPartyQueryPostgresTest.java`(58 테스트를 "정원이 찬 ACTIVE 방은 후보에서 빠진다"로), `ReservationSearchQueryPostgresTest.java`, `PartyAutoClosePostgresTest.java`, `PartyRecruitingSweeper` 관련 통합 테스트, 유닛 `PartyJpaPersistenceTest:486`

- [ ] Step 1: 통합 테스트 추가: 정원 2 에 2명인 실시간 방은 근처 방·지도 목록에 안 나온다; 정원 찬 예약 방은 예약 목록·검색에 안 나온다; 확정 실시간 방은 `created_at` 이 2일 전이어도 `departure_at` 이 아직이면 자동 정리 대상이 아니고, `departure_at` 이 1일 넘게 지나면 대상이다; 2명인 15분 지난 방은 모집 만료 대상이 아니다.
- [ ] Step 2: 실패 확인
- [ ] Step 3: 쿼리 수정
- [ ] Step 4: `./gradlew integrationTest --tests 'team.codingforest.moyeota.matching.*' -q` 통과
- [ ] Step 5: 커밋 `fix: 정원 찬 방을 목록에서 빼고 확정 방 자동 정리를 약속 시간 기준으로 고른다`

### Task 7: user 모듈 노쇼 제거

**Files:**
- Delete: `user/sanction/**`, `user/api/NoShowPenalty.java`, `user/auth/presentation/security/OperatorTokenFilter.java`, 관련 유닛 테스트(`user/sanction/**`, `OperatorTokenFilterUnitTest`, `LocalUserServiceUnitTest` 의 노쇼 4개 → 파일 삭제 여부는 다른 테스트 유무로), 통합 `user/sanction/**`
- Modify: `user/users/infrastructure/UserEntity.java`(restrictedUntil·메서드·import·@DynamicUpdate 주석), `UserRepository.java`(`findByIdForUpdate` 삭제), `user/local/application/LocalUserService.java`, `user/local/dto/MyProfileResponse.java`(필드 유지, 값 0·null, 주석 "11-03 출시 앱 호환 - 앱이 필드를 빼고 강제 업데이트된 뒤 삭제"), `LocalUserController.java` 설명, `user/auth/presentation/security/SecurityConfig.java`(운영자 체인 삭제), `user/common/exception/UserErrorCode.java`(`OPERATOR_UNAUTHORIZED` 삭제), `src/main/resources/application.yaml:125-128`(`moyeota.operator.token` 삭제)
- Test: `LocalUserServiceTest`, `LocalUserControllerUnitTest`(noShowCount 0·restrictedUntil null 단언), `integrationTest/common/OpenApiDocsTest.java:90-107` 운영자·노쇼 메서드 삭제

- [ ] Step 1: 테스트 수정 → 컴파일 실패 확인 → 구현
- [ ] Step 2: `./gradlew test --tests 'team.codingforest.moyeota.user.*' -q` 와 `ModularityTest` 통과
- [ ] Step 3: 커밋 `chore: user 모듈의 노쇼 제재와 운영자 토큰 체인을 지운다`

### Task 8: 정산·채팅 쪽 문구와 테스트

**Files:**
- Modify: `faresplit/FareSplitController.java:28,38` 설명("출발지 도착을 누른 멤버"), `http/faresplit.http:4`
- Test: `unitTest/.../faresplit/domain/FareSplitUnitTest.java`(노쇼 탑승자 테스트·헬퍼 삭제 — `RideRoster.riderIds` 에 노쇼 개념이 없어짐)

- [ ] Step 1: 수정 후 `./gradlew test --tests 'team.codingforest.moyeota.faresplit.*' -q` 통과
- [ ] Step 2: 커밋 `docs: 정산 설명에서 노쇼 문장을 지운다`

### Task 9: 마이그레이션 V37

**Files:**
- Create: `src/main/resources/db/migration/V37__drop_no_show_and_reset_unconfirmed_completed.sql`
  ```sql
  -- 노쇼 기능 제거(2026-11-10 설계 문서). dev 에만 나갔던 기능이라 데이터 이전은 없다
  DROP TABLE no_show_objection;
  DROP TABLE user_no_show;
  ALTER TABLE users DROP COLUMN no_show_count, DROP COLUMN no_show_count_updated_at, DROP COLUMN restricted_until;
  ALTER TABLE user_match_room DROP COLUMN no_show_at, DROP COLUMN no_show_voided_at;
  -- COMPLETED 는 이제 "약속 확정"만 뜻한다. 정원이 차거나 15분이 지나 COMPLETED 가 된 미확정 실시간 방과
  -- 출발 시각에 COMPLETED 가 된 예약 방은 모집 중(ACTIVE)으로 되돌린다 - 열린 방은 자동 정리(약속·생성 1일 뒤)가 닫는다
  UPDATE match_room SET status = 'ACTIVE' WHERE status = 'COMPLETED' AND (departure_at IS NULL OR room_type = 'RESERVED');
  ```
- Modify: `integrationTest/.../DriverTablesDropPostgresTest.java:33` LIVE 목록에서 `no_show_objection` 삭제
- Test: `integrationTest/.../matching/party/infrastructure/` 에 `NoShowDropMigrationPostgresTest`(컬럼·테이블 없음, 되돌린 방 상태 확인은 Flyway 가 이미 적용된 DB 라 정보 스키마 조회로)

- [ ] Step 1: 테스트 작성 → 통합 테스트 실행(Flyway 가 V37 적용) → 통과
- [ ] Step 2: 커밋 `chore: 노쇼 테이블·컬럼을 지우고 미확정 COMPLETED 방을 ACTIVE 로 되돌린다(V37)`

### Task 10: 문서·http·전체 검증

**Files:**
- Modify: `http/matching.http`(7·9 설명, 10~14 삭제, confirm 예시에 장소 필드), `CLAUDE.md` 는 손대지 않음
- 메모리 갱신은 세션에서 따로

- [ ] Step 1: `./gradlew build --no-daemon -q`(유닛+통합) 통과
- [ ] Step 2: `git grep -n -i 'noshow\|no_show\|restricted\|objection\|finishUnconfirmed\|isRecruiting\|RecruitingClosed\|CompletionPolicy' -- src http` 결과 0건(V26·V27·V34 마이그레이션 파일 제외)
- [ ] Step 3: 커밋 `docs: 바뀐 방 규칙에 맞춰 http 예시를 고친다`
- [ ] Step 4: `push` 는 사용자 지시 뒤에만
