-- 기사 탈퇴는 taxi_driver 행을 180일 남기는 soft delete 다. 신고·사고 대응에 자격번호·번호판·user_id 가 필요하다
-- 탈퇴 즉시 계좌와 FCM 토큰은 비우고 콜 수신을 끈다. 보관 기간이 지나면 정리 스케줄러가 taxi·driver_setting 까지 행째 지운다
ALTER TABLE taxi_driver ADD COLUMN deleted_at timestamp(6) with time zone;
ALTER TABLE taxi_driver ALTER COLUMN bank_name DROP NOT NULL;
ALTER TABLE taxi_driver ALTER COLUMN bank_number DROP NOT NULL;

-- 이 기능 전에 탈퇴한 사용자의 기사 행이 남아 있으면 같은 상태로 맞춘다
UPDATE taxi_driver d
SET deleted_at = u.deleted_at, bank_name = NULL, bank_number = NULL, fcm_token = NULL
FROM users u
WHERE u.id = d.user_id AND u.deleted_at IS NOT NULL;

UPDATE driver_setting s
SET call_enabled = false
FROM taxi_driver d
WHERE d.id = s.driver_id AND d.deleted_at IS NOT NULL;
