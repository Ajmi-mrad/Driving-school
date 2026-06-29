-- Table principale des utilisateurs (entité unique, pas d'héritage).
CREATE TABLE users (
    id                    UUID PRIMARY KEY,
    keycloak_id           VARCHAR(255) NOT NULL UNIQUE,
    username              VARCHAR(255) NOT NULL UNIQUE,
    email                 VARCHAR(255) UNIQUE,
    first_name            VARCHAR(255),
    last_name             VARCHAR(255),
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    notifications_enabled BOOLEAN      NOT NULL DEFAULT TRUE,
    permit_number         VARCHAR(100),
    created_by            VARCHAR(255),
    created_at            TIMESTAMP WITH TIME ZONE,
    updated_by            VARCHAR(255),
    updated_at            TIMESTAMP WITH TIME ZONE
);

-- Rôles d'un utilisateur (un utilisateur peut en porter plusieurs).
CREATE TABLE user_roles (
    user_id UUID        NOT NULL,
    role    VARCHAR(20) NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_user_roles_role ON user_roles (role);

-- Numéros de téléphone d'un utilisateur (plusieurs possibles).
CREATE TABLE user_phones (
    user_id UUID        NOT NULL,
    phone   VARCHAR(50) NOT NULL,
    CONSTRAINT pk_user_phones PRIMARY KEY (user_id, phone),
    CONSTRAINT fk_user_phones_user FOREIGN KEY (user_id) REFERENCES users (id)
);