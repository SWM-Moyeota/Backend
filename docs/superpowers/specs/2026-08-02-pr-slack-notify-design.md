# PR Slack 알림 워크플로우 설계

- 날짜: 2026-08-02
- 상태: 승인됨 (황성윤)

## 목적

`SWM-Moyeota/Backend` 레포에 PR이 올라오거나 머지되면 팀 Slack 채널로 알림을 보낸다.
리뷰어는 실제 Slack 멘션(`<@멤버ID>`)으로 태그해서 개인 알림이 가도록 한다.

## 방식 결정

- **GitHub Actions + Slack Incoming Webhook** (GitHub 공식 Slack 앱 대신 선택 — 메시지 포맷·멘션 커스텀 필요)
- 전송 액션은 Slack 공식 `slackapi/slack-github-action@v4` 사용
  - 서드파티 액션 대비 유지보수 안정성
  - PR 제목 특수문자 이스케이프 문제를 피하려고 payload는 `actions/github-script`에서 `JSON.stringify`로 생성

## 동작

| 이벤트 | 조건 | 알림 |
|---|---|---|
| `opened`, `reopened`, `ready_for_review` | draft 아님 | 🔔 새 PR: 제목(링크), 작성자, `head → base`, 리뷰어 Slack 멘션 |
| `closed` | `merged == true` | ✅ 머지 완료: 제목(링크), 브랜치, 머지한 사람 |

- draft PR은 오픈 시점엔 조용히 넘어가고, draft 해제(`ready_for_review`) 시점에 알림
- 그냥 닫힌(머지 안 된) PR은 알림 없음
- 리뷰어가 매핑에 없거나 Slack ID 미등록이면 GitHub 유저네임 텍스트로 폴백
- 알림 실패가 CI를 막지 않도록 전송 스텝에 `continue-on-error: true`

## 구성 요소

- `.github/workflows/pr-notify.yml` — 워크플로우 1개 (job 1개: payload 생성 스텝 + 전송 스텝)
- 팀원 3명의 GitHub 유저네임 ↔ Slack 멤버 ID 매핑은 워크플로우 안 상수로 하드코딩

## 사전 준비 (수동)

1. Slack 앱 생성 → 알림 채널에 Incoming Webhook 추가 → URL을 레포 시크릿 `SLACK_WEBHOOK_URL`로 등록
2. 팀원 3명 Slack 멤버 ID(프로필 → "멤버 ID 복사", `U`로 시작)를 워크플로우의 `MEMBERS` 상수에 기입

## 검증

별도 테스트 코드 없음. 머지 후 실제 PR 1건으로 오픈/머지 알림 각각 확인.
