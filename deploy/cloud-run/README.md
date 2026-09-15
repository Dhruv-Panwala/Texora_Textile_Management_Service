# Cloud Run deployment

The checked-in `service.yaml` is the production shape for the backend. Replace
`APP_PAGES_DOMAIN` with the real Pages custom domain and keep the image and
service-account placeholders rendered by `cloudbuild.yaml`; do not commit
secret values.

Repository-enforced controls:

- Cloud Run ingress is `internal-and-cloud-load-balancing`; the service is
  intended to sit behind an external HTTPS load balancer, Cloudflare, and
  Cloud Armor. Do not point Pages directly at a public `run.app` URL.
- The container runs as the non-root `app` user, exposes port 8080, and has a
  database-backed readiness probe plus a database-independent liveness probe.
- Minimum/maximum instances, concurrency, timeout, production profile,
  disabled seeding, secure cross-site cookies, trusted-proxy mode, and
  Secret Manager references are version-controlled.
- `cloudbuild.yaml` builds only `./TextileManagement`; the legacy Flask
  prototype is excluded by `.gcloudignore` and is not a production build
  context.

Cloud Run/GCP actions still required:

1. Create the Artifact Registry repository and runtime service account.
2. Grant the runtime account `roles/secretmanager.secretAccessor` only on the
   named database, storage, and mail secrets. Grant the deployer only the
   minimum Cloud Run/Artifact Registry/IAM Service Account User permissions.
3. Create the external HTTPS load balancer with a serverless NEG targeting this
   service, attach Cloud Armor, and keep the Cloud Run `run.app` URL out of DNS.
   Choose the load-balancer-to-service IAM policy deliberately; do not grant
   `allUsers` unless the load balancer design requires it and compensating
   controls are enabled.
4. Configure the service domain/region and verify the startup and liveness
   probes return 200 through the load balancer.
5. Use a Neon pooled JDBC URL for application traffic and a direct Neon URL
   for migrations/administration. Store both only in Secret Manager.

Cloudflare actions still required:

- Proxy the Pages/custom API hostname through Cloudflare and route it to the
  load balancer, not directly to `run.app`.
- Add a Request Header Transform Rule for the API origin that removes or
  overwrites visitor-controlled `Forwarded`, `X-Forwarded-For`,
  `X-Forwarded-Host`, `X-Forwarded-Proto`, `X-Real-IP`, and `True-Client-IP`.
  Cloudflare must supply the canonical `CF-Connecting-IP` value.
- Set the Pages Function `BACKEND_URL` server-side. Never expose it as a
  `VITE_*` variable and never accept forwarding headers from the browser.
- Enable WAF/rate rules for login, signup, reset, invitation, and API abuse;
  the backend limiter remains the authoritative cross-instance guard.
