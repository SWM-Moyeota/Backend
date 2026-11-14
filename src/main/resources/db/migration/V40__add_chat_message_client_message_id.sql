-- 앱이 메시지마다 만든 UUID. 응답을 못 받아 같은 메시지를 다시 보내도 한 번만 저장하려고 둔다
-- 옛 앱과 STOMP 보내기는 키가 없으므로 비워 둘 수 있다. NULL 끼리는 유일 제약에 걸리지 않는다
ALTER TABLE chat_message ADD COLUMN client_message_id UUID;

ALTER TABLE chat_message
    ADD CONSTRAINT uk_chat_message_user_client_message UNIQUE (user_id, client_message_id);
