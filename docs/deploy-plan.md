# GCP 배포 계획: 서버 스펙 + 받아야 할 정보

> 작성일: 2026-11-13. 목적: Terraform(인프라) + Ansible(구성)으로 BE 를 GCP 에
> 완전 자동 배포하기 전에, **어떤 서버를 고를지**와 **사용자가 줘야 할 정보**를 확정한다.
> 스크립트는 이 문서로 스펙·정보가 확정된 뒤 작성한다. FE(Flutter)는 범위 밖이다.

---

## 0. 먼저 결정한 것: 구조

**단일 Compute Engine VM 한 대에 기존 `docker-compose.yml` 을 그대로 올린다.**
Postgres + Redis + Spring Boot 세 컨테이너를 한 박스에서 돌리고, 앞단에 nginx(TLS).

이유:

- 사용자가 **Ansible** 을 원한다. Ansible 은 VM 을 구성하는 도구다. Cloud Run +
  Cloud SQL + Memorystore 같은 서버리스 조합은 Ansible 이 할 일이 없어진다.
  VM 한 대가 "Terraform 으로 만들고 Ansible 로 구성한다" 에 정확히 맞는다.
- **레이더 SSE 와 스케줄러가 단일 인스턴스 전제다.** 레이더의 SSE emitter 레지스트리는
  인메모리(코드에 `// shortcut: single instance only` 명시), DART 폴러·하트비트도
  `@Scheduled` 단일 노드 기준이다. 여러 인스턴스로 늘리면 지금 코드가 깨진다.
  단일 VM 이 코드와도 맞다.
- 토이/졸업작품 규모다. 관리형 서비스는 비용·복잡도만 늘린다.

업그레이드 경로(지금은 안 함): 트래픽이 커지면 Postgres→Cloud SQL,
Redis→Memorystore, 다중 인스턴스+Redis pub/sub 로 SSE 분산.

---

## 1. 추천 서버 스펙

| 항목 | 추천값 | 이유 |
|---|---|---|
| 서비스 | Compute Engine VM 1 대 | 위 §0 |
| 머신 타입 | **e2-medium (2 vCPU, 4 GB)** | §1-1 참조. 핵심은 램 |
| OS | Ubuntu 22.04 LTS (또는 24.04 LTS) | Docker·Ansible 표준, apt 안정 |
| 디스크 | pd-balanced 30 GB | OS + Docker 이미지 + Postgres 볼륨 여유 |
| 리전/존 | **asia-northeast3 (서울) / -a** | §1-2 참조 |
| 외부 IP | 고정(정적) 외부 IP 1 개 예약 | 재부팅해도 주소 유지 → DNS·데모 안정 |
| 방화벽 | 80·443 전체 공개, 22 는 내 IP 만 | §1-3 |

### 1-1. 왜 4 GB(e2-medium)인가, 2 GB 는 왜 위험한가

Dockerfile 이 멀티스테이지라 **이미지 빌드 중 gradle 이 VM 안에서 돈다**
(`./gradlew dependencies` + `bootJar`). 이 빌드만 순간 1.5~2 GB 를 쓴다. 거기에
런타임은 JVM + Postgres + Redis + nginx 가 상주한다.

- e2-small(2 GB): gradle 빌드 중 OOM 위험이 크고, 런타임도 빡빡하다.
- **e2-medium(4 GB): 빌드도 런타임도 안전.** 이게 최소 안전선이다.

비용을 더 줄이려면 두 가지 선택지가 있다(지금 결정 안 해도 됨).

1. 이미지를 **VM 밖에서 빌드**(로컬/CI)해 Artifact Registry 에 올리고 VM 은 받아서
   실행만 한다. 그러면 빌드 램 스파이크가 사라져 e2-small(2 GB)도 가능. 대신
   Artifact Registry + 빌드 파이프라인이 생겨 자동화가 한 겹 늘어난다.
2. 그냥 e2-medium 으로 VM 에서 빌드(가장 단순, 추천 기본값).

**반드시 처리할 메모리 함정 하나:** 런타임 JVM 이 `-XX:MaxRAMPercentage=75.0` 이다.
컨테이너에 메모리 상한이 없으면 JVM 이 VM 전체(4 GB)의 75%(3 GB)를 잡으려 해서
Postgres·Redis 가 굶는다. 그래서 compose 에 backend 컨테이너 `mem_limit`(약 1.5~2 GB)
를 걸어 75% 가 컨테이너 기준으로 계산되게 한다. Ansible 이 올리는 compose
override 에서 처리한다.

### 1-2. 왜 서울 리전인가

KOSPI 종목 서비스이고 사용자가 한국이다. 그리고 백엔드가 호출하는 외부 API,
즉 DART(opendart.fss.or.kr), 네이버 금융, KRX/KIS 가 전부 한국에 있다. 서울
리전(asia-northeast3)이 지연을 최소화하는 당연한 선택이다.

### 1-3. SSH 포트(22) 잠금

Ansible 은 SSH 로 VM 에 접속해 구성한다. 22 번을 전 세계(0.0.0.0/0)에 열면
위험하므로 **내 공인 IP 하나만** 허용하는 걸 추천한다. 그래서 아래에서 네 공인 IP 를
묻는다. (대안: IAP 터널. 더 안전하지만 설정이 늘어난다. 원하면 그쪽으로.)

---

## 2. 자동화가 할 일 (스크립트는 나중)

**Terraform(인프라 생성):** VPC/서브넷(또는 기본망), 고정 외부 IP, 방화벽 규칙
(80/443/22), VM 인스턴스(위 스펙), VM 에 SSH 공개키 주입, 최소 권한 서비스 계정.

