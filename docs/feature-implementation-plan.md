# 구현 계획: 레이더 스코프 · 반박 모드 · 타임머신 (백엔드)

> 작성일: 2026-11-12. 기능 정의는 [`feature-proposals.md`](feature-proposals.md) 를 따른다.
> 이 문서는 구현 에이전트 사이의 계약서다. 파일 소유권과 공유 인터페이스를 여기서 고정한다.
> 범위는 백엔드와 그 뒷단(데이터 적재)이다. FE 는 범위 밖이다.

---

## 0. 현재 상태에서 바뀐 전제

`feature-proposals.md` 작성 이후 저장소가 바뀌었다. 아래를 기준으로 삼는다.

1. 마이그레이션이 V12 까지 있다. 새 번호는 **V13, V14** 다.
2. `scripts/generate_demo_themesense.py` 가 생겼다. KSIC 업종 규칙으로 **테마 17 개**를
   만들고, 종목마다 1 ~ 3 순위 테마와 확률을 준다. 레이더 각도축은 8 개가 아니라
   17 개를 기준으로 한다.
3. `stocks.market_cap` 이 생겼다 (V12).
4. `financial_statements` 는 기간마다 `sj_div` (CIS, BS, CF) 별로 최대 3 행이다.

## 1. 새로 발견한 기존 결함

`MetricDeltaCalculator.computeForTicker` 는 `findByStockTickerOrderByPeriodDesc` 결과의
앞 두 행을 최신·직전으로 쓴다. 같은 기간의 CIS 행과 BS 행이 비교될 수 있다.
그러면 손익 항목이 한쪽에 없어 Δ 가 비고 기여도가 0 이 된다.
세 기능 모두 `expected_car` 를 화면에 쓰므로 이번에 함께 고친다 (§3, 타임머신 소유).

## 2. 실행 순서와 파일 소유권

두 파일이 동시에 같은 파일을 쓰지 않도록 소유자를 하나로 정한다.
`application.yml` 은 아무도 수정하지 않는다. 설정은 `@Value` 기본값으로 둔다.

| 단계 | 에이전트 | 소유 파일 |
|---|---|---|
| 1 (병렬) | 타임머신 | `V13`, `V14`, `timemachine/**`, `DartFinanceClient`, `FinancialStatement`, `FinancialStatementRepository`, `FinancialStatementFetchService`, `MetricDeltaCalculator`, `AnalysisReportGenerator`, `NaverDailyPriceClient`, 해당 테스트 |
| 1 (병렬) | 레이더 | `radar/**`, `SecurityConfig`, 해당 테스트 |
| 2 | 반박 모드 | `critique/**`, 해당 테스트 |

반박 모드는 타임머신의 전적 집계(§3-5)를 쓰므로 2 단계로 돌린다.

---

## 3. 타임머신

### 3-1. 스키마

```sql
-- V13__add_daily_prices.sql
CREATE TABLE daily_prices (
    ticker      VARCHAR(10) NOT NULL,
    trade_date  DATE        NOT NULL,
    close       BIGINT      NOT NULL,
    CONSTRAINT pk_daily_prices PRIMARY KEY (ticker, trade_date)
);
```

`stocks` 로 FK 를 걸지 않는다. 이유는 두 가지다.

- KOSPI 지수를 `ticker = 'KOSPI'` 로 같은 테이블에 담는다.
- 상장폐지 종목의 과거 시세도 남아야 생존 편향을 줄일 수 있다.

```sql
-- V14__add_financial_statement_disclosed_at.sql
ALTER TABLE financial_statements ADD COLUMN disclosed_at DATE NULL;
```

V11 이 `financial_statements` 를 비웠으므로 기존 행 백필은 필요 없다.

### 3-2. look-ahead bias 차단

1. `DartFinanceClient.FinanceRow` 에 `rceptNo` 를 추가한다. DART 응답의 `rcept_no` 다.
2. `FinancialStatementFetchService` 가 `rcept_no` 앞 8 자리를 `disclosed_at` 으로 저장한다.
3. `MetricDeltaCalculator` 에 `computeForTicker(String ticker, LocalDate asOf)` 를 둔다.
   `disclosed_at IS NOT NULL AND disclosed_at <= asOf` 인 행만 쓴다.
4. `AnalysisReportGenerator` 는 `disclosure.getFiledAt()` 의 날짜(KST)를 `asOf` 로 넘긴다.

§1 의 결함도 여기서 고친다. 기간 단위로 묶은 뒤 손익 지표는 CIS, 재무상태 지표는
BS 행에서 읽는다. 실제 행 구조는 구현 전에 엔티티와 적재 코드로 확인한다.

### 3-3. 시세 적재

- `NaverDailyPriceClient` 에 KOSPI 지수 조회를 추가한다.
  후보 경로는 `https://m.stock.naver.com/api/index/KOSPI/price` 다. 검증되지 않았다.
  응답 형식이 다르면 빈 목록을 돌려주고 경고 로그를 남긴다.
- `PriceBackfillService.ensure(ticker, from)` 는 `from` 이후 거래일이 부족할 때만
  최근 N 거래일을 받아 `ON CONFLICT DO NOTHING` 으로 넣는다.
  N 은 `from` 부터 오늘까지 + 5, 상한 1500.
