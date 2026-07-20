# Devashish Textile Management

Production-ready direction for the textile website:

- React/Vite frontend in `project`
- Spring Boot backend in `TextileManagement`
- PostgreSQL persistence through Docker Compose
- Server-side challan and bill PDF downloads from Spring Boot

## Local Development

Backend:

```powershell
cd TextileManagement
.\mvnw.cmd spring-boot:run
```

Frontend:

```powershell
cd project
npm run dev
```

Local backend runs on the `local` Spring profile by default and uses a file-backed H2 database, so data now survives stopping and restarting the app.

Default local login is `family` / `family123`. Change it with `APP_FAMILY_USERNAME` and `APP_FAMILY_PASSWORD`.

## Docker Compose

Create a real `.env` from `.env.example`, change every password/secret, then run:

```powershell
docker compose up --build
```

Frontend: `http://localhost:3000`

Backend: `http://localhost:8080`

PostgreSQL data is stored in the `postgres_data` Docker volume and survives container restarts.

The app now supports two company profiles, `Devashish Textile` and `Ritika Creation`, with a sidebar dropdown to switch the active business context. Sales challan numbers, bill numbers, customers, suppliers, purchases, payments, dashboard totals, and PDF headers are all scoped to the selected company.

Sales numbering behavior:

- Challan and bill numbers still track the financial year.
- You can manually enter the starting challan number or bill number when launching mid-year.
- Later auto-generated numbers continue from the highest number already used in that financial year.
- If a sale has more than 48 takas, the app generates multiple challan pages for the same bill and uses a continuous challan range.

## Database Modes

- `local`: file-backed H2 in `TextileManagement/data/textile_management.mv.db`
- `dev`: in-memory H2 for throwaway runs
- `prod`: PostgreSQL via environment variables

To switch to an online database later, set `SPRING_PROFILES_ACTIVE=prod` and provide `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`.

The frontend now defaults to same-origin `/api` requests, with `VITE_API_BASE_URL` only needed when you deliberately host the backend on a separate origin. Login state uses an HttpOnly session cookie and is validated against `GET /api/auth/me` before protected data loads.

## Env Files

- `.env.example` is the safe template committed to git.
- `.env` is your real local secret file and should stay out of git.

## Prototype Folder

- `Challan and Bill creation` is the old Python prototype.
- Its Python dependencies now live in `Challan and Bill creation/requirements.txt`.
- The live deployment uses the React frontend and Spring Boot backend only.

## Server Checklist

- Use strong values for `APP_FAMILY_PASSWORD` and `POSTGRES_PASSWORD`.
- Keep `APP_SESSION_COOKIE_SECURE=true` in production and serve the backend over HTTPS.
- Set `APP_CORS_ALLOWED_ORIGIN` to the public frontend URL.
- Set `VITE_API_BASE_URL` to the public backend URL.
- Use `/health` for production health checks.
- For Cloudflare Pages + Koyeb + Neon deployment steps, see `DEPLOYMENT.md`.
- Keep PostgreSQL volume backups. Example:

```powershell
docker exec devashish_textile-postgres-1 pg_dump -U textile -d textile_management > backup.sql
```

## Documents

Each sale can download:

- `GET /api/sales/{id}/challan.pdf`
- `GET /api/sales/{id}/bill.pdf`

The PDF renderer uses the same logo, Bappa, Mantra, A4 sizing, gold table styling, taka grouping, GST calculation, and footer structure as the original Python prototype.
