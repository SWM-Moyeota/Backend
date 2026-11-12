-- 택시비 정산 도우미. 동승이 끝난 방에서 탑승자 한 명(payer)이 택시비를 다 내고 실제 요금과 받을 계좌를 넣으면
-- 나머지 탑승자가 1/N 을 각자 송금한다. 앱은 돈을 옮기지 않는다. 방마다 하나라 match_id 가 키다
--   payer_id          : 받는 사람. users 는 user 모듈 테이블이라 FK 를 걸지 않는다
--   amount_per_person : 보내는 사람 1인당 금액 = total_fare / 탑승자 수를 10원 단위로 올린 값
--   bank_code         : 받을 계좌의 은행(Bank enum 이름)
--   account_number    : 받을 계좌번호. 앱(EncryptedStringConverter)이 user_profile 개인정보와 같은 AES-GCM 으로 암호화해 넣는다.
--                       정산이 닫히면(COMPLETED·EXPIRED) NULL 로 지운다. 취소는 행을 지운다
--   closed_at         : COMPLETED·EXPIRED 가 된 시각
CREATE TABLE fare_split (
    match_id bigint not null,
    payer_id bigint not null,
    total_fare integer not null,
    amount_per_person integer not null,
    bank_code varchar(20) not null,
    account_number varchar(255),
    status varchar(20) not null check ((status in ('OPEN','COMPLETED','EXPIRED'))),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone,
    closed_at timestamp(6) with time zone,
    primary key (match_id)
);

ALTER TABLE fare_split ADD CONSTRAINT fk_fare_split_match_room FOREIGN KEY (match_id) REFERENCES match_room (id);
-- 만료 스케줄러: OPEN 이고 만든 지 7일 지난 정산
CREATE INDEX idx_fare_split_status_created_at ON fare_split (status, created_at);

-- 보내는 사람마다 한 행(payer 의 행은 없다). 송금 상태는 컬럼이 아니라 시각으로 가른다
-- (confirmed_at 이 있으면 CONFIRMED, 없고 sent_at 이 있으면 SENT, 둘 다 없으면 NOT_SENT)
--   request_count        : 송금 요청 알림을 보낸 횟수(최대 4)
--   last_requested_at    : 마지막 송금 요청 알림 시각
--   confirm_requested_at : 받는 사람에게 입금 확인을 요청한 시각(SENT 24시간 뒤 한 번)
CREATE TABLE fare_split_transfer (
    match_id bigint not null,
    user_id bigint not null,
    sent_at timestamp(6) with time zone,
    confirmed_at timestamp(6) with time zone,
    request_count smallint not null default 0,
    last_requested_at timestamp(6) with time zone,
    confirm_requested_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone,
    primary key (match_id, user_id)
);

ALTER TABLE fare_split_transfer ADD CONSTRAINT fk_fare_split_transfer_fare_split FOREIGN KEY (match_id) REFERENCES fare_split (match_id);
-- 내가 보낼 정산 목록(홈 배너)
CREATE INDEX idx_fare_split_transfer_user_id ON fare_split_transfer (user_id);
