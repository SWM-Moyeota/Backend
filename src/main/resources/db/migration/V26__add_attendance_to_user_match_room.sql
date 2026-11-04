-- 출발지 도착·노쇼. 약속이 정해진 방에서만 채워지고 그 밖의 멤버 행은 계속 NULL 이다
--   arrived_at        : 약속 시간 뒤 출발지 100m 안에서 "도착했어요"를 누른 시각(처음 누른 시각)
--   no_show_at        : 누군가 "출발했어요"를 눌러 방이 끝난 순간 도착하지 않았던 멤버에게 찍힌 시각
--   no_show_voided_at : 운영자가 노쇼를 무효로 돌린 시각. 채워져 있으면 노쇼가 아니다
-- 탑승 기록·탑승 횟수는 no_show_at IS NOT NULL AND no_show_voided_at IS NULL 인 멤버를 뺀다
ALTER TABLE user_match_room
    ADD COLUMN arrived_at timestamp(6) with time zone,
    ADD COLUMN no_show_at timestamp(6) with time zone,
    ADD COLUMN no_show_voided_at timestamp(6) with time zone;
