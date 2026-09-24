-- event_publication.serialized_event 가 varchar(255) 라 ChatMessageSentEvent(JSON 골격 약 183자) 는
-- content 약 72자부터 INSERT 가 실패하고 메시지 전송 트랜잭션이 롤백된다 (HTTP 500).
-- 실측: docs/evidence/outbox/01-serialized-event-255-overflow.txt
-- Spring Modulith 공식 스키마와 같이 TEXT 로 변경. 기존 데이터 변환 없음 (varchar → text 는 재작성 없이 즉시 적용).
ALTER TABLE event_publication ALTER COLUMN serialized_event TYPE text;
