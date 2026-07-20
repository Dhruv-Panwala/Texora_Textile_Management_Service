ALTER TABLE sales ADD COLUMN IF NOT EXISTS challan_count INT;

UPDATE sales
SET challan_count = 1
WHERE challan_count IS NULL OR challan_count < 1;

ALTER TABLE sales ALTER COLUMN challan_count SET NOT NULL;

DROP INDEX IF EXISTS uk_sales_company_financial_challan;
