#!/usr/bin/env bash
# 회차 사이에 파티·이벤트·채팅 데이터를 비우고 Redis 를 초기화한다. 계정(users/local_user/taxi_driver)과 refresh 토큰은 남긴다.
# 이전 회차의 파티가 남아 있으면 "이미 참여 중인 방이 있습니다"(409) 로 다음 회차 에러율이 오염된다.
#   loadtest/scripts/reset-db.sh                      # 로컬 docker (moyeota-postgres / moyeota-redis)
#   PG="psql postgresql://user:pw@host/moyeota" REDIS="redis-cli -h host" loadtest/scripts/reset-db.sh
set -euo pipefail

PG=${PG:-"docker exec -i moyeota-postgres psql -U moyeota -d moyeota"}
REDIS=${REDIS:-"docker exec -i moyeota-redis redis-cli"}

echo "[reset] 파티·아웃박스·채팅 테이블 비우기"
$PG -v ON_ERROR_STOP=1 <<'SQL'
TRUNCATE TABLE
    user_match_room,
    match_room,
    event_publication,
    chat_message,
    chat_room_user,
    chat_room,
    driver_report
RESTART IDENTITY CASCADE;
SQL

echo "[reset] Redis FLUSHALL (기사 위치·콜 후보·경로 캐시)"
$REDIS FLUSHALL

echo "[reset] 완료"
