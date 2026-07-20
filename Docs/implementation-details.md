# Multi-user and Multi-business Implementation Details

## 1. Objective

Turn the current textile management application into a secure, multi-tenant product that can be used by many businesses and many users.

The implementation must support:

- Public account creation
- Email/password login
- Google login
- Password reset and email verification
- Multiple users per business
- Multiple businesses under one workspace
- Devashish Textile and Ritika Creation under one group, while preserving their separate legal records
- Secure cloud storage and backups
- Strict isolation between customers' data

The current application is a React/Vite frontend, Spring Boot backend, PostgreSQL database and Flyway migrations. The existing company-scoping code is a useful starting point, but it is not yet a complete authorization system.

## 2. Important security issue in the current implementation

`CompanyContextFilter` currently checks whether the `X-Company-Id` exists, but does not check whether the authenticated user belongs to that company. A future user could change the header and potentially read or modify another company's records.

The following endpoints also need authorization before public signup:

- `/api/company/all`
- `/api/company/{id}`
- `/api/company/{id}` with `PUT`
- Company creation and deletion operations
- All customer, supplier, purchase, sale and payment operations

The server must derive access from the authenticated user and database memberships. The browser-provided company ID can only be treated as a requested context, never as proof of permission.

## 3. Target domain model

Use the following hierarchy:

```text
User
  └── Workspace membership
        └── Workspace / business group
              ├── Devashish Textile
              └── Ritika Creation
```

### Workspace

A workspace represents one customer account or business group. It is the primary tenant boundary.

Suggested fields:

```text
workspaces
- id
- name
- slug
- created_by
- created_at
- updated_at
- status
```

### Business

Keep the existing `company_profiles` table initially and add workspace ownership instead of renaming it immediately. A later cleanup can rename it to `businesses`.

```text
company_profiles
- id
- workspace_id
- trade_name
- gst_no
- phone
- address
- default_broker
- default_quality
- created_at
- updated_at
- status
```

Devashish Textile and Ritika Creation should remain separate businesses if they have different GST registrations, invoice numbers or legal ownership. They can still be selected from the same workspace.

### Users and authentication identities

The existing `users` table should be expanded or migrated into:

```text
users
- id
- email
- display_name
- avatar_url
- email_verified_at
- status
- created_at
- updated_at

auth_identities
- id
- user_id
- provider              # LOCAL or GOOGLE
- provider_subject      # Stable provider user ID
- password_hash         # Only for LOCAL identities
- created_at
- last_login_at
```

Do not use an email address as the permanent Google identity key. Use the provider subject (`sub`) and keep the email as a verified profile value.

### Memberships and roles

```text
workspace_members
- workspace_id
- user_id
- role                  # OWNER, ADMIN, MANAGER, STAFF, ACCOUNTANT, VIEWER
- created_at
- invited_by

business_members        # Add only if business-level permissions are required
- business_id
- user_id
- role
- created_at

invitations
- id
- workspace_id
- email
- role
- token_hash
- expires_at
- accepted_at
- invited_by
- created_at
```

Start with workspace-level roles. Add `business_members` only when users genuinely need different access to Devashish and Ritika.

## 4. Database changes

Create Flyway migrations in this order:

### V7: Identity foundation

- Add stable numeric IDs and email fields to `users`.
- Create `auth_identities`.
- Add `status`, `email_verified_at` and timestamps.
- Add unique indexes on normalized email and `(provider, provider_subject)`.
- Do not keep the default family credentials as the production authentication mechanism.

### V8: Workspaces and memberships

- Create `workspaces`.
- Create `workspace_members`.
- Add `workspace_id` to `company_profiles`.
- Backfill one workspace for the existing data.
- Add the current family account as the initial `OWNER`.
- Add foreign keys and indexes.

### V9: Invitations and audit history

- Create `invitations`.
- Create `audit_logs`.
- Record user, workspace, business, action, object ID, timestamp and request metadata.
- Never store passwords or access tokens in audit records.

### V10: Data correctness hardening

- Change monetary fields from `DOUBLE PRECISION` to `NUMERIC(19,2)`.
- Map Java monetary values to `BigDecimal`.
- Add `@Version` to editable entities where concurrent edits are possible.
- Add required indexes on `(company_id, date)`, `(company_id, status)` and foreign keys.
- Add explicit soft-delete fields for records that must remain in financial history.

