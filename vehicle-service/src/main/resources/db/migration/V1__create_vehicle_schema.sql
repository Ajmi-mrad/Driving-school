-- Parc automobile : véhicules + opérations d'entretien.
CREATE TABLE vehicles (
    id                          UUID PRIMARY KEY,
    brand                       VARCHAR(100) NOT NULL,
    model                       VARCHAR(100) NOT NULL,
    registration_number        VARCHAR(50)  NOT NULL UNIQUE,
    gearbox_type               VARCHAR(20)  NOT NULL,
    fuel_type                  VARCHAR(20)  NOT NULL,
    status                     VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    manufacture_year           INTEGER,
    mileage                    INTEGER,
    insurance_expiry           DATE,
    technical_inspection_expiry DATE,
    created_by                 VARCHAR(255),
    created_at                 TIMESTAMP WITH TIME ZONE,
    updated_by                 VARCHAR(255),
    updated_at                 TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_vehicles_status ON vehicles (status);

CREATE TABLE maintenance_records (
    id           UUID PRIMARY KEY,
    vehicle_id   UUID         NOT NULL,
    type         VARCHAR(30)  NOT NULL,
    performed_at DATE         NOT NULL,
    cost         NUMERIC(12, 2),
    description  VARCHAR(1000),
    created_by   VARCHAR(255),
    created_at   TIMESTAMP WITH TIME ZONE,
    updated_by   VARCHAR(255),
    updated_at   TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_maintenance_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id)
);

CREATE INDEX idx_maintenance_vehicle ON maintenance_records (vehicle_id);