-- 채팅방별 메시지 순번. 같은 방 안에서 1부터 1씩 증가한다
-- chat_room.last_seq 는 마지막으로 나눠준 번호. 메시지를 저장할 때 이 행을 UPDATE 해 잠그고 번호를 받는다
ALTER TABLE chat_room ADD COLUMN last_seq bigint NOT NULL DEFAULT 0;
ALTER TABLE chat_message ADD COLUMN seq bigint;

-- 기존 메시지는 id 순서대로 번호를 매긴다
UPDATE chat_message m
SET seq = s.seq
    FROM (SELECT id, row_number() OVER (PARTITION BY chat_room_id ORDER BY id) AS seq FROM chat_message) s
WHERE m.id = s.id;

UPDATE chat_room r
SET last_seq = s.max_seq
    FROM (SELECT chat_room_id, max(seq) AS max_seq FROM chat_message GROUP BY chat_room_id) s
WHERE r.id = s.chat_room_id;

ALTER TABLE chat_message ALTER COLUMN seq SET NOT NULL;
ALTER TABLE chat_message ADD CONSTRAINT uk_chat_message_room_seq UNIQUE (chat_room_id, seq);