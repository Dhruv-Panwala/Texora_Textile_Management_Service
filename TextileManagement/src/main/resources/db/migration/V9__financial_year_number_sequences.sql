CREATE TABLE IF NOT EXISTS business_number_sequences (
    company_id BIGINT NOT NULL,
    financial_year VARCHAR(20) NOT NULL,
    next_challan_number INT NOT NULL,
    next_bill_number INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (company_id, financial_year),
    CONSTRAINT fk_number_sequences_company FOREIGN KEY (company_id) REFERENCES company_profiles(id),
    CONSTRAINT chk_number_sequences_challan_positive CHECK (next_challan_number > 0),
    CONSTRAINT chk_number_sequences_bill_positive CHECK (next_bill_number > 0)
);

INSERT INTO business_number_sequences (
    company_id,
    financial_year,
    next_challan_number,
    next_bill_number
)
SELECT
    company_id,
    financial_year,
    COALESCE(MAX(challan_no + challan_count), 1),
    COALESCE(MAX(bill_no) + 1, 1)
FROM sales
GROUP BY company_id, financial_year;
