# hongik-grad Backend

Spring Boot 3 backend for the disclosure-driven theme analysis mobile app.

- **Stack**: Java 21 (virtual threads), Spring Boot 3.3, Gradle (Groovy DSL), Postgres 15 (Supabase), Redis 7, Flyway 10
- **Group/Artifact**: `com.hongik.grad` / `hongik-grad` / `0.1.0-SNAPSHOT`
- **Implementation plan**: see [`../docs/implPlan.md`](../docs/implPlan.md)

This is the **PR-01 skeleton**. It builds, boots, and exposes `/actuator/health`. Auth, DB
migrations, and domain logic land in subsequent PRs.

## 5-minute quick start

```bash
# 1. Clone and switch to BE/
git clone <repo-url>
cd hongik_grad/BE

# 2. Configure environment
cp .env.example .env
# Edit .env with your Supabase / DART / KRX / Firebase credentials.

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

### Quality gates

```bash
./gradlew spotlessApply      # format
./gradlew spotlessCheck      # verify formatting
./gradlew build              # compile + test + spotlessCheck
```

## Conventions

No Lombok. DTOs are records. SLF4J only — no `System.out` or `printStackTrace`.

## License

Proprietary — Hongik University capstone project.
