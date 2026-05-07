ALTER TABLE gms.greenhouse
    ADD COLUMN IF NOT EXISTS description TEXT;

ALTER TABLE gms.greenhouse
    ADD COLUMN IF NOT EXISTS photo_url VARCHAR(500);
