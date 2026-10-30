# 인수인계서: ML 예측 서비스 연동 (BE ↔ KOSPI 모델 API)

> 작성일: 2026-10-30. 대상 독자: 이 작업을 이어받는 백엔드 담당자.
> 이 문서는 "어디까지 됐고, 무엇이 안 되며, 다음 사람이 무엇을 해야 하는지"만 적는다.
> 설계 배경은 [`architecture.md`](architecture.md), 스키마 규칙은
> [`schema-migration.md`](schema-migration.md) 를 따른다.

---

## 0. 한 줄 요약

백엔드가 ML 모델 서비스를 호출하는 배선은 끝났다. 단위 테스트로 검증했다.
다만 모델 artifact 파일이 없다. 그래서 실제 값이 흐르는 것은 확인하지 못했다.

---

## 1. 무엇을 연결했나

ML 레포(`Hongik-graduProject/ML`)의 **`boseong` 브랜치**에는 백엔드 연동용 FastAPI
서비스가 이미 들어 있다. 그 레포의 `BACKEND_HANDOFF.md` §2 가 "백엔드는 Feature 를
직접 만들지 말고 조회 API 를 쓰라"고 규정한다. 이번 작업은 그 규정대로
**저장된 예측 조회 엔드포인트만** 소비한다.

```text
FE (미연결)  →  BE  GET /analysis/{ticker}/prediction
                 └→ ML  GET /api/v1/predictions/{code}   (헤더: X-API-Key)
```

`POST /api/v1/predict` 와 `/predict/batch` 는 **사용하지 않는다.** 노트북과 동일한 31 개
Feature 를 서버에서 만들 수 있게 된 뒤에 쓰라고 ML 쪽이 못박아 두었다.

### 응답 계약

`GET /analysis/{ticker}/prediction` → `ApiResponse<StockPredictionResponse>`

| 필드 | 의미 |
|---|---|
| `available` | false 면 나머지 필드는 전부 null (§4 참조) |
| `ticker` | 요청한 종목코드 |
| `as_of_date` | 모델 예측 기준일 |
| `close` | 기준일 종가 |
| `predicted_excess_return_60d` | 60 거래일 **KOSPI 대비** 초과수익률 |
| `predicted_percentile` | 동일 기준일 예측 대상 내 상대순위 |
| `signal` | Top 10% / Above Average / Neutral / Below Average / Bottom 10% |
| `model_agreement_score` | Ridge·XGBoost 일치도 순위 |
| `scenario_price_if_kospi_flat` | KOSPI 수익률 0% 가정 시 가격 |
| `feature_coverage` | 31 개 Feature 중 유효값 비율 |

**주의 (ML 측 요구사항).** `model_agreement_score` 를 신뢰확률이나 적중확률로 표기하면
안 된다. 절대 주가 상승확률이 아니라 동일 시점 종목 간 상대순위다. 필드명을 그대로
`model_agreement_score` 로 둔 이유가 이것이다. FE 문구 작성 시 반드시 지킬 것.

---

## 2. 변경된 파일 (백엔드 레포만)

### 2-1. 이번 작업 본체

신규 4 개:

| 파일 | 역할 |
|---|---|
| `analysis/client/dto/MlPredictionJson.java` | ML 응답 와이어 DTO. 18 개 필드 중 UI 용 10 개만 매핑 |
| `analysis/client/MlPredictionClient.java` | 호출 + 실패 degrade |
| `analysis/dto/StockPredictionResponse.java` | 우리 API 응답 DTO |
| `src/test/.../analysis/client/MlPredictionClientTest.java` | MockWebServer 단위 테스트 4 개 |

수정 4 개:

| 파일 | 변경 |
|---|---|
| `analysis/api/AnalysisController.java` | `GET /{ticker}/prediction` 추가 (+8 줄) |
| `analysis/application/AnalysisQueryService.java` | `getPrediction()` 추가 (+32 줄) |
| `src/main/resources/application.yml` | `ml.api.base-url`, `ml.api.key` 추가 (+7 줄) |
| `.env.example` | `ML_API_BASE_URL`, `ML_API_KEY` 추가 (+4 줄) |

