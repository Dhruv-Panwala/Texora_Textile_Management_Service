# Production Latency Improvements

This document records the latency improvements identified from the production-performance review. It excludes server sleep/cold-start delays.

## Priority 1: Fix the Payments endpoint

### Current problem

`GET /api/payments` loads every purchase and sale for the active company, filters out paid records in Java, creates full entity graphs, and only then sorts the result.

Relevant files:

- `TextileManagement/src/main/java/com/example/TextileManagement/controller/PaymentController.java`
- `TextileManagement/src/main/java/com/example/TextileManagement/repository/PurchaseRepository.java`
- `TextileManagement/src/main/java/com/example/TextileManagement/repository/SaleRepository.java`
- `project/src/pages/Payments.tsx`

### Changes

- Add database queries that filter `status <> 'PAID'` in SQL/JPQL.
- Return a small payment-list DTO instead of full `Sale` and `Purchase` entities.
- Add pagination, for example 25 or 50 records per response.
- Apply ordering in the database using `dueDate`.
- Add a stable secondary sort, such as `id`, to keep pagination consistent.
- Update the frontend to request pages and render pagination controls.
- Keep the existing client-side grouping by supplier/customer only after receiving the reduced response.

### Acceptance criteria

- The response size remains bounded as the number of sales and purchases grows.
- Paid records are not transferred from the database.
- The endpoint does not load full entity relationships just to build payment rows.
- Payments remain correctly ordered by due date across pages.

## Priority 2: Reduce initial request latency

### Current problem

The authenticated application performs sequential requests before displaying the workspace:

1. Validate the session with `/api/auth/me`.
2. Load accessible companies with `/api/company/all`.
3. Load the active page data.

The Company page also loads the profile first and then loads the logo.

Relevant files:

- `project/src/app/App.tsx`
- `project/src/pages/Company.tsx`

### Changes

- Keep session validation, but consider combining session and accessible-company data into one authenticated bootstrap endpoint.
- If the endpoints remain separate, start independent requests as early as possible and avoid unnecessary repeated validation.
- Load the company profile and logo concurrently with `Promise.all` where the UI does not require one response before starting the other.
- Cache the active company profile for the current session and invalidate it after profile updates.
- Avoid refetching company data when switching between pages if the active company has not changed.

### Acceptance criteria

- The first authenticated page requires fewer sequential network round trips.
- The Company page does not wait for the logo request before showing the profile form.
- Changing routes does not repeatedly reload unchanged company data.

## Priority 3: Improve perceived loading performance

### Current problem

The application blocks the whole shell while checking the session and loading the company. Individual pages mostly display plain loading text.

Relevant files:

- `project/src/app/App.tsx`
- `project/src/pages/Dashboard.tsx`
- `project/src/pages/Customers.tsx`
- `project/src/pages/Suppliers.tsx`
- `project/src/pages/Purchases.tsx`
- `project/src/pages/Sales.tsx`
- `project/src/pages/Payments.tsx`

### Changes

- Keep the navigation shell visible while page data loads when authentication is already known.
- Replace plain loading text with skeleton rows/cards matching the final layout.
- Show existing data while refresh requests are in progress instead of blanking the page.
- Disable only the controls affected by a request rather than the entire page.
- Reserve space for tables, cards, and error messages to reduce layout shifts.
- Preserve the Dashboard 30-second refresh guard, but show a small refresh state instead of replacing the dashboard content.

### Acceptance criteria

- Users see stable page structure immediately after navigation.
- Refreshing a page does not cause the main content to disappear.
- Loading states do not create noticeable layout shifts.

## Priority 4: Add frontend route-level code splitting

### Current problem

All application pages are statically imported in `project/src/app/App.tsx`. Users download business pages even when opening the login screen or a single route.

The current production build is approximately:

- JavaScript: 291 KB raw
- JavaScript: 83.65 KB gzip
- CSS: 34 KB raw

This is not currently a critical bottleneck, but route splitting will reduce the initial JavaScript cost.

### Changes

