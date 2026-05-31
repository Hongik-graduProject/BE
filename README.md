# hongik-grad Backend

Spring Boot 3 backend for the disclosure-driven theme analysis mobile app. A DART disclosure poll
auto-generates a ThemeSense analysis report and fans it out to every user who favorited the ticker
(in-app notification + FCM push).

- **Stack**: Java 21 (virtual threads), Spring Boot 3.3, Gradle (Groovy DSL), Postgres 15
  (Supabase), Redis 7, Flyway, Firebase Admin (FCM)
- **Group/Artifact/Version**: `com.hongik.grad` / `hongik-grad` / `1.0.0`
- **Implementation plan**: [`../docs/implPlan.md`](../docs/implPlan.md)
- **Architecture diagrams**: [`docs/architecture.md`](docs/architecture.md)
- **Schema / migration notes**: [`docs/schema-migration.md`](docs/schema-migration.md)
- **Changelog**: [`CHANGELOG.md`](CHANGELOG.md)

## 5-minute quick start

```bash
# 1. Clone and switch to BE/
git clone <repo-url>
cd hongik_grad/BE

# 2. Configure environment
cp .env.example .env
# Edit .env with your Supabase / DART / KRX / Firebase credentials (table below).

# 3. Run the stack (Postgres + Redis + backend)
docker-compose up --build

# 4. Verify
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

### Run without Docker (local Postgres + Redis already running)

```bash
./gradlew bootRun
```

## Environment variables

Synced with [`.env.example`](.env.example). Supabase issues the JWTs the backend verifies — the
backend never mints tokens.

| Variable | Required | Description |
|---|---|---|
| `SERVER_PORT` | no (8080) | HTTP port |
| `SPRING_PROFILES_ACTIVE` | yes | `local` / `prod` |
| `SUPABASE_URL` | yes | Supabase project URL |
| `SUPABASE_ANON_KEY` | yes | Public anon key (client) |
| `SUPABASE_SERVICE_ROLE_KEY` | yes | Service role key — server only, never ship to client |
| `SUPABASE_JWT_SECRET` | yes | HS256 secret used to verify Supabase JWTs |
| `DB_URL` | yes | `jdbc:postgresql://<host>.supabase.co:5432/postgres` |
| `DB_USERNAME` | yes | DB user (`postgres`) |
| `DB_PASSWORD` | yes | DB password |
| `REDIS_HOST` / `REDIS_PORT` | yes | Redis for KRX quote cache |
| `DART_API_KEY` | yes | OpenDART key — https://opendart.fss.or.kr |
| `KRX_API_BASE_URL` | yes | KIS/KRX OpenAPI base URL |
| `KRX_API_KEY` / `KRX_API_SECRET` | yes | KIS/KRX credentials |
| `FIREBASE_CREDENTIALS_PATH` | yes (push) | Path to Firebase admin service account JSON |
| `FIREBASE_PROJECT_ID` | yes (push) | GCP project id |
| `THEMESENSE_ARTIFACTS_DIR` | yes (ETL) | Directory of ThemeSense model artifacts |
| `THEMESENSE_STAGE1_VERSION` | yes (ETL) | Stage-1 model version tag |
| `THEMESENSE_STAGE2_VERSION` | yes (ETL) | Stage-2 model version tag |

Without `FIREBASE_*` the report fan-out still writes in-app notifications (push send is skipped with
a WARN). Without `DART_API_KEY` the poller returns an empty list (no crash).

The `local` / `prod` profiles above are the only ones you set. The `test` / `test-db` profiles
activate automatically during test runs — you never set them by hand.

## Tests

```bash
./gradlew test          # full suite (unit + integration + E2E)
./gradlew build         # compile + spotlessCheck + test
```

**Docker is required.** Integration and E2E tests (`*IntegrationTest`, `e2e/*E2ETest`) spin up a
Postgres 15 container via Testcontainers and run real Flyway migrations against it. Without a
running Docker daemon those tests fail to start. The two PR-10 golden-path E2E suites are:

