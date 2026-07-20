ALTER TABLE company_profiles
    ADD COLUMN IF NOT EXISTS logo_storage_key VARCHAR(300);
