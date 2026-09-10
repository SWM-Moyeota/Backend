-- V2: chat 컨벤션 통일(#83) - 컬럼명 변경 + 감사 타임스탬프 추가
-- 이 변경이 prod 에 없어서 2026-09-10 배포가 Hibernate validate 에서 실패했다. Flyway 도입의 직접 계기.

ALTER TABLE chat_room RENAME COLUMN departure   TO departure_place;
ALTER TABLE chat_room RENAME COLUMN destination TO destination_place;
ALTER TABLE chat_room ADD COLUMN updated_at timestamp(6) with time zone NOT NULL DEFAULT now();

-- created_at 은 기존 joined_at 값으로 채운다. joined_at 은 엔티티에서 빠졌지만 데이터 보존을 위해 컬럼은 남긴다
-- (Hibernate validate 는 엔티티에 없는 여분 컬럼을 문제 삼지 않는다)
ALTER TABLE chat_room_user ADD COLUMN created_at timestamp(6) with time zone;
UPDATE chat_room_user SET created_at = joined_at WHERE created_at IS NULL;
ALTER TABLE chat_room_user ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE chat_room_user ADD COLUMN updated_at timestamp(6) with time zone NOT NULL DEFAULT now();