**Ansible(VM 구성):** 아래를 순서대로 한다.
- Docker 와 compose 를 설치한다.
- 레포를 체크아웃한다(또는 이미지를 pull 한다).
- `.env` 를 만든다(아래 시크릿 주입).
- nginx 리버스 프록시와 Let's Encrypt TLS 를 붙인다.
- `docker compose up -d` 로 올린다.
- 부팅 자동 기동(systemd)과 헬스체크(`/actuator/health`)를 건다.

---

## 3. 내가 알아서 생성할 것 (너는 안 줘도 됨)

아래는 사용자가 제공할 필요가 없다. 내가 배포 중에 만들어 안전하게 보관한다.

- `JWT_SECRET`: 랜덤 32+ 바이트 생성.
- `DB_PASSWORD`: 컨테이너 Postgres 용 랜덤 생성(외부 비공개).
- Ansible 접속용 **SSH 키페어**: 로컬에서 생성, 공개키만 VM 에 올림.

시크릿은 VM 의 `.env`(권한 600) 또는 GCP Secret Manager 에 넣는다. 채팅·git 에
평문으로 남기지 않는다.

---

## 4. 너가 줘야 할 정보 (체크리스트)

### A. GCP 접근: 필수

1. **GCP 프로젝트 ID** (이미 있으면 그 ID, 없으면 "새로 만들어줘").
2. **결제 계정 연결 여부** (연결 안 돼 있으면 Terraform 이 리소스를 못 만든다).
3. **Terraform 인증 수단**: 아래 역할을 가진 서비스 계정 키(JSON), 또는
   `gcloud auth application-default login` 을 네가 직접 실행.
   - 필요한 역할: `roles/compute.admin`, `roles/iam.serviceAccountUser`,
     그리고 (Secret Manager 쓰면) `roles/secretmanager.admin`.
   - 키 JSON 은 채팅에 붙여넣지 말 것. 파일 경로로 주거나 마스킹 입력으로.

### B. 리전·위치: 확인만

4. 서울(asia-northeast3) 로 간다. 맞으면 "ok", 아니면 원하는 리전.

### C. 도메인 / HTTPS: 필수 결정

5. **도메인이 있나?**
   - 있으면: 그 도메인(또는 서브도메인, 예 `api.example.com`)을 알려주고,
     DNS 를 어디서 관리하는지(Cloud DNS / 외부 등록기관) 알려줘. A 레코드를
     고정 IP 로 맞추면 Let's Encrypt 자동 발급.
   - 없으면: 도메인 없이 `<IP>.nip.io` 로 Let's Encrypt TLS 를 붙일 수 있다
     (데모용으로 충분). 이걸 원하면 "nip.io 로".

### D. SSH 잠금: 권장

6. **네 공인 IP** (22 번을 그 IP 만 허용하려고). 모르면
   "whatismyip" 로 확인하거나, 그냥 "열어둬" 하면 key-only 로 0.0.0.0/0 허용.

### E. 외부 연동 시크릿: 기능별

7. **`DART_API_KEY`** (사실상 필수). 없으면 종목 마스터·공시가 비어 모든 화면이
   빈 깡통이 된다. <https://opendart.fss.or.kr> 에서 무료 발급. 채팅 말고 안전하게 전달.
8. KIS/KRX 키(`KRX_API_KEY`/`KRX_API_SECRET`): **선택**. 없으면 네이버 시세
   폴백으로 동작한다. 줄 거면 안전하게.
9. Firebase FCM: **선택**. 푸시를 켜려면 서비스 계정 JSON 과 프로젝트 ID 가 필요하다.
   없으면 인앱 알림만 되고 푸시만 꺼진다.

### F. 범위 결정: 답만

10. **ML 예측 서비스(FastAPI)도 같이 배포할까?**
    - 지금 상태: 그 서비스는 Colab 산출물 artifact 4 개가 있어야 뜬다. 레포에 없다.
    - 기본값: **배포 안 함.** `ML_API_*` 를 비워 두면 예측 탭만
      `available:false` 로 나오고 나머지는 정상. 같이 배포하려면 artifact 를 줘야 한다.
11. **데이터 적재를 배포 자동화에 포함할까?**
    - 화면이 실제로 채워지려면 부팅 후: 종목 마스터(DART corpCode), 데모 ThemeSense
      적재(`generate_demo_themesense.py` + `--etl-themesense`), 재무제표 적재가
      필요하다. 이걸 Ansible 마지막 단계로 자동 실행할지, 아니면 서버만 띄우고
      적재는 수동으로 둘지.

---

## 5. 한 가지 확인 (거버넌스)

이건 네 개인/학교 졸업작품(Hongik-graduProject)이고 네 개인 GCP 프로젝트에
배포하는 것으로 이해했다. 그 전제로 진행한다. 만약 이 배포가 KISP 사내 인프라나
사내 데이터에 닿는 거라면 전제가 바뀌니(사내 정책·승인 경로) 미리 말해줘.
어느 쪽이든 시크릿은 채팅·git 에 평문으로 남기지 않고 안전하게 다룬다.
참고로 Terraform(`.tf`)·Ansible(`.yml`) 파일은 arch-gate 훅 대상이 아니라
작성에 막히지 않는다.

---

## 6. 요약: 지금 나한테 줄 최소 세트

이 다섯 개면 바로 시작한다. 나머지(§4 의 선택 항목)는 진행하며 물어도 된다.

1. GCP 프로젝트 ID (또는 "새로 만들어줘") + 결제 연결됨 여부
2. Terraform 인증 수단(서비스계정 키 경로 또는 "gcloud 로 네가 로그인해")
3. 서울 리전 ok 여부
4. 도메인 있음(+DNS 위치) / 없음("nip.io 로")
5. `DART_API_KEY` (안전한 방법으로)
