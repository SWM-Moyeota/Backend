CREATE INDEX IF NOT EXISTS idx_user_match_room_user_id ON user_match_room (user_id);
CREATE INDEX IF NOT EXISTS idx_match_room_status_location ON match_room (status, departure_lat, departure_lng);
CREATE INDEX IF NOT EXISTS idx_chat_room_user_user_id ON chat_room_user (user_id);