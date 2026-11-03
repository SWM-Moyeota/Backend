ALTER TABLE match_room
    ADD COLUMN room_type varchar(20) NOT NULL DEFAULT 'REALTIME',
    ADD COLUMN departure_at timestamp(6) with time zone,
    ADD COLUMN reminder_sent_at timestamp(6) with time zone,
    ALTER COLUMN route DROP NOT NULL,
    ALTER COLUMN estimated_fare DROP NOT NULL,
    ALTER COLUMN estimated_time DROP NOT NULL;

-- 실시간 방의 departure_at 은 방장이 약속 시간을 확정하기 전까지 비어 있고 확정하면 채워진다
ALTER TABLE match_room
    ADD CONSTRAINT chk_match_room_type_fields CHECK (
        (room_type = 'REALTIME'
            AND route IS NOT NULL AND estimated_fare IS NOT NULL AND estimated_time IS NOT NULL)
        OR (room_type = 'RESERVED' AND departure_at IS NOT NULL
            AND route IS NULL AND estimated_fare IS NULL AND estimated_time IS NULL)
    );

CREATE INDEX idx_match_room_reserved_departure
    ON match_room (status, departure_at)
    WHERE room_type = 'RESERVED';
