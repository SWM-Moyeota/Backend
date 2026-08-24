-- "user" 테이블에서 role 컬럼을 없앤다.
--
-- [이 파일이 필요한 사람]
-- 이 변경 이전부터 쓰던 DB를 그대로 들고 있는 사람만 한 번 실행하면 된다.
-- docker compose down -v 로 DB를 새로 만들거나 처음 받는 사람은 실행할 필요가 없다.
--
-- [왜 ddl-auto=update 가 알아서 못 하나]
-- 앞선 마이그레이션들(컬럼 추가)과는 반대 이유다.
-- ddl-auto=update 는 "엔티티에는 있는데 DB에 없는 것"만 만들어줄 뿐,
-- "DB에는 있는데 엔티티에 없는 것"은 절대 지우지 않는다.
-- 실수로 컬럼을 지워 데이터를 날리는 것을 막기 위한 안전장치다.
-- 그래서 엔티티에서 필드를 빼도 DB의 role 컬럼은 그대로 남는다.
-- 앱은 정상 동작하지만(Hibernate가 모르는 컬럼은 무시한다) 스키마에 쓰레기가 남으므로 여기서 직접 지운다.
--
-- [지워도 괜찮은 근거]
-- 이 컬럼은 가입할 때 PASSENGER 를 넣기만 하고 읽는 코드가 한 군데도 없었다.
-- JWT 의 role 클레임은 UserService.SECURITY_ROLE("ROLE_USER") 문자열이라 이것과 무관하다.
-- 즉 모든 행의 값이 'PASSENGER' 로 같고 아무도 보지 않던 값이라, 잃는 정보가 없다.
--
-- [실행]
--   docker exec -i moyeota-postgres psql -U moyeota -d moyeota -f - < docs/schema-drop-user-role.sql
--
-- 이미 실행했더라도 다시 돌려도 괜찮다(if exists 라 두 번째부터는 아무 일도 하지 않는다).
--
-- user 는 PostgreSQL 예약어라 반드시 따옴표로 감싸야 한다.

BEGIN;

ALTER TABLE "user" DROP COLUMN IF EXISTS role;

COMMIT;
