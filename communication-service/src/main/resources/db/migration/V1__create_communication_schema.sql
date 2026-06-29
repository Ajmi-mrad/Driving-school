-- Communication : conversations 1:1 (moniteur ↔ élève) et messages.

CREATE TABLE conversations (
    id                   UUID PRIMARY KEY,
    monitor_id           VARCHAR(255) NOT NULL,
    client_id            VARCHAR(255) NOT NULL,
    last_message_at      TIMESTAMP WITH TIME ZONE,
    last_message_preview VARCHAR(500),
    created_by           VARCHAR(255),
    created_at           TIMESTAMP WITH TIME ZONE,
    updated_by           VARCHAR(255),
    updated_at           TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_conversation_pair UNIQUE (monitor_id, client_id)
);

CREATE INDEX idx_conversations_monitor      ON conversations (monitor_id);
CREATE INDEX idx_conversations_client       ON conversations (client_id);
CREATE INDEX idx_conversations_last_message ON conversations (last_message_at);

CREATE TABLE messages (
    id              UUID PRIMARY KEY,
    conversation_id UUID         NOT NULL REFERENCES conversations (id),
    sender_id       VARCHAR(255) NOT NULL,
    type            VARCHAR(20)  NOT NULL DEFAULT 'TEXT',
    content         VARCHAR(4000) NOT NULL,
    sent_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    read_at         TIMESTAMP WITH TIME ZONE,
    created_by      VARCHAR(255),
    created_at      TIMESTAMP WITH TIME ZONE,
    updated_by      VARCHAR(255),
    updated_at      TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_messages_conversation ON messages (conversation_id);
CREATE INDEX idx_messages_sent_at      ON messages (sent_at);
CREATE INDEX idx_messages_read_at      ON messages (read_at);