-- 가입 시 입력한 프로필과 검증된 신원은 분리한다. 개인정보 원문은 저장하지 않는다.
CREATE TABLE identity_verification_request (
    id varchar(64) PRIMARY KEY,
    user_id bigint NOT NULL REFERENCES users(id),
    store_id varchar(255) NOT NULL,
    channel_key varchar(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT ck_identity_request_expiry CHECK (expires_at > created_at),
    CONSTRAINT uq_identity_request_owner UNIQUE (id, user_id)
);
CREATE INDEX idx_identity_request_user_created ON identity_verification_request(user_id, created_at);

CREATE TABLE verified_identity (
    user_id bigint PRIMARY KEY REFERENCES users(id),
    request_id varchar(64) NOT NULL UNIQUE,
    di_hash varchar(64) NOT NULL UNIQUE,
    verified_at timestamp(6) with time zone NOT NULL,
    completed_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT fk_verified_identity_request_owner FOREIGN KEY (request_id, user_id)
        REFERENCES identity_verification_request(id, user_id)
);
