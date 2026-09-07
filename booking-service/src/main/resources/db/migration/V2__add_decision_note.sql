-- Note de décision saisie par le staff/moniteur lors de la confirmation ou du refus d'une
-- demande de séance (ex. justification d'un refus, créneaux disponibles proposés à l'élève).

ALTER TABLE sessions ADD COLUMN decision_note VARCHAR(1000);