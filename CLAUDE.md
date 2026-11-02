# Invento POS

Web point-of-sale for the shop products already managed in the Invento Android app
(`idibel79/invento`, read-only reference). Backend Java 21 + Spring Boot 3 in `backend/`,
front React + TypeScript (Vite) in `frontend/` (from step 2). Answer the user (idriss) in French;
he is a Java / Spring Boot / Flutter developer.

The functional reference is the scoping document `pos-web-cadrage.md` (decisions, roles, data
model, steps). The decisions below were taken after it and override it where they differ.

## Architecture decisions

- **Online multi-user from day one.** PostgreSQL everywhere (no H2, not even for tests),
  authentication and several tills (`caisse`) from step 1. The single-till offline mode is dropped
  for now.
- **Hosting while testing:** Supabase used only as a plain PostgreSQL (no Supabase-specific
  feature: no Auth, no Storage, no Data API; tables live in the `pos` schema, which the Data API
  does not expose) + the app on Google Cloud Run.
- **Migration day (Raspberry Pi or local PC):** `docker-compose.yml` with 2 containers, `app`
  (Spring Boot, which will also serve the React build) and `db` (PostgreSQL). Images are
  multi-arch (`linux/amd64` + `linux/arm64`). Data is moved with `pg_dump` / `pg_restore`.
- **Stateless API**, so it can run on several instances: JWT access tokens, refresh tokens stored
  in the database, nothing kept in memory between requests. Scheduled jobs use ShedLock (add it
  with the first scheduled job, step 2).
- **Stock:** atomic conditional updates (`stock - stock_reserve >= qty`) or optimistic locking
  (`produit.version`), never a Java-side check alone. A sale is blocked when the available stock
  is 0, negative or lower than the requested quantity.
- **Sales on hold** (customer went to fetch their wallet): status `EN_ATTENTE`, the cart is kept
  intact (prices, discounts) with an optional note; stock is **reserved** (`produit.stock_reserve`)
  for at most **5 minutes**, then the sale is cancelled automatically and the stock released; it is
  also cancelled when the till session is closed before that. Resumable **only on the same till**.
- **Admin recovery:** a single admin, created on first start with a temporary password (to change
  at first login) and a recovery code shown once, stored hashed, single use. A server command
  `--reset-admin` sets a new temporary password. Both are written to the audit journal.
- **Front:** based on mockup 1 (Invento look), not the dark counter mockup.

## Clean Code and Architecture Rules — Invento POS

### General principles
- Prefer simple, readable, maintainable and testable code.
- Apply SOLID principles pragmatically; avoid unnecessary abstractions.
- Keep business rules centralized and never duplicate them.
- Use explicit names, strict types and small cohesive methods.
- Do not add dependencies without a clear technical justification.
- Do not refactor unrelated code when implementing a feature.

### Backend — Java 21 / Spring Boot 3
- Use a modular monolith organized by business domain (`catalogue`, `importation`, `identite`,
  `caisse`, `parametre`, `journal`, `stock`, then `vente`…), with a `shared` package for
  cross-cutting concerns only.
- Separate API, application, domain and infrastructure responsibilities (`api`, `application`,
  `domain`, `infrastructure` sub-packages). JPA entities live in `domain` (pragmatic choice); pure
  business rules live in `domain` without Spring.
- Keep controllers thin; business logic belongs in application/domain layers.
- Use DTOs for API contracts (Java records); do not expose JPA entities directly.
- Version the API under `/api/v1`. Errors are returned as RFC 9457 `ProblemDetail`.
- The OpenAPI document (springdoc, `/v3/api-docs`) is the contract: the TypeScript client is
  generated from it, never hand-written.
- Use BigDecimal for every monetary calculation.
- Centralize pricing, VAT, promotions and discount calculations.
- Use Jakarta Bean Validation and business-level validation.
- Use Flyway for database schema migrations. Never edit a migration that has been merged; add a
  new one.
- PostgreSQL only. Integration tests run against a real PostgreSQL with Testcontainers.
- Store dates as `Instant` (UTC, `timestamptz`); the shop time zone is a setting
  (`fuseau_horaire`) used only for display and daily reports. Inject `Clock`, never call
  `Instant.now()` directly.
- Configuration comes from environment variables; profiles `dev`, `test`, `prod`.
- Avoid N+1 queries, unnecessary entity loading and database calls in loops.
- Use @Transactional at application use-case boundaries.
- Keep transactions short.

### Frontend — React / TypeScript
- Organize code by feature/domain.
- Keep UI components focused on presentation and interaction.
- Encapsulate API calls in dedicated services/hooks, on top of the generated client.
- Use strict TypeScript; avoid any.
- Separate server state, local UI state and temporary business state:
  TanStack Query for server state, `useReducer` for the cart, React Hook Form + Zod for forms.
  No other global store unless a real need appears.
- Do not duplicate server data across multiple client stores.
- Do not consider frontend calculations or validations authoritative.
- Keep the UI responsive during API calls and failures.
- Visuals follow mockup 1 (Invento) and the `invento-design` skill.

