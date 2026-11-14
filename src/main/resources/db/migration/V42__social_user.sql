-- 소셜 로그인 수단. "이 회원은 어느 소셜 계정인가"만 담는다 (local_user 의 소셜 버전)
-- user_id 는 users.id 와 같은 값이다. local_user 와 같이 FK 는 걸지 않는다
-- social_id 는 소셜 서비스가 주는 고정 식별자(구글은 sub). 이메일은 바뀔 수 있어 식별에 쓰지 않는다
-- social_ci 는 본인인증 연계정보용. 구글은 주지 않아 비워 둔다
CREATE TABLE social_user (
    user_id     bigint       NOT NULL,
    social_id   varchar(255) NOT NULL,
    social_ci   varchar(255),
    social_type varchar(20)  NOT NULL CHECK (social_type IN ('KAKAO', 'GOOGLE', 'NAVER')),
    created_at  timestamp(6) with time zone NOT NULL,
    PRIMARY KEY (user_id),
    -- 같은 소셜 계정으로 가입이 동시에 두 번 들어와도 두 번째 insert 가 실패해 한 계정이 두 회원이 되지 않는다
    CONSTRAINT uk_social_user_type_social_id UNIQUE (social_type, social_id)
);