설계 결정 하나. `MlPredictionClient` 는 **모든 실패를 삼킨다.** base-url 미설정, ML 의
404, 503, 타임아웃이 전부 `available=false` 로 떨어지고 WARN 로그만 남는다. BE 종목
마스터는 DART corpCode.xml 기준 약 4000 종목인데 모델 universe 는 KOSPI200 수준이라
"예측 없음"이 오히려 다수 경우다. 이를 404 에러로 올리면 분석 탭이 대부분의 종목에서
깨진다. Firebase 미설정·Redis 장애를 다루는 기존 코드와 같은 태도다.

`getPrediction()` 은 의도적으로 `@Transactional` 을 붙이지 않았고 DB 도 건드리지 않는다.

### 2-2. 부수 변경 A: 테스트 컴파일 복구 (불가피)

이 작업 **이전부터** 테스트 소스가 컴파일되지 않는 상태였다. 직전 머지
`feat/#15_final` 에서 운영 코드의 생성자 시그니처가 바뀌었는데, 그 생성자를 직접
호출하는 테스트가 갱신되지 않았다. 새 테스트를 한 줄도 돌릴 수 없어 최소 수정했다.

| 파일 | 원인 | 수정 |
|---|---|---|
| `auth/SupabaseJwtVerifierTest.java` | `SupabaseJwtVerifier(String)` → `(String, String)` | url 에 빈 문자열 전달 (kid 없는 토큰 → HS256 경로) |
| `dev/DevTokenControllerTest.java` | 위와 동일 | 위와 동일 |
| `stocks/client/KrxClientTest.java` | `KrxClient` 생성자에 `NaverQuoteClient` 추가됨 | 목 객체 주입. `emptyBaseUrl` 테스트는 "네이버 폴백" 으로 의미 갱신 |

### 2-3. 부수 변경 B: spotless 포맷팅 16 개 파일

`gradlew spotlessApply` 를 돌리면서 `feat/#15` 가 포맷 없이 커밋한 운영 파일 16 개가
google-java-format 으로 재정렬됐다. **전부 줄바꿈·주석 래핑만 바뀌었고 의미 변경은
없다** (예: `KrxClient.java` 는 빈 줄 하나 추가가 전부).

`build.gradle` 의 `build` 태스크가 `spotlessCheck` 에 의존하므로, 이 포맷팅이 없으면
`gradlew build` 자체가 실패한다. 되돌리면 빌드가 다시 깨진다.

대상 16 개:

- `SupabaseJwtVerifier`
- `CorsConfig`
- `DartCompanyClient`
- `DartCorpCodeClient`
- `DartFinanceClient`
- `FavoriteAddRequest`
- `CorpCodeBootstrap`
- `FinancialStatementFetchService`
- `SearchHistoryService`
- `StockQueryService`
- `KrxClient`
- `NaverFinanceMetricsClient`
- `NaverQuoteClient`
- `FinancialStatement`
- `SearchHistory`
- `StockDailyPriceItem`

### 2-4. 커밋 전 판단 필요 (이 작업과 무관, 원래 그 상태였음)

- `gradlew`: 수정됨 상태로 방치되어 있다.
- `.gitattributes`: 추적되지 않은 채로 있다.

둘 다 작업 시작 전부터 그랬다. 이번 변경과 섞어 커밋할지 따로 뺄지 정할 것.

---

## 3. 설정 방법

### 3-1. ML 서비스 기동 (ML 레포 `boseong` 브랜치)

```bash
git checkout boseong
# artifacts/ 에 Colab V2.4 산출물 4 개를 넣는다 (§4-1 참조)
python scripts/validate_artifacts.py     # 성공해야 한다
make run                                 # uvicorn, 포트 8000
curl http://localhost:8000/health/ready  # 200 이어야 한다
```