### V11: Number allocation

Create a transactional number allocation table:

```text
business_number_sequences
- business_id
- financial_year
- next_challan_number
- next_bill_number
```

Allocate numbers with a database transaction and row locking. Do not use `MAX(challan_no) + 1` when multiple users can create sales at the same time.

## 5. Authentication implementation

Use a managed OpenID Connect provider. Auth0 is the recommended first implementation for this Spring Boot application; Keycloak is an alternative if self-hosting is required.

Required flows:

1. User opens the login page.
2. User chooses email/password or Google.
3. The identity provider authenticates the user.
4. The backend validates the issuer, audience, signature and expiry using the provider's JWKS endpoint.
5. The backend finds or creates the local `users` record.
6. The backend returns the authenticated user and available workspaces.

Required frontend pages:

- Sign up
- Login
- Google login button
- Verify email
- Forgot password
- Reset password
- Accept invitation
- Initial workspace/business setup
- Account settings

The current custom JWT implementation in `JwtService` should be retired or limited to a controlled internal migration period. Prefer short-lived access tokens and secure refresh-token handling. If cookies are used, enable CSRF protection and configure `HttpOnly`, `Secure` and `SameSite` attributes.

## 6. Authorization implementation

Create a single backend authorization service, for example:

```text
AuthorizationService.requireWorkspaceMember(userId, workspaceId)
AuthorizationService.requireBusinessAccess(userId, businessId)
AuthorizationService.requirePermission(userId, businessId, permission)
```

Every controller should use the authenticated principal, not a username supplied by the request.

The current company context can remain temporarily, but it must resolve like this:

```text
authenticated user
  → workspace membership
  → business membership or workspace ownership
  → business context
```

Every repository lookup must include the authorized business scope. For example:

```text
findByIdAndCompany_Id(id, authorizedCompanyId)
```

Do not load a record by ID first and check its company afterward. The scope must be part of the query.

Suggested permissions:

```text
business.read
business.manage
customers.read
customers.write
sales.read
sales.write
payments.read
payments.write
reports.read
members.manage
settings.manage
```

## 7. API changes

### Authentication

```text
GET  /api/auth/me
POST /api/auth/logout
POST /api/auth/refresh              # Only if the selected auth flow needs it
```

Provider-specific callback endpoints should be kept behind the authentication integration rather than exposing password-handling logic in application controllers.

### Workspace and business selection

```text
GET  /api/workspaces
POST /api/workspaces
GET  /api/workspaces/{workspaceId}/businesses
POST /api/workspaces/{workspaceId}/businesses
GET  /api/businesses/{businessId}
PUT  /api/businesses/{businessId}
```

The existing `GET /api/company/all` should be replaced with a membership-filtered endpoint. If `X-Company-Id` is retained during migration, the backend must validate it against the current user's membership.

### Member management

```text
GET    /api/workspaces/{workspaceId}/members
POST   /api/workspaces/{workspaceId}/invitations
DELETE /api/workspaces/{workspaceId}/members/{userId}
PATCH  /api/workspaces/{workspaceId}/members/{userId}/role
```

### Domain endpoints

Existing customers, suppliers, purchases, sales, payments and PDF endpoints can remain mostly unchanged if their service and repository layers consistently apply the authorized business scope.

Use request/response DTOs and validation rather than exposing JPA entities directly from controllers.

## 8. Migration of current data

Run the migration in a transaction where possible and create a verified backup first.

1. Stop public access to the application.
2. Back up PostgreSQL and record the backup checksum.
3. Create a workspace named `Devashish Business Group`.
4. Create or migrate the current user as its `OWNER`.
5. Attach Devashish Textile and Ritika Creation to the workspace.
6. Preserve each company's existing ID and all `company_id` values.
7. Verify sales, purchases, payments, customers, suppliers and PDFs for both businesses.
8. Replace the shared family login with the owner's verified account.
9. Run authorization tests against both business IDs.
10. Keep the old login disabled after the migration is verified.

Do not combine the two businesses' historical sales into one company unless their legal, tax and numbering records are also legally the same.

## 9. Cloud storage and deployment

