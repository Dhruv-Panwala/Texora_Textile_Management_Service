ALTER TABLE taka_entries ADD COLUMN entry_order INTEGER;

UPDATE taka_entries AS target
SET entry_order = (
    SELECT CAST(COUNT(*) - 1 AS INTEGER)
    FROM taka_entries AS earlier
    WHERE earlier.sale_id = target.sale_id
      AND earlier.id <= target.id
);

CREATE INDEX ix_taka_entries_sale_order
    ON taka_entries (sale_id, entry_order);