- 테스트에서 실제 네트워크를 부르지 않는다.

### 3-4. 판정 규칙

- 기준일: 공시 `filed_at` (KST) 이하의 마지막 거래일 종가.
- 종료일: 기준일 이후 60 번째 거래일 종가.
- `excess_return_pct = (종목 종료/기준 - 1 - (KOSPI 종료/기준 - 1)) × 100`
- 상태
  - `PENDING`: 60 거래일이 아직 안 지남. `days_remaining` 을 채운다.
  - `NEUTRAL`: `|expected_car| < 0.5`
  - `INSUFFICIENT_DATA`: 시세가 비었거나 중간에 거래정지
  - `HIT`, `MISS`: 부호 일치 여부

### 3-5. 공유 계약 (반박 모드가 쓴다)

```java
package com.hongik.grad.timemachine.dto;
public record TrackRecord(
    int total, int judged, int hits, Double hitRate,
    int neutral, int pending, int insufficient) {}
```

```java
// com.hongik.grad.timemachine.application.TrackRecordService
public TrackRecord forTicker(String ticker, DisclosureType typeOrNull)
```

`hitRate` 는 `judged < 10` 이면 null 이다.

### 3-6. API

```text
GET /analysis/reports/{report_id}/replay
GET /analysis/{ticker}/track-record?type=quarterly
```

컨트롤러는 `timemachine/api` 에 새로 만든다. `AnalysisController` 는 건드리지 않는다.

---

## 4. 레이더 스코프

### 4-1. API

```text
GET /radar/points?from=...&to=...   기본: 최근 30 분, 최대 24 시간, 최대 500 점
GET /radar/stream                   SSE
```

`points` 하나로 진입 스냅샷과 과거 하루 리플레이를 모두 처리한다.

### 4-2. 점 계산

- `theta = theme.stage1_class_idx × 360 / 테마 수`. 테마가 없으면 ticker 해시로 각도를 정한다.
- `radius`: 내 종목 0.15, 같은 primary 테마 `0.65 - 0.30 × probability`, 나머지 0.85.
- 내 종목: `FavoriteRepository.findByFolderUserId`
- 테마: `StockThemeRepository.findByStockTickerInAndIsPrimaryTrue`
- 크기·색: `analysis_reports.expected_car`. 리포트가 없으면 null.

### 4-3. SSE

- 사용자별 `SseEmitter` 레지스트리. 타임아웃은 `@Value("${radar.sse-timeout:PT30M}")`.
- `DisclosureCreatedEvent` 와 `ReportCreatedEvent` 에 `AFTER_COMMIT` + `@Async` 리스너를 붙인다.
  공시가 오면 회색 점, 리포트가 오면 같은 `disclosure_id` 로 갱신 이벤트를 보낸다.
- 20 초마다 하트비트 주석을 보낸다.
- Spring Security 6 은 ASYNC 디스패치에도 인가를 건다. context7 로 확인하고
  `SecurityConfig` 에 필요한 설정을 넣는다.

---

## 5. 반박 모드

```text
GET /analysis/reports/{report_id}/critique
```

| 코드 | 근거 | 출처 |
|---|---|---|
| `LOW_R_SQUARED` | 사용한 β 행 중 최소 R² < 0.3 | `theme_metric_sensitivity` |
| `SMALL_SAMPLE` | 최소 표본 < 100 | `theme_metric_sensitivity` |
| `MISSING_DELTA` | 기여도 상위 5 개 중 Δ = 0 이 2 개 이상 | `metric_contributions` |
| `LOW_FEATURE_COVERAGE` | ML `feature_coverage` < 0.9. ML 이 없으면 미확인 항목 | `MlPredictionClient` |
| `PAST_MISSES` | 같은 종목·같은 공시 유형 적중률 < 0.6 | `TrackRecordService` |
| `STRUCTURAL_*` | 항상 표시: z-score 없음, CAR 밴드 자리표시자, 1 순위 테마만 사용 | 고정 |

점수와 등급은 `feature-proposals.md` §2-3 을 따른다. LLM 을 쓰지 않는다.

---

## 6. 뒷단(ML·데이터) 작업

ML 서비스 코드는 바꾸지 않는다. 필요한 값은 이미 응답에 있다.
대신 아래 운영 절차가 선행되어야 실제 화면이 채워진다.

1. `DART_API_KEY` 를 넣고 부팅해 `stocks` 를 채운다.
2. `generate_demo_themesense.py` 로 산출물을 만들고 `--etl-themesense` 로 적재한다.
3. 종목 상세를 한 번씩 열어 `financial_statements` 를 채운다 (`disclosed_at` 포함).

## 7. 공통 규칙

- 계층은 기존과 같다: `api` / `application` / `domain` / `dto`.
- DTO 는 record, `@JsonProperty` 로 snake_case 를 명시한다.
- 응답은 `ApiResponse` 로 감싼다. 오류는 `DomainException` + `ErrorCode`.
- Lombok 금지, `System.out` 금지.
- 테스트는 단위 테스트와 `@WebMvcTest` 슬라이스로 쓴다. Testcontainers 테스트는
  이 환경에서 Docker 가 없어 돌릴 수 없다.
- 끝내기 전에 `./gradlew spotlessApply` 와 담당 테스트를 돌린다.
