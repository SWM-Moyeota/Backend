-- 토스 지급대행은 셀러 id 로 보내므로 계좌번호 원문은 지우고, 요청한 시점의 계좌는 가린 번호로만 남긴다
ALTER TABLE payout ADD COLUMN seller_id varchar(255);
ALTER TABLE payout ADD COLUMN masked_account_number varchar(255);

UPDATE payout SET masked_account_number = CASE
                                              WHEN length(account_number) <= 4 THEN repeat('*', length(account_number))
                                              ELSE repeat('*', length(account_number) - 4) || right(account_number, 4)
END;

ALTER TABLE payout ALTER COLUMN masked_account_number SET NOT NULL;
ALTER TABLE payout DROP COLUMN account_number;
