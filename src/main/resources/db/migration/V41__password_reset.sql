ALTER TABLE local_user ADD COLUMN password_reset_generation bigint NOT NULL DEFAULT 0;
CREATE TABLE password_reset_grant (
 id uuid PRIMARY KEY,
 token_hash varchar(64) NOT NULL UNIQUE,
 user_id bigint NOT NULL REFERENCES local_user(id) ON DELETE CASCADE,
 phone_hash varchar(64) NOT NULL,
 reset_generation bigint NOT NULL,
 expires_at timestamp with time zone NOT NULL,
 consumed_at timestamp with time zone,
 created_at timestamp with time zone NOT NULL
);
CREATE INDEX idx_password_reset_grant_user ON password_reset_grant(user_id);
CREATE INDEX idx_password_reset_grant_expiry ON password_reset_grant(expires_at);
