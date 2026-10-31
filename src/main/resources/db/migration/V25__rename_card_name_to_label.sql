-- 카드 표시 이름의 DB 컬럼을 Java 필드와 동일하게 맞춘다.
ALTER TABLE card RENAME COLUMN name TO label;
