-- favorite_place 를 테이블 정의서에 맞춘다
--   latitude   Decimal(11,8)   (float(53) → numeric)
--   longitude  Decimal(10,6)   (float(53) → numeric)
--   created_at LocalDateTime   (timestamptz → timestamp, 세션 타임존 기준으로 변환)
--   updated_at LocalDateTime
ALTER TABLE favorite_place
    ALTER COLUMN latitude   TYPE numeric(11, 8),
    ALTER COLUMN longitude  TYPE numeric(10, 6),
    ALTER COLUMN created_at TYPE timestamp(6) without time zone,
    ALTER COLUMN updated_at TYPE timestamp(6) without time zone;
