UPDATE sales
SET challan_count = GREATEST(
    1,
    CAST(CEILING((
        SELECT COUNT(*) / 36.0
        FROM taka_entries
        WHERE taka_entries.sale_id = sales.id
    )) AS INT)
);

UPDATE business_number_sequences sequence_row
SET next_challan_number = GREATEST(
    sequence_row.next_challan_number,
    COALESCE((
        SELECT MAX(sale.challan_no + sale.challan_count)
        FROM sales sale
        WHERE sale.company_id = sequence_row.company_id
          AND sale.financial_year = sequence_row.financial_year
    ), 1)
);