- Replace page imports with `React.lazy` or dynamic imports.
- Add a `Suspense` fallback using the shared loading/skeleton UI.
- Keep authentication pages in the initial chunk if desired.
- Lazy-load business pages such as Dashboard, Sales, Purchases, Payments, Customers, Suppliers, Company, and Members.
- Rebuild and compare the initial chunk and route chunk sizes.

### Acceptance criteria

- The initial login/bootstrap bundle excludes pages that are not being viewed.
- Each route loads successfully on direct navigation and browser refresh.
- The loading fallback is visually stable and does not cause layout shifts.

## Priority 5: Improve logo delivery

### Current problem

Uploaded logos are stored and served as the original PNG/JPEG bytes, up to 1 MB and 2000 x 2000 pixels. The logo endpoint is marked `no-store`, so the browser must download it again when caching could safely be used.

Relevant files:

- `TextileManagement/src/main/java/com/example/TextileManagement/controller/CompanyController.java`
- `TextileManagement/src/main/java/com/example/TextileManagement/entities/CompanyProfile.java`
- `project/src/pages/Company.tsx`

### Changes

- Resize oversized logos during upload while preserving the required PDF quality.
- Consider converting delivery copies to WebP or another modern format for browser previews.
- Keep the original or a high-quality derivative for PDF generation if required.
- Add a versioned logo URL based on the company record version or updated timestamp.
- Replace unconditional `no-store` for safe versioned logo responses with a long-lived cache policy.
- Continue preventing caching of sensitive API JSON responses.

### Acceptance criteria

- Company logo previews use bounded, appropriately sized assets.
- Repeated visits do not redownload an unchanged logo.
- PDF output retains the required logo quality.

## Priority 6: Strengthen database query efficiency

### Current state

Sales and purchase list endpoints already use pagination, list projections, and company/date indexes. Dashboard queries already aggregate in the database instead of returning all rows. Customer and supplier lists, saved Taka entries, and workspace members use bounded pages with deterministic secondary ordering; the Sales picker exposes previous/next controls instead of loading the full company library in one response. Workspace member role/delete mutations update the visible page locally. Business many-to-one relations are lazy by default, with explicit fetch graphs on detail/update paths.

### Changes

- Apply the same projection approach used by sales and purchases to Payments.
- Review all broad `findAllByCompany_Id` calls and replace them with filtered or paginated queries where the caller does not need the complete dataset.
- Change unnecessary `EAGER` many-to-one relationships to `LAZY`, then explicitly fetch only the relationships needed by each endpoint.
- Confirm indexes with PostgreSQL `EXPLAIN (ANALYZE, BUFFERS)` using production-like data.
- Add indexes that match the final payment query, likely beginning with `(company_id, status, due_date)` if query plans show they are beneficial.
- Add a deterministic secondary ordering column to paginated list queries.

### Acceptance criteria

- List and payment queries remain bounded in memory and response size.
- Query plans use company/status/date indexes for payment retrieval.
- No new N+1 relationship queries appear after changing fetch strategies.

## Priority 7: Measure production latency

Implementation should be validated with real timings rather than code inspection alone.

### Measurements

- Browser DevTools: DNS, connection, request waiting, download, and response sizes.
- Backend request duration for `/api/auth/me`, `/api/company/all`, `/api/dashboard`, `/api/payments`, and list endpoints.
- Database duration and row counts for payment and list queries.
- Initial JavaScript transfer size and route chunk sizes.
- Logo response size and cache hit rate.

### Suggested targets

- No unbounded list endpoint for interactive screens.
- Payment endpoint returns only the requested page and required fields.
- Initial authenticated bootstrap uses no more than two sequential application-level round trips before page rendering.
- Skeleton content appears immediately for data-heavy pages.
- No single normal logo preview request exceeds the optimized asset budget agreed for production.

## Recommended implementation order

1. Replace the Payments endpoint with a filtered, projected, paginated endpoint.
2. Add request timing and database query measurements.
3. Improve bootstrap and Company-page request sequencing.
4. Add skeleton and progressive loading states.
5. Add route-level code splitting.
6. Optimize and cache versioned logo assets.
7. Recheck PostgreSQL query plans with production-like data.
