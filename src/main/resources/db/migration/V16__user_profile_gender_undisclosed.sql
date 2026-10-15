-- 가입 때 성별 "선택 안 함"을 고를 수 있게 UNDISCLOSED 를 허용한다. 기존 MALE·FEMALE 행은 그대로 둔다
-- 제약 이름은 V1 의 이름 없는 컬럼 CHECK 에 PostgreSQL 이 붙인 기본 이름이다. 이름이 다르면 조용히 넘어가지 않고 실패하도록 IF EXISTS 를 쓰지 않는다
ALTER TABLE user_profile DROP CONSTRAINT user_profile_gender_check;
ALTER TABLE user_profile ADD CONSTRAINT user_profile_gender_check CHECK (gender IN ('MALE', 'FEMALE', 'UNDISCLOSED'));
