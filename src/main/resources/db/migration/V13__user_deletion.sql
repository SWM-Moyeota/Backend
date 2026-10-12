-- 회원 탈퇴는 users 행을 남기는 soft delete 다. 채팅 메시지·신고가 user_id 를 가리켜 행을 지우지 않는다
-- 탈퇴 시 닉네임·이미지·FCM 토큰은 비우고, user_profile·local_user·favorite_place 는 행째 지운다
ALTER TABLE users ADD COLUMN deleted_at timestamp(6) with time zone;

-- 탈퇴한 번호의 재가입을 30일 막는다. user_profile 이 지워져 번호를 알 수 없으니 phone_hash 만 남긴다
-- 같은 번호가 여러 번 탈퇴할 수 있어 phone_hash 를 PK 로 두고 다시 탈퇴하면 deleted_at 만 갱신한다
CREATE TABLE user_deletion (
    phone_hash varchar(64) PRIMARY KEY,
    deleted_at timestamp(6) with time zone NOT NULL
);