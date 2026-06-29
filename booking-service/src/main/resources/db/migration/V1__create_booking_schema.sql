-- Planification : séances (conduite/code) + configuration de planification.

CREATE TABLE sessions (
    id          UUID PRIMARY KEY,
    type        VARCHAR(20)  NOT NULL,
    client_id   VARCHAR(255) NOT NULL,
    monitor_id  VARCHAR(255),
    vehicle_id  UUID,
    start_time  TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time    TIMESTAMP WITH TIME ZONE NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    notes       VARCHAR(1000),
    created_by  VARCHAR(255),
    created_at  TIMESTAMP WITH TIME ZONE,
    updated_by  VARCHAR(255),
    updated_at  TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_sessions_client     ON sessions (client_id);
CREATE INDEX idx_sessions_monitor    ON sessions (monitor_id);
CREATE INDEX idx_sessions_vehicle    ON sessions (vehicle_id);
CREATE INDEX idx_sessions_start_time ON sessions (start_time);
CREATE INDEX idx_sessions_status     ON sessions (status);

CREATE TABLE booking_settings (
    id                        UUID PRIMARY KEY,
    auto_validation_enabled   BOOLEAN NOT NULL DEFAULT FALSE,
    cancellation_notice_hours INTEGER NOT NULL DEFAULT 24,
    created_by                VARCHAR(255),
    created_at                TIMESTAMP WITH TIME ZONE,
    updated_by                VARCHAR(255),
    updated_at                TIMESTAMP WITH TIME ZONE
);

-- Ligne unique de configuration (id fixe = BookingSettings.SINGLETON_ID).
INSERT INTO booking_settings (id, auto_validation_enabled, cancellation_notice_hours)
VALUES ('00000000-0000-0000-0000-000000000001', FALSE, 24);