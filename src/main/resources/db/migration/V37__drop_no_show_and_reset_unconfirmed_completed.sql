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

-- 탄 사람은 도착한 멤버다. 도착 기능 전에 끝난 FINISHED 방은 도착 기록이 하나도 없어 그때 규칙대로 멤버 전원을 탄 사람으로 채운다.
-- 이제 FINISHED 방에는 늘 도착한 멤버가 있다(출발은 도착한 방장만, 자동 정리는 도착한 멤버가 있을 때만 FINISHED)
UPDATE user_match_room m
SET arrived_at = r.updated_at
FROM match_room r
WHERE m.match_id = r.id
  AND r.status = 'FINISHED'
  AND NOT EXISTS (SELECT 1 FROM user_match_room a WHERE a.match_id = r.id AND a.arrived_at IS NOT NULL);

-- 코드가 읽지도 쓰지도 않는 컬럼 - 알림 처리 표시(약속 시각 구간으로 대신 고른다), 반경(서버 상수), 택시 기능의 흔적
ALTER TABLE match_room
    DROP COLUMN reminder_sent_at,
    DROP COLUMN meeting_notified_at,
    DROP COLUMN departure_reminder_count,
    DROP COLUMN departure_radius,
    DROP COLUMN destination_radius,
    DROP COLUMN completed_at,
    DROP COLUMN match_started_at,
    DROP COLUMN taxi_driver_id;

ALTER TABLE ride_record
    DROP COLUMN taxi_driver_id,
    DROP COLUMN pickup_time,
    DROP COLUMN total_fare;
