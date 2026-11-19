-- Platform infrastructure: distributed scheduler locks (ShedLock) and the Spring Modulith
-- event publication registry (transactional outbox for inter-module events).

CREATE TABLE shedlock (
    name        varchar(64)  PRIMARY KEY,
    lock_until  timestamptz  NOT NULL,
    locked_at   timestamptz  NOT NULL,
    locked_by   varchar(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS event_publication (
    id                      uuid PRIMARY KEY,
    listener_id             text        NOT NULL,
    event_type              text        NOT NULL,
    serialized_event        text        NOT NULL,
    publication_date        timestamptz NOT NULL,
    completion_date         timestamptz,
    status                  text,
    completion_attempts     integer,
    last_resubmission_date  timestamptz
);
CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx
    ON event_publication USING hash (serialized_event);
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx
    ON event_publication (completion_date);

CREATE TABLE IF NOT EXISTS event_publication_archive (
    id                      uuid PRIMARY KEY,
    listener_id             text        NOT NULL,
    event_type              text        NOT NULL,
    serialized_event        text        NOT NULL,
    publication_date        timestamptz NOT NULL,
    completion_date         timestamptz,
    status                  text,
    completion_attempts     integer,
    last_resubmission_date  timestamptz
);
CREATE INDEX IF NOT EXISTS event_publication_archive_serialized_event_hash_idx
    ON event_publication_archive USING hash (serialized_event);
CREATE INDEX IF NOT EXISTS event_publication_archive_by_completion_date_idx
    ON event_publication_archive (completion_date);
