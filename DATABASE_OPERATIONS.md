# Database schema and recovery operations

## V25 data contract

- Taka meters and saved-taka meters are `NUMERIC(12,2)`; sale totals and purchase quantities are `NUMERIC(14,2)`.
- Values must be positive, at most two decimal places, a taka number is `1..999999999`, and one taka is at most `10,000,000.00` metres/units. A sale total is capped at 200 taka entries.
- V25 is additive and aborts before changing types if legacy data has more than two meaningful decimal places, duplicate case-insensitive usernames/emails/trade names, duplicate/missing taka ordering, or cross-company customer/supplier references. Repair that data first; do not edit Flyway history or force the migration.
- The old non-unique `(sale_id, entry_order)` index is replaced by an exact-key unique index after the migration verifies it is safe. Other legacy indexes remain: they have not been removed without a production `pg_stat_user_indexes` review.

## Safe rollout and rollback

1. Take a restorable snapshot/PITR point and run the migration on a disposable Neon branch first.
2. Run `./mvnw test`; the PostgreSQL Testcontainers test migrates a clean database from V1 through V25 when Docker is available.
3. Deploy the application version that sends entity `version` values before enabling writes against V25. Stale/missing update versions return 409.
4. Do not run Flyway `clean`, repair history, or down-migrate production. If V25 aborts, fix the reported data; if a deployed application must be reverted, restore to the pre-rollout PITR point or direct traffic to a branch restored at that timestamp.

## Neon branch and connection budget

Use a temporary branch/check-out with the Neon CLI's no-env-pull option; provide its URL only through the command environment or CI secret store. Never copy a production URL into source or `.env`. Test, capture the migration and query-plan output, then delete the branch.

Budget pooled connections across all app replicas: `replicas × Hikari maximumPoolSize` must remain below the Neon pooled endpoint limit with headroom for admin and migration connections. Keep Flyway as a single deployment task, not one migration runner per replica.

## Pending-payment plan check

V25 changes the predicate to `status = 'PENDING'` (equivalent because the column is non-null) and adds PostgreSQL partial indexes `idx_sales_pending_company_due` and `idx_purchases_pending_company_due`. On the disposable branch, seed representative tenant data and run:

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, due_date FROM sales
WHERE company_id = :company_id AND status = 'PENDING'
ORDER BY due_date, id LIMIT 25;
```

The plan must show the corresponding pending index before any legacy index is considered for removal. Repeat for purchases and keep the captured plan with the release record.

## PITR drill

Quarterly: create a branch restored to a timestamp five minutes before a known test write, verify the write is absent and a pre-existing record is present, run read-only application smoke checks, record recovery time, then delete the branch. RLS is intentionally not added: application tenant checks plus composite foreign keys are the current model; introduce RLS only with a fully tested database-role and connection-pooling design.
