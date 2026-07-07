-- Notifications in-app : message reçu hors-ligne (NEW_MESSAGE) et rappel de paiement (PAYMENT_DUE).

CREATE TABLE notifications (
    id            UUID PRIMARY KEY,
    recipient_id  VARCHAR(255) NOT NULL,
    type          VARCHAR(30)  NOT NULL,
    title         VARCHAR(255) NOT NULL,
    body          VARCHAR(1000),
    reference_id  VARCHAR(255),
    amount        NUMERIC(12, 2),
    read_at       TIMESTAMP WITH TIME ZONE,
    created_by    VARCHAR(255),
    created_at    TIMESTAMP WITH TIME ZONE,
    updated_by    VARCHAR(255),
    updated_at    TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_notifications_recipient        ON notifications (recipient_id);
CREATE INDEX idx_notifications_recipient_unread ON notifications (recipient_id, read_at);
CREATE INDEX idx_notifications_created_at       ON notifications (created_at);
