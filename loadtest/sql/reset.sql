-- 부하테스트 실행 전 정리. 운영 EC2 의 psql 에서 그대로 붙여 넣는다.
-- 실행을 중간에 끊었다면 반드시 다시 돌린다 - 정원이 찬 방은 자동 종료 스윕이 돌 때까지 COMPLETED 로 남는다.

-- 0) 대상 확인 - 테스트 사용자(닉네임이 lt 로 시작)만 잡히는지 본다
SELECT count(*) AS test_users FROM users WHERE nickname LIKE 'lt%';

-- 1) 열려 있는 테스트 방을 닫는다.
--    COMPLETED 를 빼먹으면 그 멤버가 "이미 진행 중인 방이 있다"(409)로 새 방을 못 만든다 -
--    방 생성의 대부분이 거절되고, 서버는 한가해 보이지만 여정은 돌지 않은 실행이 된다.
UPDATE match_room SET status = 'CANCELED', updated_at = now()
 WHERE status IN ('ACTIVE', 'COMPLETED') AND destination LIKE 'LT-%';

-- 2) 처리되지 못한 이벤트를 지운다. 남겨 두면 재제출기가 지난 실행의 이벤트를 다음 실행 중에 다시 돌린다.
DELETE FROM event_publication WHERE completion_date IS NULL;

-- 3) 테스트 사용자를 옛 채팅방에서 내보낸다.
--    방이 끝나도 채팅방에는 남아 있어 반복할수록 /chat-rooms/me 응답이 길어진다.
UPDATE chat_room_user SET left_at = now(), updated_at = now()
 WHERE left_at IS NULL AND user_id IN (SELECT id FROM users WHERE nickname LIKE 'lt%');

-- 4) 확인 - 열린 테스트 방 0, 미완료 이벤트 0 이어야 한다
SELECT (SELECT count(*) FROM match_room WHERE status IN ('ACTIVE', 'COMPLETED') AND destination LIKE 'LT-%') AS open_test_rooms,
       (SELECT count(*) FROM event_publication WHERE completion_date IS NULL) AS incomplete_events;
