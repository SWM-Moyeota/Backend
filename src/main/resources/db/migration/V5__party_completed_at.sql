ALTER TABLE match_room ADD COLUMN completed_at timestamp(6) with time zone;

-- 컬럼 도입 전에 이미 COMPLETED 인 행 - 마지막 수정 시각을 기준으로 삼는다
UPDATE match_room SET completed_at = updated_at WHERE status = 'COMPLETED' AND completed_at IS NULL;