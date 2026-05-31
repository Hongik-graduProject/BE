# Changelog

All notable changes to the hongik-grad backend are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); this project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-05-31

First production release. Tag: `v1.0.0` (manual — repository is tagged by hand).

Delivered across PR-01 → PR-10. v1.0 ships the full disclosure → analysis → notification pipeline
with a Testcontainers golden-path regression net.

### Added

- **PR-01 Project Skeleton** — Spring Boot 3.3 / Java 21 / Gradle skeleton; `/actuator/health`.
- **PR-02 DB Migration** — Flyway V1 schema, V2 metric catalog seed, V3 stock universe seed.
- **PR-03 Auth + Onboarding** — Supabase HS256 JWT verification
  (`SupabaseJwtAuthenticationFilter`), idempotent `POST /users` creating one `Profile` + DEFAULT
  `Folder`.
- **PR-04 Folders + Favorites** — folder CRUD/reorder and favorite management endpoints.
- **PR-05 Stocks + KRX Cache** — stock master and Redis-cached KRX quote service.
- **PR-06 DART Poller** — `DartClient` + `DartPoller` ingest of OpenDART filings (universe-filtered,
  deduped on `dart_filing_id`), emitting `DisclosureCreatedEvent`.
- **PR-07 ThemeSense ETL** — CLI artifacts loader (`ThemeSenseEtlRunner` / `ThemeIngestService`)
  loading theme catalog, Stage-1 predictions, and Stage-2 coefficients.
- **PR-08 Analysis Endpoints** — `AnalysisReportGenerator` produces a report per disclosure via
  AFTER_COMMIT event chain; `GET /analysis/{ticker}/themes|reports` and
  `GET /analysis/reports/{report_id}` (with `ReportReadStatus` UPSERT).
- **PR-09 Notifications + FCM** — `ReportCreatedListener` fan-out to favoriting users
  (`Notification` INSERT + `FcmSender` push); `GET /notifications` with `unread_count`; FCM-token
  UPSERT endpoints. Push gracefully degrades to in-app-only when Firebase is unconfigured.
- **PR-10 Integration + E2E + Release** — two Testcontainers golden-path E2E suites
  (`SignupGoldenPathE2ETest`, `AnalysisGoldenPathE2ETest` — the latter drives the real DART HTTP
  boundary through to report read), architecture diagrams (`docs/architecture.md`), finalized
  `README.md`, and this changelog.

### Changed

- Version bumped from `0.1.0-SNAPSHOT` to `1.0.0`.

### Notes

- All persisted, polled, and pushed work is driven by existing endpoints/services; PR-10 adds no new
  business logic — only regression tests, documentation, and the version bump.
- Tag `v1.0.0` is applied manually outside this checkout.

<!-- TODO: set the real release URL (org/repo unknown until tagging) before release.
     e.g. [1.0.0]: https://github.com/<org>/<repo>/releases/tag/v1.0.0 -->
