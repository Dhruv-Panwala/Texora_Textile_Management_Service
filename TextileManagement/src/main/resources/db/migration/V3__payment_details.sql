ALTER TABLE sales ADD COLUMN payment_date DATE;
ALTER TABLE sales ADD COLUMN payment_mode VARCHAR(20);
ALTER TABLE sales ADD COLUMN cheque_no VARCHAR(50);

ALTER TABLE purchases ADD COLUMN payment_date DATE;
ALTER TABLE purchases ADD COLUMN payment_mode VARCHAR(20);
ALTER TABLE purchases ADD COLUMN cheque_no VARCHAR(50);