서비스 환경변수는 `KOSPI_API_` 접두사를 쓴다. API 키는 `KOSPI_API_API_KEY`.
`KOSPI_API_ALLOWED_ORIGINS` 는 BE 가 서버 사이드에서 호출하므로 신경쓰지 않아도 된다.

### 3-2. 백엔드 설정

`.env` 에 두 줄 추가. ML 서비스의 `KOSPI_API_API_KEY` 와 **같은 값**을 넣는다.

```properties
ML_API_BASE_URL=http://localhost:8000
ML_API_KEY=<KOSPI_API_API_KEY 와 동일한 값>
```

`ML_API_BASE_URL` 을 비워 두면 호출 자체를 하지 않고 항상 `available=false` 를 준다.
ML 서비스 없이 BE 를 띄울 때는 비워 두면 된다.

### 3-3. 확인

```bash
curl -H "Authorization: Bearer <JWT>" \
  http://localhost:8080/analysis/005930/prediction
```

---

## 4. 안 된 것 / 막힌 것

### 4-1. [블로커] 모델 artifact 4 개가 없다

ML FastAPI 가 뜨려면 `artifacts/` 에 Colab V2.4 실행 산출물이 있어야 한다.

1. `ensemble_pipeline_v24.joblib`
2. `latest_kospi_predictions_v24.csv`
3. `feature_schema_v24.json`
4. `data_manifest_v24.json`
5. `runtime_versions_v24.json` (선택이나 배포 시 권장)

이 파일들은 레포 어디에도 없고 로컬에도 없다. Colab 에서
`scripts/export_api_artifacts.py` 를 돌리면 `kospi_api_artifacts_v24.zip` 으로 생성된다.
**Colab 을 돌릴 수 있는 사람이 뽑아서 전달해야 한다.**

없는 동안의 동작: ML 이 503 → BE 는 `available=false`. 화면은 깨지지 않는다.

그래서 **실제 ML 응답으로 end-to-end 검증을 하지 못했다.** 실제 payload 모양이
단위 테스트의 목 데이터와 다르면 조용히 `available=false` 로 떨어진다. 그때는
`MlPredictionClient` 의 WARN 로그에 원인이 찍힌다.

`joblib` 은 pickle 기반이므로 신뢰할 수 있는 본인 산출물만 로드할 것.

### 4-2. [기존 결함] `KrxClientTest` 2 건 실패: 이번 작업과 무관

실패 항목:

- 정상 응답 시 KrxQuote 를 올바르게 파싱한다
- 429 응답 후 재시도하여 성공하면 올바른 결과를 반환한다

원인은 생성자가 아니라 운영 코드다. `KrxClient.getQuote()` 가 URI 스킴을 `https` 로
하드코딩하고, 조회 전에 KIS 액세스 토큰 발급을 먼저 호출한다. 그런데 테스트는 평문
HTTP MockWebServer 를 쓰고 토큰 응답을 큐에 넣지 않는다. 맞을 수가 없다.

제대로 고치려면 스킴을 설정 가능하게 바꾸는 **운영 코드 변경**이 필요하다. ML 연동과
무관한 별개 결함이라 손대지 않았다. 별도 이슈로 처리할 것.

### 4-3. 전체 테스트 스위트 미실행

통합·E2E 테스트는 Testcontainers 로 Postgres 를 띄우므로 Docker 가 필요하다.
이번에 돌린 것은 4 개 클래스 15 개 테스트뿐이다.

### 4-4. FE 미연결

Flutter 앱은 한 줄도 건드리지 않았다. 새 엔드포인트를 호출하는 곳이 없다.
붙일 자리는 `lib/screens/company/analysis_tab.dart` (W-15 분석 탭) 이고,
`lib/services/api_service.dart` 에 메서드를 추가하면 된다.

### 4-5. ML 테마 분석 → ThemeSense 슬롯은 범위 밖

