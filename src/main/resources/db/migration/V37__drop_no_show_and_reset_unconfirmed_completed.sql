-- 노쇼 기능 제거 (docs/superpowers/specs/2026-11-10-party-status-no-show-removal-design.md)
-- 노쇼 기록·제재·이의 신청은 dev 에만 나갔던 기능이라 운영 데이터 이전은 없다. V26·V27·V34 는 dev 에 적용돼 있어 파일을 남긴다
DROP TABLE no_show_objection;
DROP TABLE user_no_show;

ALTER TABLE users
    DROP COLUMN no_show_count,
    DROP COLUMN no_show_count_updated_at,
    DROP COLUMN restricted_until;

ALTER TABLE user_match_room
    DROP COLUMN no_show_at,
    DROP COLUMN no_show_voided_at;

-- COMPLETED 는 이제 "방장이 약속을 확정한 실시간 방"만 뜻한다. 정원이 차거나 15분이 지나 COMPLETED 가 된 미확정 실시간 방(departure_at 없음)과
-- 출발 시각에 COMPLETED 가 된 예약 방은 모집 중(ACTIVE)으로 되돌린다. 열린 방은 자동 정리(약속·생성 1일 뒤)가 닫는다
UPDATE match_room
SET status = 'ACTIVE'
WHERE status = 'COMPLETED'
  AND (departure_at IS NULL OR room_type = 'RESERVED');

ALTER TABLE match_room
DROP COLUMN reminder_sent_at,
    DROP COLUMN meeting_notified_at,
    DROP COLUMN departure_reminder_count;