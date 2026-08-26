-- 방 20개 × 각 5만 건 = 100만 건
-- id를 라운드로빈으로 배분해 방별 id가 연속되지 않게 함 (실제 채팅과 유사)
TRUNCATE chat_message RESTART IDENTITY;

INSERT INTO chat_message (chat_room_id, user_id, content, type, status, created_at)
SELECT
    (i % 20) + 1,
    (i % 4) + 1,
    'benchmark message ' || i,
    'TEXT',
    'ACTIVE',
    now() - (interval '1 second' * (1000000 - i))
FROM generate_series(1, 1000000) AS i;

ANALYZE chat_message;
