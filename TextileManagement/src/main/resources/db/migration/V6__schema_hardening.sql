CREATE UNIQUE INDEX IF NOT EXISTS uk_company_profiles_trade_name
    ON company_profiles(trade_name);

CREATE INDEX IF NOT EXISTS idx_taka_entries_sale_id
    ON taka_entries(sale_id);

CREATE INDEX IF NOT EXISTS idx_purchases_company_purchase_date
    ON purchases(company_id, purchase_date DESC);

CREATE INDEX IF NOT EXISTS idx_sales_company_sale_date
    ON sales(company_id, sale_date DESC);

ALTER TABLE company_profiles
    ADD CONSTRAINT chk_company_profiles_trade_name_not_blank
    CHECK (LENGTH(TRIM(trade_name)) > 0);

ALTER TABLE customers
    ADD CONSTRAINT chk_customers_name_not_blank
    CHECK (LENGTH(TRIM(name)) > 0);

ALTER TABLE suppliers
    ADD CONSTRAINT chk_suppliers_name_not_blank
    CHECK (LENGTH(TRIM(name)) > 0);

ALTER TABLE purchases
    ADD CONSTRAINT chk_purchases_material_type
    CHECK (material_type IN ('BEAM', 'YARN', 'MISCELLANEOUS'));

ALTER TABLE purchases
    ADD CONSTRAINT chk_purchases_quantity_positive
    CHECK (quantity > 0);

ALTER TABLE purchases
    ADD CONSTRAINT chk_purchases_rate_positive
    CHECK (rate > 0);

ALTER TABLE purchases
    ADD CONSTRAINT chk_purchases_amount_non_negative
    CHECK (amount >= 0);

ALTER TABLE purchases
    ADD CONSTRAINT chk_purchases_status
    CHECK (status IN ('PENDING', 'PAID'));

ALTER TABLE purchases
    ADD CONSTRAINT chk_purchases_payment_mode
    CHECK (payment_mode IS NULL OR payment_mode IN ('CASH', 'CHEQUE', 'UPI'));

ALTER TABLE purchases
    ADD CONSTRAINT chk_purchases_cheque_requirement
    CHECK (
        payment_mode IS NULL
        OR payment_mode <> 'CHEQUE'
        OR (cheque_no IS NOT NULL AND LENGTH(TRIM(cheque_no)) > 0)
    );

ALTER TABLE sales
    ADD CONSTRAINT chk_sales_quality_not_blank
    CHECK (LENGTH(TRIM(quality)) > 0);

ALTER TABLE sales
    ADD CONSTRAINT chk_sales_challan_no_positive
    CHECK (challan_no > 0);

ALTER TABLE sales
    ADD CONSTRAINT chk_sales_challan_count_positive
    CHECK (challan_count > 0);

ALTER TABLE sales
    ADD CONSTRAINT chk_sales_bill_no_positive
    CHECK (bill_no IS NULL OR bill_no > 0);

ALTER TABLE sales
    ADD CONSTRAINT chk_sales_rate_positive
    CHECK (rate > 0);

ALTER TABLE sales
    ADD CONSTRAINT chk_sales_amount_non_negative
    CHECK (amount >= 0);

ALTER TABLE sales
    ADD CONSTRAINT chk_sales_total_meters_positive
    CHECK (total_meters > 0);

ALTER TABLE sales
    ADD CONSTRAINT chk_sales_status
    CHECK (status IN ('PENDING', 'PAID'));

ALTER TABLE sales
    ADD CONSTRAINT chk_sales_payment_mode
    CHECK (payment_mode IS NULL OR payment_mode IN ('CASH', 'CHEQUE', 'UPI'));

ALTER TABLE sales
    ADD CONSTRAINT chk_sales_cheque_requirement
    CHECK (
        payment_mode IS NULL
        OR payment_mode <> 'CHEQUE'
        OR (cheque_no IS NOT NULL AND LENGTH(TRIM(cheque_no)) > 0)
    );

ALTER TABLE taka_entries
    ADD CONSTRAINT chk_taka_entries_taka_no_positive
    CHECK (taka_no > 0);

ALTER TABLE taka_entries
    ADD CONSTRAINT chk_taka_entries_meters_positive
    CHECK (meters > 0);
