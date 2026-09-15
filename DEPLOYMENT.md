# Production deployment

The production request path is:

```text
Browser -> Cloudflare Pages + Functions -> external HTTPS load balancer
        -> Cloud Run -> Neon PostgreSQL
```

Pages Functions proxy `/api/*` to the server-only `BACKEND_URL`. Do not point
the browser or Pages directly at a public `run.app` URL. The Cloud Run service
template and Cloud Build configuration are in [`deploy/cloud-run/`](deploy/cloud-run/).

## Backend and database

1. Create the Artifact Registry repository, runtime service account, load
   balancer/serverless NEG, and Cloud Armor policy.
2. Store the pooled Neon JDBC URL, database username/password, storage keys,
   and mail password in Secret Manager. Use a direct Neon URL only for
   migrations and administration.
3. Deploy with `deploy/cloud-run/cloudbuild.yaml`. Render
   `APP_PAGES_DOMAIN`, `PROJECT_ID`, and the image through the build
   substitutions; do not commit rendered YAML or secret values.
4. Keep `SPRING_PROFILES_ACTIVE=prod`, `APP_TRUSTED_PROXY=cloudflare`, secure
   cross-site cookies, and `APP_SEED_ENABLED=false`.
5. Verify `/health/live` through the load balancer without requiring the
   database, then verify `/health/ready` and `/health` with the database
   available.

The checked-in Cloud Run template uses one minimum instance, a maximum of ten
instances, startup CPU boost, concurrency 40, and a 60-second request timeout.
Before increasing the maximum instance count, verify the Neon capacity and
keep this bound within the available connection budget:

```text
Cloud Run max instances * Hikari maximumPoolSize
    <= Neon max_connections - reserved/admin headroom
```

See [`LATENCY_BENCHMARK.md`](LATENCY_BENCHMARK.md) for the region, pooler,
Smart Placement, and connection-budget evidence to record.

## Cloudflare Pages

- Build from `project/` with the committed lockfile (`npm ci`, `npm run build`).
- Configure `BACKEND_URL` as a server-side Pages environment binding.
- Keep API calls same-origin; do not expose `BACKEND_URL` through a `VITE_*`
  variable.
- Configure the API origin to remove visitor-controlled forwarding headers;
  Cloudflare must provide the canonical client IP.
- Deploy the same Pages revision for both arms of any Smart Placement test.

## Production latency evidence

Use an isolated benchmark company and customer. Supply credentials at runtime,
never from source control or `.env` files:

```powershell
$env:BENCHMARK_USERNAME = 'isolated-test-user@example.invalid'
$env:BENCHMARK_PASSWORD = '<secret supplied at runtime>'
$env:BENCHMARK_COMPANY_ID = '<isolated company id>'
$env:BENCHMARK_CUSTOMER_ID = '<isolated customer id>'
$env:BENCHMARK_ALLOW_MUTATIONS = '1'
$env:BENCHMARK_REQUIRE_CF_TIMING = '1'
$env:BENCHMARK_REQUIRE_PRODUCTION_METADATA = '1'
$env:BENCHMARK_PAGES_REVISION = '<Pages deployment id or commit>'
$env:BENCHMARK_BACKEND_REVISION = '<Cloud Run revision>'
$env:BENCHMARK_DATABASE_BRANCH = '<Neon branch>'
$env:BENCHMARK_REGION = '<Cloud Run/Neon region>'
$env:BENCHMARK_CLIENT_REGION = '<operator location>'
node scripts/latency-benchmark.mjs --url https://<pages-domain> --mode warm --iterations 20 --output artifacts/production-warm.md
node scripts/latency-benchmark.mjs --url https://<pages-domain> --mode cold --iterations 20 --output artifacts/production-cold.md
```

Record the Pages revision, Cloud Run revision, Neon branch, regions, pooler
mode, and client location with the reports. Reset the test Cloud Run revision
before cold mode; the script cannot manufacture a platform cold start. Compare
the same data and client location before and after a release, and run the
Smart Placement A/B procedure in [`LATENCY_BENCHMARK.md`](LATENCY_BENCHMARK.md).
