CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE match_room
    ADD COLUMN departure_geog geography(Point, 4326)
        GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(departure_lng, departure_lat), 4326)::geography) STORED,
    ADD COLUMN destination_geog geography(Point, 4326)
        GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(destination_lng, destination_lat), 4326)::geography) STORED;

-- B-tree 출발지 위도 경도 status 복합키 제거
DROP INDEX IF EXISTS idx_match_room_status_location;

CREATE INDEX idx_match_room_departure_geog
    ON match_room USING GIST (departure_geog) WHERE status = 'WAITING';
CREATE INDEX idx_match_room_destination_geog
    ON match_room USING GIST (destination_geog) WHERE status = 'WAITING';