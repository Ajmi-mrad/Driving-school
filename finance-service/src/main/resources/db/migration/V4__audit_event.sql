-- Journal d'audit append-only : chaque création / modification / suppression y est tracée
-- (qui, quand, quoi). Les lignes ne sont jamais modifiées ni supprimées.
CREATE TABLE audit_event (
    id          UUID PRIMARY KEY,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    actor       VARCHAR(255) NOT NULL,
    action      VARCHAR(40)  NOT NULL,
    entity_type VARCHAR(40)  NOT NULL,
    entity_id   VARCHAR(255),
    summary     VARCHAR(500)
);

CREATE INDEX idx_audit_event_occurred ON audit_event (occurred_at DESC);
