-- 프로필 이미지를 URL 대신 S3 키(profile/...)로 저장하도록 바꿨다. 예전 PATCH 로 저장된 URL 은 응답에서 깨진 주소가 된다
-- profile/ 로 시작하지 않는 값은 비워 기본 이미지로 돌린다. 제공 이미지(profile/presets/)와 이후 사용자 업로드(profile/users/)는 모두 이 규칙 안에 둔다
UPDATE users SET image_url = NULL
WHERE image_url IS NOT NULL AND image_url NOT LIKE 'profile/%';