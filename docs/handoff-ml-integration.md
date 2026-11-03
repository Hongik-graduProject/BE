# 인수인계서: ML 예측 서비스 연동 + Supabase 제거

> 작성일: 2026-10-30. 대상 독자: 이 작업을 이어받는 백엔드 담당자.
> 이 문서는 "어디까지 됐고, 무엇이 안 되며, 다음 사람이 무엇을 해야 하는지"만 적는다.
> 설계 배경은 [`architecture.md`](architecture.md), 스키마 규칙은
> [`schema-migration.md`](schema-migration.md) 를 따른다.

---

## 0. 한 줄 요약

이 세션에서 두 가지를 했다.

첫째, 백엔드가 ML 모델 서비스를 호출하는 배선을 끝냈다. 단위 테스트로 검증했다.
다만 모델 artifact 파일이 없다. 그래서 실제 값이 흐르는 것은 확인하지 못했다.
자세한 내용은 §1 부터 §6 까지다.

둘째, Supabase 의존성을 전부 걷어내고 로컬 Postgres 기반 자체 인증으로 옮겼다.
단위 테스트 91 개가 통과한다. 자세한 내용은 §8 이다.

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
# 또는 KOSPI_API_ARTIFACT_DIR 로 산출물 폴더를 직접 가리킨다
make validate                            # 성공해야 한다
make run                                 # uvicorn, 포트 8000
curl http://localhost:8000/health/ready  # 200 이어야 한다
```

`python scripts/validate_artifacts.py` 를 그대로 실행하면 `ModuleNotFoundError: app`
으로 실패한다. Python 은 스크립트 폴더만 import 경로에 넣기 때문이다.
`make validate` 또는 `PYTHONPATH=. python scripts/validate_artifacts.py` 를 쓴다.

Python 은 3.13 을 쓴다. 모델은 scikit-learn 1.6.1 로 저장되었으므로
`pip install -r requirements-dev.txt "scikit-learn==1.6.1"` 로 버전을 맞춘다.
macOS 에서는 XGBoost 가 OpenMP 를 요구하므로 먼저 `brew install libomp` 를 한다.

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

### 3-4. 인수인계 시점의 로컬 `.env` 전문

작업에 쓰던 로컬 `.env` 의 실제 내용이다. `.env` 는 `.gitignore` 1 행이 막고 있어
저장소에 올라가지 않는다. 아래 값은 전부 docker-compose 로컬 기본값이고
실제 자격증명은 하나도 들어 있지 않다. 그대로 복사해서 쓰면 된다.

```properties
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=local
DB_URL=jdbc:postgresql://localhost:5432/postgres
DB_USERNAME=postgres
DB_PASSWORD=postgres
REDIS_HOST=localhost
REDIS_PORT=6379
DART_API_KEY=
DART_POLLER_ENABLED=false
KRX_API_BASE_URL=
KRX_API_KEY=
KRX_API_SECRET=
FIREBASE_CREDENTIALS_PATH=
FIREBASE_PROJECT_ID=
ML_API_BASE_URL=
ML_API_KEY=
```

`JWT_SECRET` 은 일부러 넣지 않았다. 비워 두면 `application-local.yml` 의 로컬 전용
기본값을 쓰므로 로컬에서는 설정이 필요 없다. 운영에서는 반드시 32 자 이상 랜덤
값을 넣어야 한다 (§8-4).

`SUPABASE_*` 키는 전부 사라졌다 (§8).

### 3-5. 비어 있는 키가 무엇을 끄고 있나

위 `.env` 는 외부 연동이 거의 다 꺼진 상태다. 각 빈 키의 영향은 다음과 같다.

| 빈 키 | 꺼지는 기능 |
|---|---|
| `DART_API_KEY` | `DartClient` 가 빈 목록을 반환한다. 공시 수집이 0 건이므로 보고서도 알림도 생성되지 않는다. 게다가 `CorpCodeBootstrap` 이 종목 마스터를 못 채우므로 `stocks` 테이블이 빈 채로 남는다 |
| `DART_POLLER_ENABLED=false` | 60 초 주기 폴링 자체를 끈다. 위 항목과 중복으로 꺼져 있다 |
| `KRX_API_BASE_URL`, `KRX_API_KEY`, `KRX_API_SECRET` | KIS 시세를 쓰지 않고 `NaverQuoteClient` 폴백으로 간다. 시세는 나온다 |
| `FIREBASE_CREDENTIALS_PATH`, `FIREBASE_PROJECT_ID` | FCM 푸시만 생략된다. 인앱 알림은 정상 기록된다 |
| `ML_API_BASE_URL`, `ML_API_KEY` | 이번 작업의 예측 엔드포인트가 항상 `available=false` 를 준다 |

인증은 이제 외부 키가 필요 없다. `POST /auth/signup` 과 `POST /auth/login` 이 로컬
`profiles` 테이블만으로 동작한다 (§8).

가장 먼저 채워야 할 키는 `DART_API_KEY` 다.
V7 마이그레이션이 V3 의 placeholder 종목을 지웠다.
그 자리는 부팅 시 `CorpCodeBootstrap` 이 DART `corpCode.xml` 로 채운다.
키가 없으면 `stocks` 가 빈 채로 남는다.
그러면 검색도 즐겨찾기도 동작하지 않는다.
키는 <https://opendart.fss.or.kr> 에서 무료로 발급한다.

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

없는 동안의 동작: BE 는 `available=false` 를 준다. 화면은 깨지지 않는다.
(2026-11-01 정정) 이때 ML 의 `/api/v1/predictions/{code}` 는 503 이 아니라 500 을
준다. 빈 DataFrame 에서 `Code` 컬럼을 찾다가 KeyError 가 나기 때문이다. 503 을
주는 것은 `/health/ready` 와 `/model/info` 뿐이다. BE 는 모든 예외를 잡으므로
결과는 같지만, WARN 로그에는 500 이 찍힌다.

(2026-11-01 정정) 예측 조회에 실제로 필요한 파일은
`latest_kospi_predictions_v24.csv` 하나다. `ArtifactStore.load()` 가 CSV 를 먼저
읽으므로 joblib 이 없어도 조회는 200 을 준다. 그 경우 `/health/ready` 만 503 이다.

**(2026-11-01) 이 블로커는 해소되었다. §9 참조.**

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

**(2026-11-01) 고쳤다. §9 참조.**

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

---

## 8. Supabase 제거와 자체 인증 전환

### 8-1. 왜 바꿨나

Supabase 는 DB 가 아니라 인증에만 걸려 있었다. 로컬 `.env` 의 `DB_URL` 은 이미
`localhost:5432` 였고 `docker-compose.yml` 이 `postgres:15-alpine` 을 띄운다.
남은 의존성은 GoTrue 프록시와 JWKS 토큰 검증뿐이었고, 둘 다 외부 계정이 있어야
동작했다. 토이 프로젝트에서 외부 계정 없이 띄울 수 있도록 자체 인증으로 옮겼다.

### 8-2. 바뀐 구조

| 항목 | 전 | 후 |
|---|---|---|
| 회원가입·로그인 | Supabase GoTrue 프록시 | 로컬 `profiles` 테이블 |
| 비밀번호 | Supabase 보관 | BCrypt 해시로 `profiles.password_hash` |
| 토큰 발급 | Supabase | `JwtProvider` (HS256) |
| 토큰 검증 | JWKS 조회 + HS256 폴백 | `JwtVerifier` (HS256 전용) |
| 사용자 PK | Supabase `auth.users.id` | 가입 시 백엔드가 생성한 UUID |
| DB | Supabase Postgres | 로컬 docker-compose Postgres |

**FE 는 바꿀 것이 없다.** `SignupResponse`, `TokenResponse`, `AuthUserDto` 의 필드
모양과 엔드포인트 경로가 그대로다. 구현만 교체했다.

### 8-3. 변경된 파일

신규 3 개:

- `db/migration/V9__add_profile_credentials.sql`
- `auth/provider/JwtProvider.java`
- `auth/provider/JwtVerifier.java`

삭제 7 개:

- `auth/provider/SupabaseJwtVerifier.java`
- `dev/SupabaseAuthClient.java`
- `dev/DevTokenController.java`
- `dev/DevTokenRequest.java`
- `dev/DevTokenResponse.java`
- `src/test/.../dev/DevTokenControllerTest.java`
- `src/test/.../auth/SupabaseJwtVerifierTest.java`

이름 변경 1 개: `SupabaseJwtAuthenticationFilter` → `JwtAuthenticationFilter`.
이 이름을 참조하던 테스트 6 개도 함께 고쳤다.

수정:

- `auth/application/AuthService.java` 전면 재작성
- `users/domain/Profile.java` (email, passwordHash, `signup()` 팩토리)
- `users/domain/ProfileRepository.java` (`findByEmail`, `existsByEmail`)
- `config/SecurityConfig.java` (`PasswordEncoder` 빈 추가, `/dev/**` permitAll 삭제)
- `application.yml`, `application-local.yml`, 테스트 yml 2 개, `.env.example`

신규 테스트 2 개: `JwtVerifierTest` 9 개, `AuthServiceTest` 9 개.

### 8-4. 설정

`JWT_SECRET` 하나만 쓴다. `SUPABASE_*` 는 전부 없앴다.

```properties
JWT_SECRET=<32자 이상 랜덤 문자열>
JWT_ACCESS_TTL=PT1H
JWT_REFRESH_TTL=P30D
```

로컬에서는 비워 두어도 된다. `application-local.yml` 이 개발용 기본값을 준다.
운영에서는 반드시 넣어야 한다. 비었거나 `replace-me` 로 시작하거나 32 바이트
미만이면 `JwtVerifier` 가 기동 시점에 예외를 던진다.

### 8-5. 설계 결정 세 가지

**리프레시 토큰은 상태를 저장하지 않는다.** `refresh_tokens` 테이블을 만들지 않고
`typ=refresh` 클레임을 가진 JWT 를 쓴다. 그래서 서버가 토큰을 즉시 무효화할 수
없다. 대신 `AuthService.refresh` 가 프로필을 다시 읽으므로, 탈퇴한 계정의 토큰은
바로 막힌다. 유출된 토큰은 만료까지 유효하다.

**액세스 토큰과 리프레시 토큰은 서로 대체할 수 없다.** `verify` 는 `typ=refresh` 를
거절하고 `verifyRefresh` 는 그 반대를 거절한다. 둘 다 테스트로 고정했다.

**로그인 실패 메시지는 한 종류다.** 없는 이메일과 틀린 비밀번호가 똑같이
`AUTH_INVALID` 를 준다. 어떤 이메일이 가입되어 있는지 로그인 화면에서 새지 않게
하기 위해서다. `/auth/check-email` 은 가입 폼을 위해 그 정보를 일부러 노출한다.

### 8-6. V9 마이그레이션 주의

`profiles.email` 과 `profiles.password_hash` 는 nullable 이다. V1 부터 V8 사이에
만들어진 기존 행은 Supabase 가 자격증명을 들고 있어서 백필할 값이 없다. 그런 행은
로그인할 수 없고, 다시 가입하면 된다. 이메일 유니크는 부분 인덱스
(`WHERE email IS NOT NULL`) 로 걸어서 NULL 행이 여러 개 있어도 된다.

### 8-7. 검증 결과

```bash
./gradlew spotlessApply test \
  --tests "*AuthServiceTest*" --tests "*JwtVerifierTest*" \
  --tests "*AuthControllerSliceTest*" --tests "*SecurityConfigTest*" \
  --tests "*ControllerSliceTest*" --tests "*MlPredictionClientTest*"
```

| 클래스 | 결과 |
|---|---|
| `AuthServiceTest` | 9 / 9 통과 |
| `JwtVerifierTest` | 9 / 9 통과 |
| `AuthControllerSliceTest` | 12 / 12 통과 |
| `SecurityConfigTest` | 4 / 4 통과 |
| `AnalysisControllerSliceTest` | 10 / 10 통과 |
| `FolderControllerSliceTest` | 18 / 18 통과 |
| `FavoriteControllerSliceTest` | 13 / 13 통과 |
| `NotificationControllerSliceTest` | 7 / 7 통과 |
| `FcmTokenControllerSliceTest` | 5 / 5 통과 |
| `MlPredictionClientTest` | 4 / 4 통과 |

합계 91 개 전부 통과. 실패 0 건.

### 8-8. 아직 확인 못 한 것

**V9 를 실제 Postgres 에 적용해 보지 못했다.** `FlywayMigrationSmokeTest` 가
Testcontainers 를 쓰는데 이 환경에 Docker 가 없다. V1 부터 V9 까지 전체 시퀀스
dry-run 은 Docker 를 켜고 다음을 돌려서 꼭 확인할 것.

```bash
./gradlew test --tests "*FlywayMigrationSmokeTest*"
```

같은 이유로 `SignupGoldenPathE2ETest` 와 `AuthOnboardingE2ETest` 도 못 돌렸다.
두 테스트는 `JwtTestFactory` 가 만든 토큰으로 `POST /users` 를 호출하는 구조라
이번 변경과 호환되지만, 실제 실행으로 확인한 것은 아니다.

### 8-9. 같이 고친 문서

`README.md` 와 `docs/architecture.md` 도 함께 갱신했다. 둘 다 Supabase 를 전제로
쓰여 있어서 그대로 두면 다음 사람이 틀린 지시를 따르게 된다.

- README 환경변수 표에서 `SUPABASE_*` 4 개를 빼고 `JWT_SECRET`,
  `JWT_ACCESS_TTL`, `JWT_REFRESH_TTL` 을 넣었다.
- README 의 "Swagger 에서 dev 토큰 발급" 절을 "가입 후 로그인" 절로 바꿨다.
  `POST /dev/auth/token` 은 삭제됐다. `POST /auth/signup` 으로 가입하고
  `POST /auth/login` 이 주는 `access_token` 을 Swagger Authorize 에 넣으면 된다.
- `architecture.md` 의 1 번 시퀀스 다이어그램에서 Supabase Auth 참여자를 빼고
  `AuthController` 와 `AuthService` 가 직접 토큰을 발급하는 흐름으로 다시 그렸다.

(2026-11-01 정정) 아래 문단은 정확하지 않다. V9 SQL 주석, `JwtVerifierTest`,
`AuthServiceTest` 의 javadoc, `docs/schema-migration.md` 본문에도 Supabase 언급이
남아 있다. 전부 이력 설명이라 동작에는 영향이 없다.

이제 `src/`, `README.md`, `docs/` 어디에도 Supabase 참조가 없다. 유일한 예외는
두 군데의 의도적인 이력 설명이다. `V1__init_schema.sql` 은 불변 파일이라 당시
주석을 그대로 두었고, `Profile.java` 의 javadoc 은 왜 자격증명 컬럼이 nullable
인지 설명하려고 전환 사실을 언급한다.

---

## 9. 2026-11-01 후속 작업

### 9-1. ML 산출물 검증

`ML_result/` 로 받은 Colab V2.4 산출물을 검증했다. 필수 4 개는 모두 있다.
선택 파일 `runtime_versions_v24.json` 은 없다.

| 파일 | 결과 |
|---|---|
| `ensemble_pipeline_v24.joblib` | 키 7 개 (`ridge`, `xgb`, `ensemble_xgb_weight`=0.7, `target_clip`, `features`, `target`, `version`). scikit-learn 1.6.1 로 저장됨 |
| `latest_kospi_predictions_v24.csv` | 669 행, 필수 컬럼 전부 있음, NaN·중복 코드 없음. 기준일은 651 종목이 2026-10-30 이고 18 종목이 그 이전이다 |
| `feature_schema_v24.json` | 31 개 Feature. 순서가 모델 bundle 과 같다 |
| `data_manifest_v24.json` | 2015-01-01 ~ 2026-10-31, 학습 2024~2026, mean Rank IC 0.116 |

나머지 7 개(`oos_predictions_v24.parquet`, `walk_forward_metrics_v24.csv` 등)는
API 가 읽지 않는 분석용 산출물이다.

`make validate` 는 `features=31, predictions=669, latest_date=2026-10-30` 으로
통과했다. CSV 의 Feature 로 Ridge·XGBoost 를 다시 돌려 보았다. 그 결과가 CSV 의
`Predicted_Excess_Return_60D` 와 1e-16 이내로 일치했다. ML 레포 `pytest` 는 3/3
통과했다.

데이터에서 FE 가 알아야 할 점은 두 가지다.

1. `Predicted_Excess_Return_60D` 의 최댓값이 +1.5% 다. 거의 모든 종목이
   음수이므로 "Top 10%" 도 대부분 KOSPI 대비 마이너스다. 상대 순위로만 표시한다.
2. `Sector` 컬럼은 669 행 전부 비어 있다.

### 9-2. End-to-end 확인

ML 서비스(`KOSPI_API_ARTIFACT_DIR` 로 `ML_result/` 지정)와 BE 를 로컬에서 띄웠다.
가입, 로그인, 예측 조회를 실제로 호출했다.

- `005930` → `available=true`, `signal="Above Average"`, `as_of_date=2026-10-30`
- `000660` → `available=true`
- `999999` → `available=false`
- 토큰 없음 → 401

실제 payload 는 §1 계약과 일치한다. `MlPredictionJson` 은 수정할 필요가 없었다.

앱(FE)까지 붙인 E2E 도 확인했다 (2026-11-01). iOS 시뮬레이터(iPhone 17)에서
Flutter integration test 로 앱을 띄우고, 로그인 화면에 입력해 로그인한 뒤
`005930`, `000660` 의 분석 탭을 열었다. 두 종목 모두 예측 카드가 그려졌고,
ML 서비스 로그에 `GET /api/v1/predictions/{ticker}` 200 이 두 번 찍혔다.
DART 키가 없어 `stocks` 가 비어 있으므로, 이 두 종목은 임시 DB 에 직접 넣었다.
테스트 파일은 임시로 만들고 지웠다. 다시 돌리려면 `integration_test` dev dependency 를
추가해야 한다.

### 9-3. 이번에 바꾼 것 (커밋하지 않음)

BE:

| 파일 | 변경 |
|---|---|
| `stocks/client/KrxClient.java` | 시세 URI 를 baseUrl 에서 만든다. `https` 하드코딩 제거. 운영 URL 은 전과 같다 |
| `auth/application/AuthService.java` | `signup` 이 `saveAndFlush` 를 쓴다. 전에는 응답의 `created_at` 이 null 이었다 |
| `stocks/domain/FinancialStatement.java` | `sjDiv` 기본값 `"CIS"`. V8 컬럼 DEFAULT 와 맞춘다 |
| `db/migration/V10__remove_kosdaq_placeholder_stocks.sql` | 신규. V7 이 빠뜨린 `Q%` placeholder 150 개를 지운다 |
| 테스트 5 개 | `KrxClientTest`, `AuthServiceTest`, `AuthOnboardingE2ETest`(created_at 검증 추가), `FavoriteRepositoryTest`, `FlywayMigrationSmokeTest`(최종 상태 검증으로 재작성) |
| `docs/schema-migration.md` | placeholder 제거 사실 추가 |

V10 주의: 기존 로컬 DB 에서 다음 부팅 때 실행된다. V3 seed 형식
(`^Q[0-9]{5}$`) 과 정확히 맞는 종목과, 그 종목에 걸린 관심종목·알림·보고서 행이
지워진다. 실제 종목코드는 DART `stock_code` 의 6 자리 숫자라서 실제 데이터는
지워지지 않는다.

FE:

| 파일 | 변경 |
|---|---|
| `lib/models/analysis_model.dart` | `PredictionResponse` 추가 |
| `lib/services/api_service.dart` | `getStockPrediction(ticker)` 추가 |
| `lib/screens/company/analysis_tab.dart` | 예측 카드 추가. 요청이 실패하면 빈 상태를 보여 주고 다른 섹션은 그대로 동작한다 |
| `test/prediction_model_test.dart` | 신규. available true/false 파싱 |

카드 문구는 ML 측 요구를 따른다. `model_agreement_score` 는 "모델 일치도" 로만
표시하고, 확률이라는 말은 쓰지 않는다. 손익 색상도 쓰지 않는다.

### 9-4. 검증 결과

- BE `./gradlew build`: 성공. 220/220 통과. `FlywayMigrationSmokeTest`,
  `SignupGoldenPathE2ETest`, `AuthOnboardingE2ETest` 를 포함한 Testcontainers
  테스트를 전부 돌렸다. `spotlessCheck` 도 통과했다.
- V1~V10 을 빈 Postgres 15 에 적용했다. 전부 성공했다.
- FE `flutter analyze`: 이슈 없음. `test/prediction_model_test.dart` 2/2 통과.
- FE `test/widget_test.dart` 는 실패한다. 변경 전 HEAD 에서도 같은 이유
  ("A Timer is still pending")로 실패하므로 이번 변경과 무관하다.

### 9-5. 실행 환경 주의

- Gradle 8.10 은 JDK 26 에서 `Unsupported class file major version 70` 으로
  실패한다. `JAVA_HOME` 을 JDK 21 로 지정한다.
- `.env.example` 의 `JWT_SECRET` placeholder 는 UTF-8 로 31 바이트다. 그대로
  `.env` 에 복사하면 기동 시점에 실패한다. 로컬에서는 이 줄을 지운다.
- 기존 Docker 볼륨 `be_postgres-data` 는 `postgres` 역할이 없는 상태로
  초기화되어 있다. docker-compose 기본 계정으로는 접속이 실패한다. 이 볼륨은
  건드리지 않았다. 새로 쓰려면 볼륨을 직접 확인한 뒤 정리한다.

### 9-6. 남은 일 (§10 에서 갱신)

§10-6 을 본다.

## 10. 2026-11-02 데모 완성 작업

목표: 데모 버전에서 KOSPI·KOSDAQ 어떤 종목을 검색해도 시세·뉴스·재무·공시·테마·분석 보고서가 나오게 한다.
푸시 알림(Firebase)은 범위에서 뺐다. 코드는 그대로이고, 키가 없으면 발송을 건너뛴다.

### 10-1. 외부 키

- `DART_API_KEY`: 로컬 `.env` 에 넣었다. 키 출처는 개인 ThemeSense 작업 폴더의 `.env` 다.
  ML 레포(PUBLIC) 히스토리의 노트북에 있던 다른 DART 키는 이미 만료됐다 (status 010).
- 한국투자증권(KIS) 키: 없다. 없으면 네이버 시세로 대신 받으므로 데모에는 필요 없다.
- Firebase: 없다. 범위 밖.

### 10-2. 시장 구분

DART `corpCode.xml` 에는 시장 정보가 없어서 `CorpCodeBootstrap` 이 모든 종목을 KOSPI 로 넣고 있었다.
이제 네이버 시장별 목록(`NaverMarketListClient`, 약 44 회 호출)으로 KOSPI/KOSDAQ 을 정한다.

- 두 목록 어디에도 없는 종목(코넥스, 상장폐지 등)은 새로 넣지 않는다.
- 이미 있는 종목은 절대 지우지 않는다. 지우면 관심종목·보고서·테마가 FK CASCADE 로 같이 사라지고,
  네이버 목록이 잠깐 덜 오는 것만으로도 그렇게 될 수 있기 때문이다 (리뷰 HIGH 지적 반영).
  그래서 예전에 전부 KOSPI 로 들어간 DB 에는 코넥스 종목이 남는다. 데모 DB 는 새로 만드는 것을 권한다.
- 목록을 못 받으면(빈 맵) 새 종목을 넣지 않고 다음 부팅에 다시 시도한다.
- 적재가 예외로 끝나도 `StocksBootstrappedEvent` 는 `finally` 에서 나간다.
- 결과: DART 상장 4,009 건 중 KOSPI 829, KOSDAQ 1,825 (합 2,654). DART `corp_cls` 와 정확히 일치했다.

### 10-3. 데모 테마 데이터 (ThemeSense 대체)

ThemeSense 모델 산출물이 없어서 규칙 기반 데모 산출물을 만들었다. **모델 출력이 아니다.**

- 생성: `scripts/generate_demo_themesense.py`. DART 회사개황의 업종코드(KSIC)를 17 개 테마로 매핑하고,
  대형주 10 개는 직접 보정한다 (예: 삼성전자 KSIC 264 = 통신장비 → AI/반도체). 테마별 β 는 손으로 정한 값이다.
- 산출물: `demo/themesense/` 3 개 파일. 2,654 종목 전부, 종목당 상위 3 테마.
- 적재: `.env` 에 `THEMESENSE_ARTIFACTS_DIR=demo/themesense` 를 두면, 종목 적재가 끝난 뒤
  `ThemeSenseAutoIngest` 가 stock_themes 가 비어 있을 때 한 번 적재한다 (약 70 초).
- 다시 만들려면: `DART_API_KEY=... python3 scripts/generate_demo_themesense.py --out demo/themesense --cache <캐시파일>`.
  DART 를 약 4,000 회 부르고 10 분쯤 걸린다. 캐시가 있으면 이어서 진행한다.

### 10-4. 분석 보고서 생성 방식

- 공시 수집 직후 이벤트 경로는 그 종목의 재무 데이터가 없으면 보고서를 만들지 않는다.
  첫 수집 때 공시 약 3,000 건마다 재무를 받으면 DART 일일 한도(약 2 만 회)를 넘기 때문이다.
- 대신 `GET /analysis/{ticker}/reports` (1 쪽) 를 열 때 `ReportBackfillService` 가 최근 공시 5 건 중
  보고서 없는 것을 만든다. 재무가 없으면 그때 DART 에서 받는다. 첫 조회는 1~2 초 걸린다.
  파라미터 검증(목록 조회)을 먼저 하므로 잘못된 요청(400)은 DART 를 부르지 않는다.
- 재무 변화량은 손익계산서 연간 대 연간(예: 2025 vs 2024)으로 계산한다. 연간이 둘 미만이면
  시간 순 최신 분기를 전년 같은 분기와 비교한다 (2026H1 ↔ 2025H1). 누적치끼리 섞지 않는다.
- 극단값(지주회사 영업이익률 600% 등)은 비율 ±20%p, 성장률 ±50% 로 자른다.
- 한계: 기대 CAR 은 공시 내용이 아니라 종목 재무로 정해지므로, 같은 종목의 보고서는 값이 같다.

### 10-5. 이번에 고친 버그

| 증상 | 원인 | 수정 |
|---|---|---|
| 일별 시세·뉴스·PER 등이 빈 값 | 네이버가 PC 페이지를 내림 (410 "더 이상 제공되지 않습니다") | `m.stock.naver.com` JSON API 로 교체 |
| 재무상태표·현금흐름표 탭이 비어 있음, 지배주주순이익에 자본총액이 들어감 | `DartFinanceClient` 가 행에 없는 `fs_div` 를 읽어 BS·CF·SCE 가 모두 CIS 한 행에 섞임 | `sj_div` 를 읽고, SCE 는 버리고, 지배/비지배 순이익은 "포괄" 이 없는 순이익 행에서 고른다 |
| 위 수정 후 BS·CF 행 저장 실패 (unique 위반) | V8 이 옛 키를 지우려 했지만 이름이 틀려 V1 `uk_financial_ticker_period` 가 남음 | `V11__drop_legacy_financial_unique.sql` |
| 부채총계 = 자산총계 | "부채총계" 가 앞선 "자본과부채총계" 행에 걸림 | 계정명 정확 일치를 먼저 찾는다 |
| ROE·ROA 항상 빈 값 | 자본·자산이 손익 행에 없음 | 같은 기간의 BS 행에서 가져온다 |
| 기말 현금 빈 값 | 계정명이 "기말의 현금…"/"기말현금…" 인데 키워드가 "기말의현금" | 키워드 "기말" |
| 재무 탭 표가 화면 아래로 넘침, 분기 열 순서가 H1→Q1→Q3 | 세로 스크롤 없음, 문자열 정렬 | FE 세로 스크롤 + 시간 순 정렬 |
| `.env` 에 DART 키를 넣으면 테스트가 실제 DART·네이버를 부름 | 테스트 프로파일도 `./.env` 를 import | 테스트 프로파일에서 `dart.api.key`, `themesense.artifacts-dir` 를 비움 |

V11 은 `financial_statements` 를 비운다. 옛 파서가 섞어 저장한 CIS 행이 남아 있으면 다시 받지 않기 때문이다.
재무는 화면을 열 때 DART 에서 다시 받는다. 이미 만들어진 분석 보고서는 지우지 않는다.

### 10-6. 검증과 남은 일

검증 (2026-11-02):

- BE `./gradlew build` 통과, 테스트 233 개.
- 별도 리뷰 에이전트 검토: HIGH 1 (부트스트랩의 종목 삭제), MEDIUM 3 (V11 데이터 정리, 요청 검증 순서,
  분기 fallback) 을 모두 반영했다. LOW 중 이벤트 `finally` 발행도 반영했다.
- 빈 DB 에서 기동 → 종목 2,654 적재 → 테마 자동 적재 (17 테마, 7,962 stock_themes) → 공시 2,763 건 수집.
- iOS 시뮬레이터 Flutter integration test: 로그인 → 검색("제주반도체") → 정보·뉴스·재무(손익·재무상태)·공시·분석 탭.
  모든 탭에 실제 데이터가 나왔고 렌더링 예외가 없었다. 테스트 파일은 임시로 만들고 지웠다.

남은 일:

1. 변경을 브랜치에 나눠 커밋하고 PR 을 연다.
2. 실제 기기 확인.
3. 데모 테마는 규칙 기반이다. ThemeSense 모델을 학습하면 `THEMESENSE_ARTIFACTS_DIR` 만 바꾸고
   `--etl-themesense` 로 다시 적재한다.
4. `signal` 한국어 표기, FE `widget_test.dart` 기존 실패는 그대로다.
5. 네이버 API 는 비공식이다. 응답 형식이 바뀌면 시세·뉴스·지표·시장 구분이 같이 깨진다.