- `e2e/SignupGoldenPathE2ETest` — signup JWT → `POST /users` → `GET /folders`.
- `e2e/AnalysisGoldenPathE2ETest` — stubbed DART filing → `DartPoller.poll()` → report → FCM +
  notification → `GET /analysis/reports/{id}`.

### Quality gates

```bash
./gradlew spotlessApply      # format
./gradlew spotlessCheck      # verify formatting
```

## Test the API in Swagger UI (local only)

The backend never mints real tokens, but on the `local` profile a dev-only endpoint mints a
Supabase-shaped JWT so you can drive every protected endpoint from Swagger UI. This endpoint
(`POST /dev/auth/token`) is gated by `@Profile("local")` and does not exist in prod (404).

1. Start the stack on the `local` profile (default):

   ```bash
   docker-compose up --build        # or: ./gradlew bootRun
   ```

2. Open Swagger UI: <http://localhost:8080/swagger-ui.html>.

3. Mint a dev token. In Swagger expand **dev-token-controller → POST /dev/auth/token** and
   *Execute* (body is optional — omit it for a random `sub`), or via curl:

   ```bash
   curl -s -X POST http://localhost:8080/dev/auth/token \
     -H "Content-Type: application/json" -d '{"email":"hongik@local.test"}'
   # { "status":"success", "data": { "access_token":"<JWT>", "token_type":"Bearer",
   #   "sub":"<uuid>", "expires_in":3600 } }
   ```

   Copy `data.access_token`.

4. Click **Authorize** (top right). The scheme is HTTP `bearer` (`bearerAuth`), so paste just the
   raw token — Swagger adds the `Authorization: Bearer <token>` header to every request. Confirm and
   close.

5. Now call any endpoint. Golden path: `POST /users` (onboarding, creates the default folder) →
   `GET /folders` → `GET /notifications`.

The token lives 1 hour; mint a new one when calls start returning 401. The dev token endpoint is
**local only** — never available in prod.

## Golden path (curl)

Replace `<JWT>` with a Supabase access token (the `Authorization` header is `Bearer <JWT>`).

```bash
BASE=http://localhost:8080
JWT=<supabase-access-token>

# 1. Onboarding — creates the profile + default folder (idempotent).
curl -s -X POST "$BASE/users" \
  -H "Authorization: Bearer $JWT" -H "Content-Type: application/json" \
  -d '{"nickname":"hongik"}'

# 2. List folders — exactly one DEFAULT folder with item_count 0 for a new user.
curl -s "$BASE/folders" -H "Authorization: Bearer $JWT"

# 3. Read a generated analysis report — flips is_read to true.
curl -s "$BASE/analysis/reports/1" -H "Authorization: Bearer $JWT"

# 4. List notifications — includes unread_count.
curl -s "$BASE/notifications" -H "Authorization: Bearer $JWT"
```

## Production deployment (student-friendly)

The app is a single stateless JAR — any container/PaaS host works. Build it with:

```bash
./gradlew bootJar           # → build/libs/hongik-grad-1.0.0.jar
java -jar build/libs/hongik-grad-1.0.0.jar
```

**Render / Fly.io / Railway** (free or low-cost tiers):

1. Provision managed Postgres (or point `DB_*` at Supabase) and a managed Redis instance.
2. Set every environment variable from the table above as platform secrets.
3. Deploy from the `Dockerfile` (or buildpack) and set the health check to `GET /actuator/health`.
4. Upload the Firebase service-account JSON as a secret file and point `FIREBASE_CREDENTIALS_PATH`
   at it; omit it for push-disabled environments.
5. Flyway runs migrations automatically on boot — no manual schema step.

## Conventions

No Lombok. DTOs are records. SLF4J only — no `System.out` or `printStackTrace`.

## License

Proprietary — Hongik University capstone project.
