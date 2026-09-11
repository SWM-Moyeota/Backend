CREATE TABLE event_publication (
   id                     uuid PRIMARY KEY,
   publication_date       timestamp(6) with time zone NOT NULL,
   listener_id            varchar(255) NOT NULL,
   serialized_event       varchar(255) NOT NULL,
   event_type             varchar(255) NOT NULL,
   completion_date        timestamp(6) with time zone,
   last_resubmission_date timestamp(6) with time zone,
   completion_attempts    integer NOT NULL,
   status                 varchar(255)
);
CREATE INDEX idx_event_publication_incomplete ON event_publication (publication_date) WHERE completion_date IS NULL;