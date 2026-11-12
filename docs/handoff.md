# 세션 인수인계 (계속 갱신)

> 이 문서는 다음 세션이 이어서 작업할 수 있도록 맥락과 진행 상태를 담는다.
> 작업 단위가 끝날 때마다 갱신한다. 가장 위의 "현재 상태" 가 항상 최신이다.
> 마지막 갱신: 2026-11-12, 3 기능 전부 머지·푸시 완료.

---

## 1. 현재 상태

**세 기능 모두 완료·검토·머지·푸시됨.** 최종 main `38b7e89`,
142 개 테스트 녹색(실패 0), spotlessCheck 통과. 모든 브랜치 origin 에 있음.

| 항목 | 상태 |
|---|---|
| 3 기능 계획서 | 완료. `docs/feature-implementation-plan.md` |
| 타임머신 | **완료·머지·푸시.** `feat/timemachine` → main. critic APPROVE |
| 레이더 | **완료·머지·푸시.** `feat/radar` → main. critic 지적(스케줄러 풀) 반영 |
| 반박 모드 | **완료·머지·푸시.** `feat/critique` → main. critic 지적 2 건(트랜잭션 범위·zeroDelta) 반영 후 CLEARED-TO-MERGE |
| 설계 검토 | 세 기능 모두 critic1 검토 완료 |
| 커밋·푸시 | 완료 |

### main 커밋 (743ab53 이후)

```
38b7e89 merge: feat/critique into main [#critique]
b9c28e2 feat(critique): ...
4d1c15b merge: feat/radar into main [#radar]
16b0c0e feat(radar): ...
9b9e7d9 merge: feat/timemachine into main [#timemachine]
3c64823 feat(timemachine): ...
```

### 새 API (세 기능)

- `GET /analysis/reports/{id}/replay`: 공시 되감기 (타임머신)
- `GET /analysis/{ticker}/track-record?type=`: 종목 모델 적중 전적 (타임머신)
- `GET /radar/points?from=&to=`: 레이더 점 스냅샷
- `GET /radar/stream`: 레이더 실시간 SSE
- `GET /analysis/reports/{id}/critique`: 반박 모드 (등급·점수·근거)

### 반드시 남은 수동 확인 (자동 테스트로 증명 안 됨)

세 컨트롤러의 HTTP 계층은 슬라이스 테스트가 생략돼 미검증이다 (arch-gate).
로직은 단위 테스트로 덮였고, 빠진 건 라우팅·인증·응답 모양뿐이다. 앱을 띄워:

- `curl -N -H "Authorization: Bearer <jwt>" http://localhost:8080/radar/stream`
  (스트림 유지 + 20 초마다 ping, 헤더 빼면 401)
- `GET /analysis/reports/{id}/replay`, `/track-record`, `/critique` 의
  200 모양 / 400(잘못된 param) / 404(없는 report) / 401(무토큰)

### 지금까지 머지된 것 (2026-11-12)

- `feat/timemachine` (3c64823) → main: V13/V14, look-ahead 안전 Δ, sj_div/BS 버그 수정,
  replay·track-record API. 크리틱 APPROVE (nits only).
- `feat/radar` (16b0c0e) → main: /radar/points·/radar/stream(SSE), SecurityConfig async
  허용, `spring.task.scheduling.pool.size: 2`. 크리틱 APPROVE-WITH-FIXES → 그 fix 반영.
- 합쳐진 main 에서 125 개 테스트 녹색, 실패 0. 세 브랜치 모두 origin 에 푸시.
- 커밋 메시지는 safe-commit 훅 규칙: `type(scope): subject [#tag]`. tag 는 단어 허용
  (`[#radar]` 등), 실제 이슈번호 불필요. `--no-verify` 금지, WIP 금지.

### 반영 안 한 크리틱 지적 (비차단, 알려진 한계)

- 레이더 푸시가 `taskExecutor` 를 리포트 파이프라인과 공유한다. 느린 SSE 가 몰리면
  파이프라인에 백프레셔. 데모 규모에선 안 터진다. 격리하려면 전용 bounded executor.
