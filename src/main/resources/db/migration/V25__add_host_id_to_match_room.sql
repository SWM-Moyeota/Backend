-- 방장 userId. 멤버가 하나도 없는 방(마지막 멤버가 나가 CANCELED 된 방 등)은 방장도 없어 NULL 을 허용한다
ALTER TABLE match_room ADD COLUMN host_id bigint;

-- 백필: 지금까지 방장은 저장하지 않고 "가장 먼저 합류한 현재 멤버(같은 시각이면 user_id 가 작은 쪽)"로 계산했다.
-- 기존 방은 같은 규칙으로 채우고, 멤버가 없는 방은 대상에서 빠져 NULL 로 남는다
UPDATE match_room m
SET host_id = first_member.user_id
FROM (
    SELECT DISTINCT ON (match_id) match_id, user_id
    FROM user_match_room
    ORDER BY match_id, joined_at, user_id
) first_member
WHERE first_member.match_id = m.id;
