-- 택시비 정산 테이블 정리
--   fare_split_transfer 는 탑승자 전원의 장부다 - 받는 사람(payer)의 줄도 둔다
--   fare_split_transfer.payment       : 그 사람이 택시비 중 부담하는 금액. 정산에 한 번 두던 1인당 금액(fare_split.amount_per_person)을 줄마다 옮긴다
--   fare_split_transfer.request_count : 송금 요청 차례가 몇 번째까지 지났는지(첫 요청이 1, 최대 6). 다음 재요청은 정산 시작 시각 + 횟수 × 4시간이고 밀린 차례는 건너뛴다
--   last_requested_at                 : 재요청 시점을 request_count 로 세게 돼 지운다
--   confirm_requested_at              : 보냈어요를 누르는 순간 받는 사람에게 바로 알려서 4시간 뒤 입금 확인 요청이 없어졌다
ALTER TABLE fare_split_transfer ADD COLUMN payment integer;

UPDATE fare_split_transfer t
SET payment = s.amount_per_person
FROM fare_split s
WHERE s.match_id = t.match_id;

ALTER TABLE fare_split_transfer ALTER COLUMN payment SET NOT NULL;

-- 이미 있는 송금은 마지막 요청 시각으로 보낸 횟수를 되짚는다(4시간마다 한 번, 첫 요청 포함)
ALTER TABLE fare_split_transfer ADD COLUMN request_count integer;

UPDATE fare_split_transfer
SET request_count = LEAST(6, 1 + FLOOR(EXTRACT(EPOCH FROM (last_requested_at - created_at)) / 14400)::integer);

ALTER TABLE fare_split_transfer ALTER COLUMN request_count SET NOT NULL;

-- 이미 있는 정산에 받는 사람의 줄을 넣는다 - 택시비에서 다른 사람들의 금액을 뺀 만큼 부담했고, 이미 낸 것이라 CONFIRMED, 송금 요청은 0번
INSERT INTO fare_split_transfer (match_id, user_id, payment, sent_at, confirmed_at, request_count, last_requested_at, created_at, updated_at)
SELECT s.match_id, s.payer_id,
       s.total_fare - COALESCE((SELECT SUM(t.payment) FROM fare_split_transfer t WHERE t.match_id = s.match_id), 0),
       s.created_at, s.created_at, 0, s.created_at, s.created_at, s.created_at
FROM fare_split s;

ALTER TABLE fare_split_transfer
    DROP COLUMN last_requested_at,
    DROP COLUMN confirm_requested_at;

ALTER TABLE fare_split DROP COLUMN amount_per_person;
