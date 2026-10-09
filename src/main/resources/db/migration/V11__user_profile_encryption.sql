-- 이름, 이메일, 전화번호를 앱(EncryptedStringConverter)에서 암호화해 저장한다
-- 기존 평문 행은 백필하지 않는다. 배포 시점에 실제 유저가 없어 테스트 데이터를 비우고 적용한다
-- 행이 남아 있으면 아래 NOT NULL 컬럼 추가가 실패해 배포가 멈춘다 - 평문이 섞여 남지 않게 하는 안전장치

-- 암호문은 평문보다 길다. email 100자 제한이어도 멀티바이트가 섞이면 255 를 넘을 수 있다
ALTER TABLE user_profile ALTER COLUMN email TYPE varchar(512);

-- phone_number 는 암호문이 되어 같은 번호인지 비교할 수 없다. 조회와 중복 검사는 phone_hash(HMAC) 로 한다
ALTER TABLE user_profile ADD COLUMN phone_hash varchar(64) NOT NULL;
ALTER TABLE user_profile ADD CONSTRAINT uk_user_profile_phone_hash UNIQUE (phone_hash);

-- baseline 전 Hibernate 가 만든 DB 는 제약 이름이 달라 이름 대신 컬럼으로 찾는다
DO $$
DECLARE constraint_name text;
BEGIN
    SELECT con.conname INTO constraint_name
    FROM pg_constraint con
    JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = ANY (con.conkey)
    WHERE con.conrelid = 'user_profile'::regclass AND con.contype = 'u' AND att.attname = 'phone_number';

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE user_profile DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;
