CREATE TABLE user_rating (
    user_id bigint not null,
    rating_type varchar(255) not null check (rating_type in ('PASSENGER', 'DRIVER')),
    rating_sum integer not null,
    total_ratings integer not null,
    avg_rating numeric(3, 2) not null,
    updated_at timestamp(6) not null,
    primary key (user_id, rating_type)
);

CREATE TABLE rating_log (
    match_id bigint not null,
    rater_id bigint not null,
    ratee_id bigint not null,
    rating_type varchar(255) not null check (rating_type in ('PASSENGER', 'DRIVER')),
    rating smallint not null check (rating between 1 and 5),
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    primary key (match_id, rater_id, ratee_id)
);

CREATE INDEX idx_rating_log_ratee_id on rating_log (ratee_id, rating_type);