ML 레포 README 가 말하는 "매크로 유전자" 8 개 테마 분류를 BE 의 ThemeSense stage1 /
stage2 자리에 꽂는 것은 **포맷 변환이 아니라 모델링 작업**이다.

BE 의 stage2 는 테마 × 재무지표 β 를 기대하고, `MetricDeltaCalculator` 가 Δ 를 계산할 수
있는 지표는 `financial_metric_catalog` 의 15 개뿐이다. 반면 ML 의 테마 가중치는
금리·환율·유가·금에 대한 민감도, 즉 매크로 축이다. 매크로 β 를 그대로 적재하면
`MetricDeltaCalculator` 가 코드를 인식하지 못해 Δ 를 0 으로 떨구고, 모든 기여도와
`expected_car` 가 0 이 된다. **조용히 망가지므로 더 위험하다.**

진행하려면 둘 중 하나를 택해야 한다.

1. 테마별 β 를 재무지표 축 위에서 다시 적합한다.
2. 카탈로그와 `MetricDeltaCalculator` 에 매크로 지표를 추가하고, 그 Δ 를
   `FinancialStatement` 가 아닌 별도 소스에서 가져온다.

---

## 5. 검증된 것 (증거)

실행한 명령:

```bash
./gradlew spotlessApply test \
  --tests "*MlPredictionClientTest*" \
  --tests "*KrxClientTest*" \
  --tests "*DevTokenControllerTest*" \
  --tests "*SupabaseJwtVerifierTest*"
```

| 클래스 | 결과 |
|---|---|
| `MlPredictionClientTest` | 4 / 4 통과 |
| `SupabaseJwtVerifierTest` | 4 / 4 통과 |
| `DevTokenControllerTest` | 2 / 2 통과 |
| `KrxClientTest` | 3 / 5 통과 (§4-2) |

메인 소스·테스트 소스 모두 컴파일된다.

`MlPredictionClientTest` 가 고정하는 것:

1. ML 의 **18 개 필드 전체 payload** 를 먹여도 디코딩이 깨지지 않는다
   (우리가 매핑하지 않는 8 개 필드 무시 보장).
2. 요청 경로가 `/api/v1/predictions/005930` 이고 `X-API-Key` 헤더가 붙는다.
3. 404 (예측 없음) → 빈 결과.
4. 503 (artifact 미로드) → 예외 없이 빈 결과.
5. base-url 미설정 → 호출 0 회, 빈 결과.

---

## 6. 다음 사람 체크리스트

1. Colab 담당자에게 artifact 4 개를 요청한다 (§4-1).
2. ML `boseong` 브랜치를 띄우고 `/health/ready` 가 200 인지 본다 (§3-1).
3. BE `.env` 에 두 줄을 넣고 `curl` 로 실제 값이 오는지 확인한다 (§3-2, §3-3).
4. 실제 payload 가 §1 계약과 맞는지 확인한다. 다르면
   `MlPredictionJson` 의 `@JsonProperty` 이름을 맞춘다.
5. FE 분석 탭에 카드를 붙인다 (§4-4). 문구에서 `model_agreement_score` 를
   확률로 쓰지 않는다 (§1 주의).
6. `KrxClientTest` 2 건은 별도 이슈로 돌린다 (§4-2).
7. Docker 를 켜고 전체 스위트를 한 번 돌린다 (§4-3).

---

## 7. 레포별 변경 범위

| 레포 | 변경 | 푸시 |
|---|---|---|
| `BE` | 본 문서 §2 | **필요** |
| `ML` | 없음 (0 건). `git show` 로 읽기만 했다 | 불필요 |
| `FE` | 추적되는 변경 없음 | 불필요 |

FE 에 `.omc/` 디렉토리가 추적되지 않은 채 남아 있다. 에이전트 툴의 상태 파일이므로
커밋하면 안 된다. FE `.gitignore` 에 `.omc/` 를 추가해 두는 것을 권한다.
