-- 합승이 끝나면 채팅방을 FINISHED(같이 탐) 또는 CANCELED(같이 타지 못함)로 바꾼다. 대화는 CLOSED 가 되기 전까지 계속 된다
-- 제약 이름은 V1 의 이름 없는 컬럼 CHECK 에 PostgreSQL 이 붙인 기본 이름이다. 이름이 다르면 조용히 넘어가지 않고 실패하도록 IF EXISTS 를 쓰지 않는다
ALTER TABLE chat_room DROP CONSTRAINT chat_room_status_check;
ALTER TABLE chat_room ADD CONSTRAINT chat_room_status_check
    CHECK (status IN ('ACTIVE', 'FINISHED', 'CANCELED', 'CLOSED', 'ARCHIVED'));

-- 이미 끝난 파티의 채팅방을 파티 상태에 맞춘다
UPDATE chat_room cr
SET status = m.status,
    updated_at = now()
FROM match_room m
WHERE m.id = cr.party_id
  AND cr.status = 'ACTIVE'
  AND m.status IN ('FINISHED', 'CANCELED');
