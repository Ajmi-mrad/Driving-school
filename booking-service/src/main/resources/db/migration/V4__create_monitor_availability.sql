-- Disponibilité des moniteurs : heures de travail hebdomadaires récurrentes + absences ponctuelles.
-- Socle du calcul des créneaux réservables (heures de travail − séances réservées − absences),
-- destiné à la réservation manuelle et au futur assistant de planification par IA.

CREATE TABLE monitor_availability (
    id          UUID PRIMARY KEY,
    monitor_id  VARCHAR(255) NOT NULL,
    day_of_week VARCHAR(10)  NOT NULL,   -- MONDAY .. SUNDAY (java.time.DayOfWeek)
    start_time  TIME         NOT NULL,   -- horloge locale (Africa/Tunis)
    end_time    TIME         NOT NULL,
    created_by  VARCHAR(255),
    created_at  TIMESTAMP WITH TIME ZONE,
    updated_by  VARCHAR(255),
    updated_at  TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_monitor_avail_monitor ON monitor_availability (monitor_id);

CREATE TABLE monitor_time_off (
    id         UUID PRIMARY KEY,
    monitor_id VARCHAR(255) NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time   TIMESTAMP WITH TIME ZONE NOT NULL,
    reason     VARCHAR(255),
    created_by VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE,
    updated_by VARCHAR(255),
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_monitor_timeoff_monitor ON monitor_time_off (monitor_id);
CREATE INDEX idx_monitor_timeoff_range   ON monitor_time_off (start_time, end_time);
