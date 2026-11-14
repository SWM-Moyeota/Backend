-- 방 출발지·도착지 이름 컬럼을 chat_room(V2)·ride_record 와 같은 이름으로 맞춘다
ALTER TABLE match_room RENAME COLUMN departure TO departure_place;
ALTER TABLE match_room RENAME COLUMN destination TO destination_place;

-- 멤버 행은 방에 합류할 때 생기므로 합류 시각을 created_at 으로 두고, 출석(도착·노쇼) 변경 시각을 updated_at 에 남긴다
ALTER TABLE user_match_room RENAME COLUMN joined_at TO created_at;
ALTER TABLE user_match_room ADD COLUMN updated_at timestamp(6) with time zone;
