-- Suppression logique (soft delete) auditable pour les entités finance.
-- Les lignes ne sont jamais physiquement supprimées : on trace qui/quand.

ALTER TABLE forfaits    ADD COLUMN deleted    BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE forfaits    ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE forfaits    ADD COLUMN deleted_by VARCHAR(255);

ALTER TABLE enrollments ADD COLUMN deleted    BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE enrollments ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE enrollments ADD COLUMN deleted_by VARCHAR(255);

ALTER TABLE payments    ADD COLUMN deleted    BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE payments    ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE payments    ADD COLUMN deleted_by VARCHAR(255);

ALTER TABLE invoices    ADD COLUMN deleted    BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE invoices    ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE invoices    ADD COLUMN deleted_by VARCHAR(255);

-- Lien reçu -> paiement, pour annuler le bon reçu quand un paiement est annulé.
ALTER TABLE invoices    ADD COLUMN payment_id UUID;
CREATE INDEX idx_invoices_payment ON invoices (payment_id);

-- Index partiels : les listes ne portent que sur les lignes vivantes.
CREATE INDEX idx_forfaits_live    ON forfaits    (id) WHERE deleted = FALSE;
CREATE INDEX idx_enrollments_live ON enrollments (id) WHERE deleted = FALSE;
CREATE INDEX idx_payments_live    ON payments    (id) WHERE deleted = FALSE;
CREATE INDEX idx_invoices_live    ON invoices    (id) WHERE deleted = FALSE;