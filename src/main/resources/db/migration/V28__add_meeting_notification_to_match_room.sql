-- 약속 시각 알림·출발 재촉. 약속이 정해진 방(확정한 실시간 방, 모든 예약 방)에서만 쓴다
--   meeting_notified_at      : 약속 시각 알림을 처리한 시각. 30분 넘게 늦어 보내지 않은 경우도 처리로 남긴다
--   departure_reminder_count : 처리한 출발 재촉 차례 수(0..4). 다음 재촉은 departure_at + [30분, 1시간, 2시간, 4시간][이 값]
--                              방해 금지 시간(23~08시)이거나 도착한 멤버가 없어 보내지 않은 차례도 센다
-- 이미 열려 있는 방은 NULL/0 으로 시작한다 - 약속 시각에서 30분 넘게 지난 방은 알림 없이 처리만 되고, 재촉은 밀린 차례를 한 번에 넘긴다
ALTER TABLE match_room
    ADD COLUMN meeting_notified_at timestamp(6) with time zone,
    ADD COLUMN departure_reminder_count smallint NOT NULL DEFAULT 0;

-- 1분마다 도는 약속 시각 알림·출발 재촉 조회용. match_room 은 끝난 방(FINISHED·CANCELED)이 계속 쌓이지만 대상은 열린 확정 방뿐이고,
-- 그런 방은 자동 정리(1일)로 닫혀 늘 소수다 - 부분 인덱스로 열린 확정 방만 담아 조회가 테이블 크기와 상관없이 그 수만큼만 훑게 한다.
-- 기존 idx_match_room_reserved_departure 는 예약 방만 담아 확정한 실시간 방을 고를 수 없다
CREATE INDEX idx_match_room_open_confirmed_departure
    ON match_room (departure_at)
    WHERE status IN ('ACTIVE', 'COMPLETED') AND departure_at IS NOT NULL;