### State and business consistency
- The backend is the source of truth for prices, discounts, VAT, payments and stock.
- Recalculate and validate all critical amounts on the backend.
- Do not store redundant derived state.
- Invalidate or refresh affected server data after successful mutations.
- Handle loading, error, empty and success states explicitly.

### Concurrency and transactions
- Ensure sale finalization is atomic.
- Save sale, lines, payments, stock updates and stock movements in one transaction.
- Prevent negative stock through atomic conditional database updates or appropriate locking.
- Never rely only on a Java-side stock check before updating.
- Make sale submission idempotent (idempotency key) to prevent duplicate sales.
- Handle concurrent modifications explicitly.
- Never use frontend button disabling as the only concurrency protection.
- Keep all role and permission checks on the backend.

### Performance
- Optimize based on measurements, not assumptions.
- Index fields used frequently for product search and barcode lookup.
- Fetch only the data required by each screen.
- Avoid unnecessary React renders and expensive calculations.
- Keep barcode scanning immediate; do not debounce scanner input.
- Avoid unnecessary API requests.
- Use pagination for large histories and reports.
- Process large Excel imports/exports efficiently (preloaded lookups, JDBC batching).

### Security and audit
- Hash passwords and PINs (BCrypt); never store or log them in plain text.
- Short-lived JWT access token (15 min) + rotating refresh token in an `HttpOnly`, `Secure`,
  `SameSite=Strict` cookie, stored hashed in the database; reuse of a revoked refresh token
  revokes all the user's tokens.
- Lock an account after 5 failed logins (and 5 failed PINs); only the admin unlocks it.
- Restrict CORS to the configured origins. HTTPS is mandatory online.
- No secret in the repository (`.env` is ignored; `.env.example` holds placeholders).
- Enforce authorization on every sensitive backend operation.
- Keep an audit trail (`journal_action`) for sensitive operations.
- Never delete users with historical activity; deactivate them instead.
- Never expose internal errors or sensitive information in API responses.
- Dependabot keeps Maven, npm, Docker and GitHub Actions dependencies up to date.

### Logs and observability
- SLF4J only. JSON (ECS) logs in `prod`, readable logs in `dev`.
- Every request gets a request id (`X-Request-Id`, MDC `requestId`) returned in the response.
- Never log personal data, passwords, PINs, tokens or payment details.
- Actuator exposes `/actuator/health` only (liveness/readiness probes).

### Verification
- Add unit and integration tests for business rules: JUnit 5 + AssertJ + Mockito for the backend,
  Vitest + Testing Library for the front, Playwright for the sale flow.
- Test critical database operations on PostgreSQL (Testcontainers).
- Test concurrent sales and stock consistency.
- Test duplicate sale submissions and transaction rollback.
- Formatting and lint: Spotless (palantir-java-format) for Java, ESLint + Prettier for the front.
- JaCoCo coverage report, with a minimum (80 % of lines) on the `domain` packages.
- Run relevant tests and builds after changes (`cd backend && ./mvnw verify`).
- Review the complete diff and avoid unrelated modifications.

## CI/CD and Git

- GitHub Actions (`.github/workflows/ci.yml`): build, tests and lint on every pull request. On
  `main`: multi-arch Docker image (amd64 + arm64) published to GHCR, then deployment to Cloud Run
  (only once the GCP variables are configured).
- One PR per feature, Conventional Commits (`feat:`, `fix:`, `chore:`…), `main` protected.

### PR validation and merge (standing instruction)

idriss tests every PR in his own browser, on his PC (Windows), before it is merged.
1. When a PR is opened, give idriss the preview command with the PR number, both forms:
   `.\scripts\preview.ps1 <n>` (PowerShell) and `scripts/preview.sh <n>` (bash). It fetches the
   PR, starts PostgreSQL in Docker, runs the API with the `demo` profile (fresh demo data: accounts
   `admin` / `responsable` / `caissier`, password `demo1234`, two tills, eight products) and the
   Vite front, and opens the browser. Say what to check in the browser for that PR.
2. Merge only on idriss's explicit validation. Once he validates, merge right away, without
   waiting for the CI or the tests to finish (every merge on `main` deploys to Cloud Run).
3. Without his validation, never merge. If he reports a problem, fix it on the same branch and
   give him the command again.
4. Keep `scripts/preview.sh`, `scripts/preview.ps1` and the `demo` profile (`ma.invento.pos.demo`)
   working: when a feature needs demo data to be tested, add it to `DonneesDemo`.

## Skills to use

- `invento-design`: every front screen (mockup 1, Invento look).
- `code-review` and `security-review`: before opening each PR.
- `simplify`: after a feature, on the changed code.
- `run`: to start the app and check a change for real (`scripts/preview.sh` does it in one step).

## Commands

- Preview a PR locally (fresh demo data): `scripts/preview.sh <n>` or `.\scripts\preview.ps1 <n>`.
- Local database: `docker compose up -d db`
- Run the API: `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
- Full check: `cd backend && ./mvnw verify` (unit tests, Testcontainers integration tests,
  Spotless, JaCoCo; Docker must be running). Format: `./mvnw spotless:apply`.
- Admin password reset on the server: `docker compose run --rm app --reset-admin`.
