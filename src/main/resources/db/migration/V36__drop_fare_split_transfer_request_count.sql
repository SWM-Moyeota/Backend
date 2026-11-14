-- 송금 재요청을 시작 시각부터 센 차례(request_count)가 아니라 마지막 송금 요청 시각(last_requested_at) 4시간 뒤로 고른다
--   request_count     : 더 쓰지 않아 지운다
--   last_requested_at : 첫 송금 요청부터 늘 채운다(재요청·못 받았어요 알림 때 바뀐다). 비어 있으면 재요청 대상에서 조용히 빠지므로 NOT NULL 로 막는다
-- prod 에는 아직 fare_split 이 없어 V35 와 함께 처음 적용된다. 바로 지워도 지금 도는 main jar 와 롤백 대상은 이 테이블을 매핑하지 않는다
-- dev 는 AllAtOnce 배포라 옛 jar 와 섞여 돌지 않는다. 다만 이 전 develop jar 를 손으로 다시 올리면 request_count 가 없어 validate 에서 기동이 실패한다
UPDATE fare_split_transfer SET last_requested_at = created_at WHERE last_requested_at IS NULL;

ALTER TABLE fare_split_transfer ALTER COLUMN last_requested_at SET NOT NULL;

ALTER TABLE fare_split_transfer DROP COLUMN request_count;
