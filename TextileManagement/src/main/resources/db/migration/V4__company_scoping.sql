ALTER TABLE customers ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE purchases ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE sales ADD COLUMN IF NOT EXISTS company_id BIGINT;

UPDATE customers
SET company_id = COALESCE(company_id, (SELECT id FROM company_profiles ORDER BY id LIMIT 1))
WHERE company_id IS NULL;

UPDATE suppliers
SET company_id = COALESCE(company_id, (SELECT id FROM company_profiles ORDER BY id LIMIT 1))
WHERE company_id IS NULL;

UPDATE purchases
SET company_id = COALESCE(company_id, (SELECT id FROM company_profiles ORDER BY id LIMIT 1))
WHERE company_id IS NULL;

UPDATE sales
SET company_id = COALESCE(company_id, (SELECT id FROM company_profiles ORDER BY id LIMIT 1))
WHERE company_id IS NULL;

ALTER TABLE customers ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE suppliers ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE purchases ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE sales ALTER COLUMN company_id SET NOT NULL;

ALTER TABLE customers
    ADD CONSTRAINT fk_customers_company FOREIGN KEY (company_id) REFERENCES company_profiles(id);

ALTER TABLE suppliers
    ADD CONSTRAINT fk_suppliers_company FOREIGN KEY (company_id) REFERENCES company_profiles(id);

ALTER TABLE purchases
    ADD CONSTRAINT fk_purchases_company FOREIGN KEY (company_id) REFERENCES company_profiles(id);

ALTER TABLE sales
    ADD CONSTRAINT fk_sales_company FOREIGN KEY (company_id) REFERENCES company_profiles(id);

DROP INDEX IF EXISTS uk_customers_gst_no;
CREATE UNIQUE INDEX IF NOT EXISTS uk_customers_company_gst_no ON customers(company_id, gst_no);

CREATE INDEX IF NOT EXISTS idx_customers_company_name ON customers(company_id, name);
CREATE INDEX IF NOT EXISTS idx_suppliers_company_name ON suppliers(company_id, name);
CREATE INDEX IF NOT EXISTS idx_purchases_company_due_status ON purchases(company_id, due_date, status);
CREATE INDEX IF NOT EXISTS idx_sales_company_financial_year ON sales(company_id, financial_year);
CREATE INDEX IF NOT EXISTS idx_sales_company_due_status ON sales(company_id, due_date, status);

DROP INDEX IF EXISTS idx_sales_financial_year;
DROP INDEX IF EXISTS idx_sales_due_status;
DROP INDEX IF EXISTS idx_purchases_due_status;

CREATE UNIQUE INDEX IF NOT EXISTS uk_sales_company_financial_challan
    ON sales(company_id, financial_year, challan_no);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sales_company_financial_bill
    ON sales(company_id, financial_year, bill_no);