# Textile Manager

A full-stack business-management application for textile trading workflows. It supports multi-company operations, sales and purchase records, payments, printable challans/bills, and fast taka entry through voice capture or a reusable library.

## Highlights

- Role-based, multi-company workspace with secure session authentication.
- Sales, purchases, customers, suppliers, payments, and dashboard reporting.
- Challan and bill PDF generation with financial-year numbering.
- Voice-assisted taka capture with editable review and saved reusable taka entries.
- Fixed-precision quantity handling, validation, audit logging, and optimistic concurrency checks.

## Tech Stack

- **Frontend:** React, TypeScript, Vite, Tailwind CSS
- **Backend:** Java 17, Spring Boot, Spring Security, Spring Data JPA
- **Data:** PostgreSQL with Flyway migrations; H2 for local development
- **Testing:** Vitest, React Testing Library, JUnit, Mockito, Testcontainers

## Run Locally

Prerequisites: Node.js 20+, Java 17+, and Git.

Start the backend:

```powershell
cd TextileManagement
$env:SPRING_PROFILES_ACTIVE = "local"
.\mvnw.cmd spring-boot:run
```

In a second terminal, start the frontend:

```powershell
cd project
npm ci
npm run dev
```

Open the URL displayed by Vite. The local profile uses a file-backed H2 database and does not require production credentials.

## Tests

```powershell
cd project
npm test

cd ..\TextileManagement
.\mvnw.cmd test
```

## Repository Layout

```text
project/             React frontend
TextileManagement/   Spring Boot API and database migrations
deploy/              Deployment configuration
scripts/             Development and benchmark utilities
```
