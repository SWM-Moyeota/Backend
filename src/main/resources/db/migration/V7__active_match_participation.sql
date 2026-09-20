-- 진행 중인 방의 사용자당 참여 1건. 과거 이력은 user_match_room에 보존한다.
CREATE TABLE active_match_participation (
    user_id bigint NOT NULL,
    party_id bigint NOT NULL REFERENCES match_room(id),
    CONSTRAINT pk_active_match_participation PRIMARY KEY (user_id)
);
CREATE INDEX idx_active_match_party ON active_match_participation(party_id);

-- 기존 중복이 있다면 임의로 한쪽을 선택하지 않고 마이그레이션을 실패시킨다.
-- 배포 전 중복 확인 쿼리는 docs/matching-concurrency.md 참고.
INSERT INTO active_match_participation(user_id, party_id)
SELECT m.user_id, m.match_id
FROM user_match_room m JOIN match_room p ON p.id = m.match_id
WHERE p.status IN ('ACTIVE', 'COMPLETED', 'MATCHING', 'DRIVER_ASSIGNED', 'IN_RIDE');
