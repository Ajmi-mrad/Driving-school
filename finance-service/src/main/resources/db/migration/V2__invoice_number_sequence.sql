-- Numérotation des documents financiers via une séquence dédiée : unique et sans course
-- concurrente (contrairement à un COUNT(*) + 1). Démarre au-delà des lignes déjà émises.
CREATE SEQUENCE invoice_number_seq
    START WITH 1
    INCREMENT BY 1;

-- Positionne la séquence : le prochain nextval() vaut (nombre de factures existantes + 1).
-- is_called = false => nextval() renvoie exactement la valeur passée.
SELECT setval('invoice_number_seq', (SELECT COUNT(*) FROM invoices) + 1, false);
