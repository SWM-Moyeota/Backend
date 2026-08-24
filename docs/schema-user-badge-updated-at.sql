-- "user" 테이블에 badge_id, updated_at 컬럼을 추가한다.
--
-- [이 파일이 필요한 사람]
-- 이 변경 이전부터 쓰던 DB를 그대로 들고 있는 사람만 한 번 실행하면 된다.
-- docker compose down -v 로 DB를 새로 만들거나 처음 받는 사람은 실행할 필요가 없다.
-- Hibernate가 테이블을 만들 때 이 컬럼까지 같이 만들어주기 때문이다.
--
-- [왜 ddl-auto=update 가 알아서 못 하나]
-- badge_id 는 nullable 이라 ddl-auto=update 가 알아서 붙여준다. 문제는 updated_at 이다.
-- Hibernate는 @UpdateTimestamp 컬럼을 not null 로 만들려 하는데,
-- 행이 이미 있는 테이블에는 not null 컬럼을 그냥 붙일 수 없다
-- (기존 행에 넣을 값이 없어서 "column ... contains null values" 로 거절당한다).
-- ddl-auto=update 는 이 실패를 로그에만 남기고 앱은 그대로 띄우기 때문에,
-- 컬럼이 없는 채로 회원가입을 하면 그때서야 500이 난다.
-- 그래서 "컬럼 추가 -> 기존 행 채우기 -> not null 걸기" 세 단계를 여기서 직접 해준다.
-- (자세한 사정은 docs/schema-timestamps.sql 에 같은 내용으로 적어두었다)
--
-- [실행]
--   docker exec -i moyeota-postgres psql -U moyeota -d moyeota -f - < docs/schema-user-badge-updated-at.sql
--
-- 이미 실행했더라도 다시 돌려도 괜찮다(if not exists / is null 조건이라 두 번째부터는 아무 일도 하지 않는다).
--
-- user 는 PostgreSQL 예약어라 반드시 따옴표로 감싸야 한다.

BEGIN;

-- 대표 뱃지. 아직 아무것도 안 걸어둔 상태가 정상이므로 null 을 허용한다.
-- badge 테이블이 생기면 그때 FK 제약을 따로 걸어주면 된다.
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS badge_id bigint;

ALTER TABLE "user" ADD COLUMN IF NOT EXISTS updated_at timestamp(6);

-- 기존 행이 마지막으로 바뀐 시각은 어디에도 남아 있지 않다.
-- not null 을 걸려면 무언가는 채워야 하므로 "이 마이그레이션을 돌린 시각"을 넣는다.
-- 실제 수정 시각이 아니라는 뜻이니, 통계 같은 데 쓸 때는 이 점을 감안해야 한다.
UPDATE "user" SET updated_at = now() WHERE updated_at IS NULL;

-- 여기까지 오면 빈 값이 없으므로 not null 을 걸 수 있다.
-- 새로 만들어지는 DB의 스키마와 모양을 맞춰두는 것이 목적이다.
ALTER TABLE "user" ALTER COLUMN updated_at SET NOT NULL;

COMMIT;
