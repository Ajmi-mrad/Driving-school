-- Gestion financière : forfaits (catalogue), inscriptions, paiements et documents (factures/reçus).

CREATE TABLE forfaits (
    id            UUID PRIMARY KEY,
    name          VARCHAR(150)  NOT NULL,
    description   VARCHAR(1000),
    driving_hours INTEGER       NOT NULL DEFAULT 0,
    code_sessions INTEGER       NOT NULL DEFAULT 0,
    price         NUMERIC(10,2) NOT NULL,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by    VARCHAR(255),
    created_at    TIMESTAMP WITH TIME ZONE,
    updated_by    VARCHAR(255),
    updated_at    TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_forfaits_active ON forfaits (active);

CREATE TABLE enrollments (
    id                      UUID PRIMARY KEY,
    client_id               VARCHAR(255)  NOT NULL,
    forfait_id              UUID          NOT NULL REFERENCES forfaits (id),
    status                  VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    total_price             NUMERIC(10,2) NOT NULL,
    amount_paid             NUMERIC(10,2) NOT NULL DEFAULT 0,
    remaining_driving_hours INTEGER       NOT NULL DEFAULT 0,
    remaining_code_sessions INTEGER       NOT NULL DEFAULT 0,
    enrolled_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by              VARCHAR(255),
    created_at              TIMESTAMP WITH TIME ZONE,
    updated_by              VARCHAR(255),
    updated_at              TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_enrollments_client  ON enrollments (client_id);
CREATE INDEX idx_enrollments_forfait ON enrollments (forfait_id);
CREATE INDEX idx_enrollments_status  ON enrollments (status);

CREATE TABLE payments (
    id            UUID PRIMARY KEY,
    enrollment_id UUID          NOT NULL REFERENCES enrollments (id),
    client_id     VARCHAR(255)  NOT NULL,
    amount        NUMERIC(10,2) NOT NULL,
    method        VARCHAR(20)   NOT NULL,
    reference     VARCHAR(255),
    paid_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by    VARCHAR(255),
    created_at    TIMESTAMP WITH TIME ZONE,
    updated_by    VARCHAR(255),
    updated_at    TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_payments_enrollment ON payments (enrollment_id);
CREATE INDEX idx_payments_client     ON payments (client_id);

CREATE TABLE invoices (
    id            UUID PRIMARY KEY,
    enrollment_id UUID          NOT NULL REFERENCES enrollments (id),
    client_id     VARCHAR(255)  NOT NULL,
    number        VARCHAR(50)   NOT NULL UNIQUE,
    type          VARCHAR(20)   NOT NULL,
    amount        NUMERIC(10,2) NOT NULL,
    issued_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by    VARCHAR(255),
    created_at    TIMESTAMP WITH TIME ZONE,
    updated_by    VARCHAR(255),
    updated_at    TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_invoices_enrollment ON invoices (enrollment_id);
CREATE INDEX idx_invoices_client     ON invoices (client_id);
