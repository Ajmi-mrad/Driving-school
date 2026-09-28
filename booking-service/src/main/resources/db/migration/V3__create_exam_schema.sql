-- Examens officiels (code / conduite) : planifiés par le staff, résolus par un résultat.

CREATE TABLE exams (
    id             UUID PRIMARY KEY,
    type           VARCHAR(20)  NOT NULL,
    client_id      VARCHAR(255) NOT NULL,
    monitor_id     VARCHAR(255),
    vehicle_id     UUID,
    scheduled_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    location       VARCHAR(255),
    status         VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    attempt_number INTEGER      NOT NULL DEFAULT 1,
    result_note    VARCHAR(1000),
    created_by     VARCHAR(255),
    created_at     TIMESTAMP WITH TIME ZONE,
    updated_by     VARCHAR(255),
    updated_at     TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_exams_client       ON exams (client_id);
CREATE INDEX idx_exams_monitor      ON exams (monitor_id);
CREATE INDEX idx_exams_scheduled_at ON exams (scheduled_at);
CREATE INDEX idx_exams_status       ON exams (status);
