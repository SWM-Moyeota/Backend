-- 토스 지급대행은 셀러 id 로 보내므로 계좌번호 원문은 지우고, 요청한 시점의 계좌는 가린 번호로만 남긴다
ALTER TABLE payout ADD COLUMN seller_id varchar(255);
ALTER TABLE payout ADD COLUMN masked_account_number varchar(255);

UPDATE payout SET masked_account_number = CASE
                                              WHEN length(account_number) <= 4 THEN repeat('*', length(account_number))
                                              ELSE repeat('*', length(account_number) - 4) || right(account_number, 4)
END;

ALTER TABLE payout ALTER COLUMN masked_account_number SET NOT NULL;
ALTER TABLE payout DROP COLUMN account_number;

-- 토스가 접수만 하고 결과는 나중에 주는 동안의 상태
ALTER TABLE payout DROP CONSTRAINT payout_status_check;
ALTER TABLE payout ADD CONSTRAINT payout_status_check
    CHECK (status IN ('REQUESTED', 'ACCEPTED', 'SUCCEED', 'FAILED', 'UNKNOWN'));

-- 웹훅과 대사가 토스 지급 id 로 지급을 찾는다
CREATE UNIQUE INDEX uk_payout_pg_id ON payout (pg_id);

-- 정산서 하나에 진행 중이거나 성공한 지급은 하나뿐. 토스가 접수한 지급도 진행 중이라 넣는다
DROP INDEX uk_payout_settlement_active;
CREATE UNIQUE INDEX uk_payout_settlement_active ON payout (settlement_id) WHERE status IN ('REQUESTED', 'ACCEPTED', 'UNKNOWN', 'SUCCEED');
