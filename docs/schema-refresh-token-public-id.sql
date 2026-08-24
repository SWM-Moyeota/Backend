-- refresh_token 테이블에 public_id 컬럼을 추가한다.
--
-- [이 파일이 필요한 사람]
-- 이 변경 이전부터 쓰던 DB를 그대로 들고 있는 사람만 한 번 실행하면 된다.
-- docker compose down -v 로 DB를 새로 만들거나 처음 받는 사람은 실행할 필요가 없다.
-- Hibernate가 테이블을 만들 때 이 컬럼까지 같이 만들어주기 때문이다.
--
-- [왜 ddl-auto=update 가 알아서 못 하나]
-- 엔티티에서 이 컬럼을 not null 로 선언했는데,
-- 행이 이미 있는 테이블에는 not null 컬럼을 그냥 붙일 수 없다
-- (기존 행에 넣을 값이 없어서 "column ... contains null values" 로 거절당한다).
-- ddl-auto=update 는 이 실패를 로그에만 남기고 앱은 그대로 띄우므로,
-- 컬럼이 없는 채로 로그인을 하면 그때서야 500이 난다.
-- 그래서 "컬럼 추가 -> 기존 행 채우기 -> not null 걸기" 세 단계를 여기서 직접 해준다.
--
-- [앞선 마이그레이션들과 다른 점]
-- created_at/updated_at 때는 기존 행에 넣을 진짜 값이 없어서 now() 로 때웠지만,
-- 여기는 다르다. public_id 는 user 테이블에 이미 정확한 값이 있으므로 그대로 가져다 채우면 된다.
-- 즉 이 마이그레이션은 근사값이 아니라 진짜 값을 넣는다.
--
-- [실행]
--   docker exec -i moyeota-postgres psql -U moyeota -d moyeota -f - < docs/schema-refresh-token-public-id.sql
--
-- 이미 실행했더라도 다시 돌려도 괜찮다(if not exists / is null 조건이라 두 번째부터는 아무 일도 하지 않는다).
--
-- user 는 PostgreSQL 예약어라 반드시 따옴표로 감싸야 한다.

BEGIN;

ALTER TABLE refresh_token ADD COLUMN IF NOT EXISTS public_id uuid;

-- 주인이 되는 user 행에서 그대로 복사해 온다.
-- refresh_token.user_id 는 user.user_id 를 그대로 쓰는 공유 기본키라 이 조인은 항상 한 행만 짚는다.
UPDATE refresh_token rt
   SET public_id = u.public_id
  FROM "user" u
 WHERE u.user_id = rt.user_id
   AND rt.public_id IS NULL;

-- 여기까지 오면 빈 값이 없으므로 not null 을 걸 수 있다.
ALTER TABLE refresh_token ALTER COLUMN public_id SET NOT NULL;

-- user_id 가 PK라 사용자당 행이 하나뿐이므로 public_id 도 유일하다.
-- 엔티티의 @UniqueConstraint 와 이름을 맞춰둔다.
CREATE UNIQUE INDEX IF NOT EXISTS uk_refresh_token_public_id ON refresh_token (public_id);

COMMIT;
