ALTER TABLE company_profiles
    ADD COLUMN IF NOT EXISTS logo_data BYTEA;

ALTER TABLE company_profiles
    ADD COLUMN IF NOT EXISTS logo_content_type VARCHAR(50);

ALTER TABLE company_profiles
    ADD COLUMN IF NOT EXISTS logo_width INT;

ALTER TABLE company_profiles
    ADD COLUMN IF NOT EXISTS logo_height INT;

ALTER TABLE company_profiles
    ADD CONSTRAINT chk_company_profiles_logo_content_type
    CHECK (logo_content_type IS NULL OR logo_content_type IN ('image/png', 'image/jpeg'));

ALTER TABLE company_profiles
    ADD CONSTRAINT chk_company_profiles_logo_dimensions
    CHECK (
        logo_data IS NULL
        OR (logo_width IS NOT NULL AND logo_height IS NOT NULL AND logo_width > 0 AND logo_height > 0)
    );
