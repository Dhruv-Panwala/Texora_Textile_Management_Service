ALTER TABLE purchases ADD COLUMN description VARCHAR(500);

UPDATE customers SET gst_no = NULL WHERE gst_no = '';

CREATE UNIQUE INDEX uk_customers_gst_no ON customers(gst_no);
