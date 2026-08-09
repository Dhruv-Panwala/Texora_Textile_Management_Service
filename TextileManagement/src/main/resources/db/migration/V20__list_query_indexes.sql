CREATE INDEX IF NOT EXISTS idx_purchases_company_purchase_date
    ON purchases (company_id, purchase_date);

CREATE INDEX IF NOT EXISTS idx_sales_company_sale_date
    ON sales (company_id, sale_date);
