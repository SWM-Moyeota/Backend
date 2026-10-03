-- 재시도 상한을 넘어서 재발행을 포기한 outbox 이벤트
-- event_publication에 남기면 재발행 배치의 앞에 두어 새로운 이벤트가 재실행 되지 않음
CREATE TABLE event_dead_letter (
    id                     uuid PRIMARY KEY,
    listener_id            varchar(255) NOT NULL,
    event_type             varchar(255) NOT NULL,
    serialized_event       text NOT NULL,
    publication_date       timestamp(6) with time zone NOT NULL,
    completion_attempts    integer NOT NULL,
    last_resubmission_date timestamp(6) with time zone,
    dead_at                timestamp(6) with time zone NOT NULL
);