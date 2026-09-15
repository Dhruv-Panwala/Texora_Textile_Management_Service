# Latency measurement and deployment recommendations

Run the same benchmark against the Pages URL before and after a release. It
reports p50, p95, and p99 for login, the single-request authenticated bootstrap,
sales list, create-sale, and dashboard. It does not read `.env` files.

```powershell
$env:BENCHMARK_USERNAME = 'isolated-test-user@example.invalid'
$env:BENCHMARK_PASSWORD = '<secret supplied at runtime>'
$env:BENCHMARK_COMPANY_ID = '<isolated company id>'
$env:BENCHMARK_CUSTOMER_ID = '<isolated customer id>'
$env:BENCHMARK_ALLOW_MUTATIONS = '1'
# Use `parallel` only when benchmarking a pre-bootstrap revision; current code uses `endpoint`.
$env:BENCHMARK_BOOTSTRAP_MODE = 'endpoint'
# Required by the production-evidence gate; identifiers only, never secrets.
$env:BENCHMARK_RUN_LABEL = 'before'
$env:BENCHMARK_PAGES_REVISION = '<Pages deployment id or commit>'
$env:BENCHMARK_BACKEND_REVISION = '<Cloud Run revision>'
$env:BENCHMARK_DATABASE_BRANCH = '<Neon branch>'
$env:BENCHMARK_REGION = 'europe-west1'
$env:BENCHMARK_CLIENT_REGION = '<operator location>'
$env:BENCHMARK_REQUIRE_CF_TIMING = '1'
$env:BENCHMARK_REQUIRE_PRODUCTION_METADATA = '1'
node scripts/latency-benchmark.mjs --url https://APP_PAGES_DOMAIN --mode warm --iterations 20 --output artifacts/latency-warm.md
node scripts/latency-benchmark.mjs --url https://APP_PAGES_DOMAIN --mode cold --iterations 20 --output artifacts/latency-cold.md
node scripts/latency-benchmark.mjs --url https://APP_PAGES_DOMAIN --mode warm --iterations 20 --json-output artifacts/after.json
node scripts/latency-compare.mjs artifacts/before.json artifacts/after.json artifacts/latency-comparison.md
```

Production evidence requires the Pages revision, Cloud Run revision, Neon
branch, deployment region, and client location. The benchmark copies only
these non-secret identifiers into the report and comparison so results cannot
be confused with a different deployment, database branch, region, or client
location. It never accepts credentials, tokens, connection strings, or request
bodies as metadata. The comparison report includes both end-to-end client
latency and the available `app`, `db`, and `cf_upstream` Server-Timing deltas.

The cold run must be started only after the operator scales the test Cloud Run
revision to zero or restarts it. The script measures the first request after
that reset; cold mode creates its session lazily inside the measured operation
so setup does not consume that first sample. It cannot manufacture a platform
cold start. Run both modes at the same client location, with the same isolated
data, revision, database branch, and concurrency. Keep the generated reports
out of source control when they contain deployment metadata.

The bootstrap path is `/api/auth/bootstrap`; it returns the authenticated user
and accessible companies in one response, so the initial workspace no longer
needs separate `/api/auth/me` and `/api/company/all` requests. Every response
now carries `X-Request-ID` and `Server-Timing`. The production
timing log contains only request ID, method, route, status, route duration,
database duration, and query count. The new timing code never logs credentials,
tokens, SQL, request bodies, or raw mail data; development SQL logging remains
controlled separately by the existing development profile.

The benchmark sends a fresh UUID with every measured request, verifies that the
same `X-Request-ID` is returned, and reports p50/p95/p99 for each parsed
`Server-Timing` metric (`app`, `db`, and `cf_upstream` when present) alongside
the end-to-end client timings. The parallel bootstrap compatibility mode omits
combined server-timing rows because it measures two requests concurrently.
Set `BENCHMARK_REQUIRE_CF_TIMING=1` for production runs to fail unless every
measured sample includes the Cloudflare `cf_upstream` metric. Also set
`BENCHMARK_REQUIRE_PRODUCTION_METADATA=1` so an un-attributed report cannot be
treated as production evidence.

Bootstrap session validation and accessible company data now come from one
scalar projection query (logo bytes are not read), cached by the frontend for
the active session, and reused by the Company page. Successful payment updates
remove the paid row locally; a page reload is needed only when removing the
last row causes pagination underflow.
The saved Taka library is also fetched in bounded pages of at most 100 entries,
with stable `takaNo`/`id` ordering and local mutation updates instead of an
immediate list refetch. Workspace members use the same bounded page pattern
with stable `createdAt`/`id` ordering, and role/delete mutations update the
visible page locally.

## Controlled local before/after result

Measured on 13 September 2026 against two fresh, identical seeded local
Spring Boot/H2 services. Each run used 20 samples and three warmups for warm
mode. The comparison is against the exact `HEAD` before the latency work and
the current working tree, using the same synthetic account/company/customer.
The baseline used `BENCHMARK_BOOTSTRAP_MODE=parallel` (the old `/me` plus
`/company/all` requests); the current run used `endpoint` (one
`/auth/bootstrap` request). Values are `before -> after` in milliseconds.

| Path | Warm p50 | Warm p95 | Warm p99 |
|---|---:|---:|---:|
| login | 218.07 -> 305.88 | 281.85 -> 478.95 | 369.79 -> 498.04 |
| bootstrap | 30.48 -> 14.73 | 126.42 -> 58.95 | 169.47 -> 65.17 |
| list | 25.77 -> 11.95 | 118.46 -> 92.30 | 121.06 -> 107.92 |
| dashboard | 52.03 -> 12.66 | 176.69 -> 15.92 | 276.39 -> 17.38 |
| create-sale | 43.00 -> 22.53 | 152.10 -> 52.00 | 162.41 -> 84.02 |