Recommended initial production architecture:

```text
Cloudflare Pages/CDN
        ↓
Spring Boot API on Koyeb, Render or AWS
        ↓
Managed PostgreSQL
        ↓
Private object storage for PDFs and images
```

The backend must remain stateless so multiple instances can run behind a load balancer.

Store in object storage:

- Generated bills
- Challans
- Logos
- Uploaded business documents

Use private buckets and short-lived signed download URLs. Do not expose permanent public file URLs.

Use a secret manager for:

- Database credentials
- OAuth client secrets
- JWT/OIDC configuration
- Object storage keys
- Email provider credentials

The database should not be publicly reachable except through the application network or an approved administrative path.

## 10. Security baseline

- HTTPS everywhere
- Strict CORS allowlist
- Content Security Policy
- HSTS and secure response headers
- Secure, HttpOnly, SameSite cookies if cookies are used
- CSRF protection for cookie-authenticated requests
- Rate limiting on login, signup, password reset and invitations
- Generic authentication error messages
- Input validation and output encoding
- File type, size and malware checks for uploads
- No secrets in Git, frontend bundles or logs
- No passwords, tokens or private customer data in logs
- Encrypted backups
- Point-in-time database recovery
- Periodic restore testing
- Audit logs for financial and permission changes
- Automated tenant-isolation tests
- Optional PostgreSQL row-level security as defense in depth

The H2 console must remain disabled in production.

## 11. Testing strategy

### Backend tests

- Signup and email verification
- Password login and failed-login rate limiting
- Google identity validation
- Invitation acceptance
- Role and permission checks
- Access denial for another workspace
- Access denial for another business in the same database
- PDF download authorization
- Company profile authorization
- Concurrent challan and bill number allocation
- Flyway migration from a copy of the current production database

Use PostgreSQL integration tests, preferably with Testcontainers, for constraints, transactions and locking behavior.

### Frontend tests

- New user onboarding
- Login and logout
- Google login callback
- Workspace and business switching
- Role-based navigation
- Expired session handling
- Unauthorized API response handling
- Invitation acceptance

### Release checks

- Run lint and build for frontend
- Run Maven tests and package build
- Run database migration on a restore copy
- Verify backups and restore procedure
- Run a security scan before public signup

## 12. Implementation phases

### Phase 0: Security correction

- Fix company authorization
- Restrict company endpoints
- Add tenant-isolation tests
- Disable unsafe production defaults

### Phase 1: Identity and tenant foundation

- Add users and auth identities
- Add workspaces and memberships
- Add roles and permissions
- Migrate the current family account

### Phase 2: Product authentication

- Add signup
- Add email verification
- Add Google login
- Add password recovery with one-time hashed reset tokens, Gmail SMTP delivery, 15-minute expiry, rate limiting and session invalidation
- Add logout and session expiry

### Phase 3: Business collaboration

- Add invitations
- Add member management
- Add role-based access
- Add business switching under one workspace

### Phase 4: Financial correctness and scale

Implemented in the current build:

- Replace floating-point money fields
- Add transactional invoice sequences
- Add pagination and database-level dashboard aggregation
- Move PDFs to private object storage

### Phase 5: Production hardening

Implemented in the current build:

- Add backups and restore drills (documented deployment runbook)
- Add monitoring and alerts (database-backed `/health` plus deployment alert checklist)
- Add rate limiting (login, signup, password reset, and invitation acceptance)
- Add security headers
- Add server-side audit logging with retention; user-facing audit reporting is intentionally omitted
- Launch invite-only beta configuration (`APP_SIGNUP_ENABLED=false` in production)

## 13. Definition of done

The system is ready for wider release when:

- A user can sign up and log in securely.
- A user can log in with Google and link the account safely.
- An owner can create a workspace and business.
- An owner can invite staff with a selected role.
- Devashish Textile and Ritika Creation remain correctly separated under one workspace.
- Users cannot read or modify data outside their memberships.
- Invoice numbers remain unique under concurrent use.
- PDFs are private and authorized.
- Production secrets are stored outside the repository.
- Backups have been restored successfully in a test environment.
- Monitoring can detect authentication errors, API failures and database problems.
- The application passes tenant-isolation, migration and production smoke tests.
