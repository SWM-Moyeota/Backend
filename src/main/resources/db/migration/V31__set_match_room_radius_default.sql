-- 방 반경(departure_radius, destination_radius)은 실시간·예약 방 모두 늘 500(m)이고 읽는 쿼리가 없어 코드가 더는 매핑하지 않는다.
-- 매핑이 빠지면 INSERT 에서 두 컬럼이 빠지는데 NOT NULL 이라 기본값이 없으면 방 생성이 실패한다 - 지금까지 넣던 500 을 기본값으로 둔다.
-- Flyway 가 앱보다 먼저 돌아 이 기본값은 새 코드와 같은 배포에서 먼저 깔린다. 배포 중 함께 도는 구버전 서버는 500 을 직접 넣으므로 영향이 없다.
-- 기존 행의 값은 읽는 곳이 없어 그대로 둔다. 컬럼 DROP 은 매핑을 지운 코드가 모든 서버와 롤백 대상에 깔린 다음 릴리스에서 한다
ALTER TABLE match_room
    ALTER COLUMN departure_radius SET DEFAULT 500,
    ALTER COLUMN destination_radius SET DEFAULT 500;