| Path | Cold p50 | Cold p95 | Cold p99 |
|---|---:|---:|---:|
| login | 295.55 -> 212.00 | 439.42 -> 217.68 | 444.44 -> 223.61 |
| bootstrap | 15.57 -> 10.81 | 138.37 -> 12.14 | 142.64 -> 13.04 |
| list | 10.43 -> 8.69 | 15.96 -> 19.30 | 20.13 -> 24.87 |
| dashboard | 19.26 -> 15.60 | 30.90 -> 20.43 | 31.36 -> 20.65 |
| create-sale | 35.03 -> 23.29 | 46.19 -> 33.83 | 48.79 -> 38.37 |

The cold mode creates a fresh session for each sample; it is a post-reset-style
application benchmark, not a claim that a local process reproduced Cloud Run
instance cold-start behavior. Run the commands above against the pre-change
and current deployed revisions for the production comparison, using the same
isolated account, data, client location, revision, and Neon branch.

## Current local runtime instrumentation check

On 13 September 2026, a local file-backed H2 Spring Boot instance was started
on `127.0.0.1:18081` with the current working tree. A disposable account,
company, and customer were created through the public API. The benchmark ran
20 samples with three warmups for warm mode and 20 fresh authenticated sessions
per operation for cold mode. The run was labelled `current-local-lazy-fetch`
and recorded `local-working-tree`, `local-spring-h2`, `local-h2`, `loopback`,
and `local` as its deployment metadata. Every measured response echoed its
request ID and returned both `app` and `db` Server-Timing metrics. These
numbers verify the instrumentation and endpoint behavior locally; H2 and
loopback do not represent Neon, Cloud Run, or Cloudflare network latency.

| Path | Warm client p50/p95/p99 (ms) | Warm app p50/p95/p99 (ms) | Warm db p50/p95/p99 (ms) |
|---|---:|---:|---:|
| login | 98.96 / 106.45 / 117.88 | 98 / 105 / 116 | 4 / 4 / 7 |
| bootstrap | 8.20 / 13.02 / 13.64 | 6 / 10 / 11 | 2 / 4 / 4 |
| list | 6.55 / 13.25 / 13.42 | 5 / 12 / 12 | 2 / 3 / 4 |
| dashboard | 11.31 / 17.76 / 19.52 | 10 / 16 / 18 | 6 / 10 / 13 |
| create-sale | 15.60 / 20.61 / 39.32 | 14 / 19 / 37 | 9 / 13 / 15 |

| Path | Cold client p50/p95/p99 (ms) | Cold app p50/p95/p99 (ms) | Cold db p50/p95/p99 (ms) |
|---|---:|---:|---:|
| login | 96.51 / 269.00 / 269.59 | 94 / 267 / 267 | 3 / 7 / 7 |
| bootstrap | 21.17 / 24.42 / 26.61 | 15 / 18 / 19 | 5 / 5 / 7 |
| list | 18.29 / 20.16 / 20.19 | 15 / 17 / 18 | 6 / 7 / 7 |
| dashboard | 29.18 / 32.49 / 192.82 | 26 / 30 / 186 | 15 / 19 / 78 |
| create-sale | 43.90 / 50.36 / 56.65 | 39 / 44 / 51 | 24 / 27 / 29 |

The raw local reports are generated under `artifacts/` and should remain out
of source control. A real Pages/Cloud Run/Neon comparison is still required;
no deployed URL or production credentials are present in this workspace.

## Connection budget

Use a pooled Neon JDBC URL for application traffic. Set the application pool
so the hard upper bound is:

```
Cloud Run max instances x Hikari maximumPoolSize
  <= Neon max_connections - reserved/admin headroom
```

The checked-in defaults are 10 x 8 = 80 possible application connections.
Verify the actual Neon `max_connections` for the production compute size and
leave headroom for migrations, SQL editor sessions, and operational access;
reduce `DB_POOL_MAX_SIZE` before increasing Cloud Run max instances.
The scheduled audit cleanup uses a transaction-level PostgreSQL advisory lock,
which is compatible with Neon’s transaction pooler; the local H2 fallback uses
an in-process lock for tests.

## Placement and startup experiments

- Keep Cloud Run and Neon in the same or nearby region after comparing the
  actual request and DB timings. Record region, compute size, pooler/direct
  URL type, and the benchmark report.
- Keep one Cloud Run minimum instance for this interactive application. Test
  `minScale: 0` versus `1` only with the warm/cold reports and cost data.
- Startup CPU boost is enabled in the checked-in Cloud Run template. Compare
  the same revision with the setting disabled if startup CPU is not the
  measured bottleneck.
- Test Cloudflare Smart Placement as an A/B experiment: same Pages revision,
  same backend, same client regions, and at least 20 warm plus 20 post-reset
  samples per arm. Accept it only if p95 and p99 improve without harming the
  measured browser/client regions; Smart Placement can move middleware and
  assets toward the backend, so verify static asset timing too.

References: [Cloudflare Smart Placement](https://developers.cloudflare.com/pages/functions/smart-placement/),
[Cloud Run minimum instances](https://cloud.google.com/run/docs/configuring/min-instances),
[Cloud Run startup CPU boost](https://cloud.google.com/run/docs/configuring/services/cpu),
and [Neon connection pooling](https://neon.com/docs/connect/connection-pooling).
