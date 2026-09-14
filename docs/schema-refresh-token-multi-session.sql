-- 사용자 한 명이 여러 로그인 세션의 refresh token을 동시에 유지할 수 있게 한다.
-- 기존 PostgreSQL DB를 계속 사용하는 환경에서 한 번 실행한다.
-- 새 DB는 RefreshToken 엔티티를 기준으로 만들어지므로 이 파일을 실행할 필요가 없다.

BEGIN;

ALTER TABLE refresh_token
    DROP CONSTRAINT IF EXISTS uk_refresh_token_user_id;

ALTER TABLE refresh_token
    DROP CONSTRAINT IF EXISTS uk_refresh_token_public_id;

DROP INDEX IF EXISTS uk_refresh_token_user_id;
DROP INDEX IF EXISTS uk_refresh_token_public_id;

--사용자 식별자는 중복을 허용하지만, 토큰 문자열 자체는 한 행만 가리켜야 한다.
CREATE UNIQUE INDEX IF NOT EXISTS uk_refresh_token_value
    ON refresh_token (refresh_token);

COMMIT;