- 레이더 `/radar/points` 는 DB LIMIT 없이 메모리에서 500 컷. KR 물량이면 괜찮음.

### arch-gate 결정 (2026-11-12)

사용자가 `.kisp/arch.json` 을 만들지 않기로 했다. 그래서 두 컨트롤러
`@WebMvcTest` 슬라이스 테스트는 생략했다.

**왜 막혔나.** 이 레포는 Spring Boot 다. `@WebMvcTest` 슬라이스 테스트 파일은
`import org.springframework.boot...` 를 포함하는데, arch-gate 훅의
`server_code_patterns` 가 그 import 를 "웹 서버 코드" 로 판정한다. dx 등급 세션이
`.java` 새 파일을 Write 하면서 루트에 `.kisp/arch.json` 선언이 없으면 차단된다
(rule ARCH-DECL). 기존 파일 Edit 과 단위 테스트(순수 Mockito, Spring import 없음)는
안 걸렸고, 두 컨트롤러의 슬라이스 테스트 신규 Write 만 걸렸다.

**생략한 파일 (나중에 쓰려면 arch.json 선언 후 가능).**

- `src/test/java/com/hongik/grad/radar/api/RadarControllerSliceTest.java` (미작성)
- `src/test/java/com/hongik/grad/timemachine/api/TimeMachineControllerSliceTest.java` (미작성)

두 에이전트 모두 "내용은 준비됐고 arch.json 이 생기면 쓸 수 있다" 고 했다.

**그래서 자동 테스트로 증명되지 않는 것 (수동 curl 필요).**

- 레이더: `/radar/**` 무토큰 401, `/radar/stream` 의 `text/event-stream`,
  SSE async dispatch 보안 수정(스트림이 열린 채 20 초마다 `:ping`)
  - 확인: `curl -N -H "Authorization: Bearer <jwt>" http://localhost:8080/radar/stream`
    (헤더 빼면 401 이어야 함)
- 타임머신: `/analysis/reports/{id}/replay` 와 `/analysis/{ticker}/track-record` 의
  200 응답 모양, 400(잘못된 ticker·type), 404(없는 report), 401(무토큰)

로직 자체는 단위 테스트로 덮여 있다. 빠진 것은 HTTP 껍데기뿐이다.
나중에 슬라이스 테스트를 되살리려면 arch.json 을 로컬에 두고(§5, git-exclude)
두 파일을 작성하면 된다.

## 2. 사용자가 정한 작업 방식

- 구현은 Opus 5.5 서브에이전트로 병렬 진행한다.
- 모든 결과는 `/team critic:1` 로 설계 적절성을 검토한다.
- ponytail(최소 완결 변경)과 context7(라이브러리 문서 확인)을 적극 쓴다.
- 검토가 승인된 worktree 는 커밋하고 푸시한다. git 충돌은 직접 푼다.
- 작업 단위가 끝날 때마다 이 문서를 갱신한다.
- FE 는 범위 밖이다. 백엔드와 데이터 적재까지만 한다.

## 3. 기능과 문서

| 문서 | 내용 |
|---|---|
| `docs/feature-proposals.md` | 3 기능의 제품 설계 (화면, 데이터 출처, 엣지 케이스) |
| `docs/feature-implementation-plan.md` | 구현 계약서. 파일 소유권, 스키마, API, 공유 인터페이스 |
| `docs/handoff-ml-integration.md` | 이전 작업: ML 예측 연동, Supabase 제거 |

계획서의 핵심 결정은 다음과 같다.

- 마이그레이션 번호: V13 `daily_prices`, V14 `financial_statements.disclosed_at`
- `daily_prices` 는 `stocks` 로 FK 를 걸지 않는다. KOSPI 지수를 `ticker = 'KOSPI'` 로 넣고
  상장폐지 종목 시세도 남긴다.
- look-ahead bias: DART `rcept_no` 앞 8 자리를 공시일로 저장하고, Δ 계산 시 그 날짜 이전
  재무제표만 쓴다.
- 기존 결함: `MetricDeltaCalculator` 가 기간당 `sj_div` 별 3 행 중 앞 두 행을 비교한다.
  타임머신 에이전트가 확인 후 고친다.
