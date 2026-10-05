-- 부하테스트 사후 대조. 실행이 끝난 뒤(재제출이 가라앉도록 몇 분 뒤) 운영 EC2 의 psql 에서 붙여 넣는다.
-- "방에 참여한 사람은 그 방의 채팅방에도 들어가 있다" 가 지켜졌는지 DB 로 맞춰 본다.
-- 탐침(chat_join_lag_ms)이 "얼마나 늦었나"를 표본으로 본다면, 이 쿼리는 "몇 건이 끝내 안 됐나"를 전수로 센다.
-- 기간을 바꾸려면 interval 값을 고친다 (기본: 최근 30분에 만들어진 테스트 방).

-- 1) 요약
WITH run AS (
  SELECT id FROM match_room WHERE destination LIKE 'LT-%' AND created_at >= now() - interval '30 minutes'
), party AS (
  SELECT m.match_id AS party_id, count(*) AS members
    FROM user_match_room m JOIN run r ON r.id = m.match_id GROUP BY 1
), chat AS (
  SELECT c.party_id, count(u.user_id) AS chat_members       -- 나간 사람(left_at)도 센다: 입장한 적이 있는지만 본다
    FROM chat_room c JOIN run r ON r.id = c.party_id
    LEFT JOIN chat_room_user u ON u.chat_room_id = c.id GROUP BY 1
)
SELECT count(*)                                                              AS parties,           -- 멤버가 있는 테스트 방
       count(*) FILTER (WHERE chat.party_id IS NULL)                         AS no_chat_room,      -- 채팅방이 아예 안 만들어진 방
       count(*) FILTER (WHERE chat.chat_members < party.members)             AS rooms_short,       -- 채팅방은 있는데 멤버가 모자란 방
       sum(party.members)                                                    AS party_members,
       sum(GREATEST(party.members - coalesce(chat.chat_members, 0), 0))      AS missing_members,   -- 채팅방에 못 들어간 사람 수 (0 이어야 한다)
       round(100.0 * sum(GREATEST(party.members - coalesce(chat.chat_members, 0), 0)) / NULLIF(sum(party.members), 0), 2) AS missing_pct
  FROM party LEFT JOIN chat USING (party_id);

-- 2) 아직 처리되지 않은 이벤트 - 종류·상태별. PUBLISHED 가 남아 있으면 큐에서 거절돼 리스너가 시작도 못 한 것이다
SELECT regexp_replace(event_type, '.*\.', '') AS event, status, count(*),
       to_char(min(publication_date) AT TIME ZONE 'Asia/Seoul', 'HH24:MI:SS') AS oldest
  FROM event_publication WHERE completion_date IS NULL GROUP BY 1, 2 ORDER BY 3 DESC;

-- 3) 닫히지 못하고 남은 테스트 방 - 다음 실행 전에 reset.sql 로 지운다
SELECT status, count(*) FROM match_room
 WHERE destination LIKE 'LT-%' AND status IN ('ACTIVE', 'COMPLETED') GROUP BY 1;
