CREATE TABLE ride_record (
    match_id bigint not null,
    taxi_driver_id bigint,
    departure_place varchar(255) not null,
    destination_place varchar(255) not null,
    pickup_time timestamp(6) with time zone,
    dropoff_time timestamp(6) with time zone,
    total_fare integer,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone,
    primary key (match_id)
);

ALTER TABLE ride_record ADD CONSTRAINT fk_ride_record_match_room FOREIGN KEY (match_id) REFERENCES match_room (id);
