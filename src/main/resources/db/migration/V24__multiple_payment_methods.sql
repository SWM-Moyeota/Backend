-- 기존 활성 카드는 기본 카드로 승계하고 여러 카드 보관을 허용한다.
ALTER TABLE payment_method ADD COLUMN is_default boolean NOT NULL DEFAULT false;
UPDATE payment_method SET is_default = true WHERE deleted_at IS NULL;
DROP INDEX uq_payment_method_active_user;
CREATE UNIQUE INDEX uq_payment_method_default_user ON payment_method (user_id)
    WHERE deleted_at IS NULL AND is_default = true;
ALTER TABLE payment_method ADD CONSTRAINT ck_payment_method_default_active
    CHECK (NOT is_default OR deleted_at IS NULL);