- 레이더 각도축은 데모 ThemeSense 17 개 테마 기준이다.
- `application.yml` 은 아무도 수정하지 않는다. 설정은 `@Value` 기본값으로 둔다.

## 4. git 작업 기록 (완료됨)

실제로 밟은 순서:

1. 타임머신·레이더를 각 worktree 에서 `feat/timemachine`·`feat/radar` 로 커밋.
2. 두 브랜치를 origin 에 푸시, main 에 머지(순서: timemachine → radar), main 푸시.
3. 머지된 main 에서 반박 모드 worktree 를 떼어 구현 → `feat/critique` 커밋·머지·푸시.
4. 세 머지 모두 충돌 없음. 임시 worktree 3 개는 `git worktree remove` 로 정리 완료.

브랜치: `feat/timemachine`, `feat/radar`, `feat/critique` 모두 origin 에 있음.
worktree 는 남아 있지 않다 (`git worktree list` 는 main 하나만 보여준다).

## 5. 이 환경의 제약

- Gradle 은 기본 JDK 25 로는 안 돈다 (Gradle 8.10 비호환). 빌드 전에
  `JAVA_HOME=C:/Users/IT_intern2/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2`
  를 지정한다. 레이더 에이전트가 확인했다.
- Docker 가 없어 Testcontainers 테스트(Flyway 스모크, E2E)를 돌릴 수 없다.
  V13·V14 를 실제 Postgres 에 적용하는 검증은 Docker 가 있는 곳에서 해야 한다.
- arch-gate 훅: Spring Boot 서버 코드 `.java` 새 파일 Write 시 `.kisp/arch.json`
  이 없으면 차단된다 (§1 arch-gate 결정 참조). 회피·로그 무력화는 금지.
  사용자가 arch.json 을 만들지 않기로 결정 → 슬라이스 테스트 2 개 생략으로 종결.
- 회사 정책상 외부 네트워크 호출은 실행하지 않는다. 네이버 KOSPI 지수 엔드포인트
  형식은 검증되지 않았다.
- 커밋 시 사내 `safe-commit` 훅이 형식과 이슈번호를 검사할 수 있다.

## 6. 다음 세션이 할 일 (3 기능 자체는 끝남)

세 기능의 구현·검토·머지·푸시는 모두 끝났다. 남은 것은 검증과 후속뿐이다.

1. **HTTP 계층 수동 확인** (§1 "반드시 남은 수동 확인"). 앱을 띄워 curl 로
   5 개 엔드포인트의 401/400/404/200/SSE 를 직접 본다. 슬라이스 테스트가 없다.
2. **Docker 환경에서 Flyway 검증.** V1→V14 전체 시퀀스를 실제 Postgres 로
   dry-run (`*FlywayMigrationSmokeTest*`). V13/V14 가 이 세션에선 미검증.
3. **네이버 KOSPI 지수 엔드포인트 실호출 확인.** 응답 형식 미검증 (타임머신 시세용).
4. **데이터 적재** (화면이 실제로 채워지려면): `DART_API_KEY` →
   `generate_demo_themesense.py` + `--etl-themesense` → 재무제표 적재.
   자세한 선행 과제는 `feature-implementation-plan.md` §6.
5. (선택) 비차단 크리틱 지적 2 건 반영. 위 "반영 안 한 크리틱 지적" 참조.
6. (선택) arch.json 을 로컬에 두면 슬라이스 테스트 2 개를 되살릴 수 있다 (§1).

## 7. 이전 세션까지 끝난 일

- ML 예측 서비스 연동: `GET /analysis/{ticker}/prediction`. ML artifact 4 개가 없어
  실제 값 흐름은 미확인이다.
- Supabase 완전 제거, 로컬 Postgres 자체 인증으로 전환 (V9).
- 위 두 작업은 커밋되어 있다. 자세한 내용은 `docs/handoff-ml-integration.md`.
- 기존 결함: `KrxClientTest` 2 건은 `KrxClient` 의 https 하드코딩 때문에 실패한다.
  이번 작업과 무관하다.
