# AGENTS.md

> AgentSupport 프로젝트의 LLM 에이전트 진입점.
> 모든 작업은 본 문서를 가장 먼저 읽고 시작한다.
> 본 문서는 짧고 안정적이어야 한다. 상세 규칙은 각 도메인 문서로 위임한다.

---

## 1. 프로젝트 정체성

**한 줄 정의**: 한국 GA(독립대리점) 소속 보험설계사를 위한 통합 업무 지원 플랫폼.

**핵심 사용자**: 다보험사 위촉 설계사. 외근(모바일) → 상담(태블릿) → 정산/리포팅(PC) 환경을 옮겨다님.

**아키텍처**: **프론트엔드(Next.js) ↔ 백엔드(Spring Boot)** 분리. **단일 레포 + `/frontend` + `/backend` 폴더 구조.** HTTP API + 세션 쿠키로 통신.

**언어 정책**: **ko-first**. 모든 UI 문구·검증·접근성 테스트는 한국어 콘텐츠로 한다. 코드 식별자/주석/문서는 영어 허용.

**규제 준수 대상**: 보험업법, 금융소비자보호법, 광고 카피 제한, 개인정보보호법, 신용정보법.
→ 자세한 사항은 `docs/SECURITY.md`, `docs/references/insurance-regulation-llms.txt`.

---

## 2. 기술 스택

### 확정 (변경 시 본 문서 PR 필수)

**공통**
- 리포지토리 구조: **단일 레포 + `/frontend` + `/backend` + `/docs`**
- 패키지 매니저(FE): **pnpm**
- 빌드 도구(BE): **Gradle** (Kotlin DSL)
- API 컨트랙트: **OpenAPI 3.x** (백엔드 생성 → 프론트엔드 소비)
- 환경 변수: 루트 `.env` (`.env.example` 기반)

**배포 (확정)**
- FE 호스팅: **Vercel**
- BE 호스팅: **Render** (Web Service)
- PostgreSQL: **Supabase** (Seoul 리전) — 도메인 데이터 + Spring Session JDBC 저장
- ⚠️ Vercel과 Render는 서로 다른 eTLD+1 → cross-origin cookie 이슈. 운영 진입 전 커스텀 도메인 확보 필수 (`tech-debt-tracker.md`).

**프론트엔드**
- 프레임워크: **Next.js (App Router)**
- 언어: **TypeScript 5.x** (`strict: true`, `noUncheckedIndexedAccess: true`)
- 스타일링: **Tailwind CSS** (토큰은 `docs/DESIGN.md` 단일 출처)
- 폰트: **Pretendard** (한글) + **Hanken Grotesk** (Latin)
- 서버 상태: **TanStack Query**
- 폼/검증: **React Hook Form** + **Zod**
- API 클라이언트: **OpenAPI 코드 생성** (`openapi-typescript`)
- 컴포넌트 패턴: **shadcn/ui 스타일** (소유권 우선, 의존성 최소)

**백엔드**
- 프레임워크: **Spring Boot 3.x**
- JVM: **Java 21 LTS / OpenJDK** ([결정 필요] 벤더 — Temurin / Corretto / Liberica)
- DB: **PostgreSQL** (Supabase)
- 세션 저장소: **Spring Session JDBC** (Postgres의 `SPRING_SESSION` 테이블)
- API 문서: **springdoc-openapi** (소스로부터 OpenAPI spec 생성)
- 보안: **Spring Security** + **세션 쿠키** + **CSRF 토큰** (CookieCsrfTokenRepository)

### [결정 필요] — 작업 시작 전 확정

| # | 항목 | 후보 | 비고 |
|---|---|---|---|
| 1 | OpenJDK 벤더 | Temurin / Corretto / Liberica | 셋 중 어느 것이든 무방. CI 일관성만 확보. |
| 2 | ORM | JPA(Hibernate) / MyBatis / jOOQ | 한국 금융권은 MyBatis 비중 높음. 컴플라이언스팀이 raw SQL 가시성 요구 가능. |
| 3 | 본인인증 SDK | KMC / NICE / KCB / 통합 | MVP 단계에서는 placeholder. 출시 직전 결정. |

→ 결정 후 본 섹션 갱신. 근거는 `ARCHITECTURE.md` 의사결정 로그.

---

## 3. 문서 지도

루트 ALL_CAPS = 원칙/규칙 (변경 빈도 낮음). `docs/` 하위 = 콘텐츠 (변경 빈도 높음).

| 문서 | 담는 것 | 읽는 시점 |
|---|---|---|
| `AGENTS.md` (본 문서) | LLM 진입점, 철칙 | **모든 작업 시작 시** |
| `ARCHITECTURE.md` | 시스템 구조, FE/BE 통신, 인증 플로우, ADR | 새 기능 설계 시 |
| `docs/DESIGN.md` | 디자인 토큰 SSOT | UI 작업 시 |
| `docs/FRONTEND.md` | FE 컨벤션 (폴더·네이밍·컴포넌트 패턴·API 호출) | FE 코드 작성 시 |
| `docs/BACKEND.md` | BE 컨벤션 (패키지 구조·레이어링·예외·DTO) | BE 코드 작성 시 |
| `docs/API.md` | API 설계 규칙 (네이밍·페이지네이션·에러 포맷·버저닝) | FE/BE 모두, 새 엔드포인트 시 |
| `docs/PRODUCT_SENSE.md` | 제품 판단 휴리스틱 | 디자인/UX 트레이드오프 시 |
| `docs/SECURITY.md` | 보안 규칙 (PII, 인증, 세션, 감사로그) | 인증/데이터 관련 작업 |
| `docs/RELIABILITY.md` | 장애 방지, 로깅, 모니터링 기준 | 운영 코드 작성 시 |
| `docs/QUALITY_SCORE.md` | "이 PR이 머지 가능한가" 체크리스트 | PR 작성 시 |
| `docs/PLANS.md` | exec-plan 작성 방법 메타 | 새 plan 작성 시 |
| `docs/product-specs/*` | 기능별 제품 사양 | 해당 기능 작업 시 |
| `docs/exec-plans/active/*` | 진행 중 실행 계획 | 해당 작업 시 |
| `docs/exec-plans/completed/*` | 완료된 계획 (회고 포함) | 유사 작업 참고 시 |
| `docs/exec-plans/tech-debt-tracker.md` | 기술 부채 등록부 | 작업 종료 시 갱신 |
| `docs/design-docs/*` | 디자인 결정 기록, 스크린샷 reference | UI 작업 검증 시 |
| `docs/references/*-llms.txt` | 도메인 지식 (보험 용어, 외부 라이브러리, Spring/Next 가이드) | 관련 작업 시 |
| `docs/generated/db-schema.md` | DB 스키마 자동 생성 (수동 편집 금지) | 데이터 작업 시 |
| `docs/generated/openapi.yaml` | API 스펙 자동 생성 (BE → FE 컨트랙트) | API 작업 시 |

---

## 4. 컨텍스트 로드 순서

작업 유형별로 다음 순서로 컨텍스트에 로드한다. **목록에 없는 문서는 묻지 말고 로드하지 않는다.**

**프론트엔드 UI 작업**
1. `AGENTS.md`
2. 해당 `docs/exec-plans/active/*.md`
3. `docs/DESIGN.md`
4. `docs/FRONTEND.md`
5. 관련 `docs/product-specs/*.md`
6. 필요 시 `docs/design-docs/screens/*.png`
7. API 호출이 있으면 `docs/generated/openapi.yaml` (해당 엔드포인트 부분)

**백엔드 코드 작업**
1. `AGENTS.md`
2. 해당 `docs/exec-plans/active/*.md`
3. `docs/BACKEND.md`
4. `docs/API.md`
5. `docs/references/insurance-terms-llms.txt`
6. `docs/references/insurance-regulation-llms.txt`
7. `docs/generated/db-schema.md`
8. PII/인증 관련 시 `docs/SECURITY.md`

**API 설계 작업 (FE/BE 양쪽)**
1. `AGENTS.md`
2. `docs/API.md`
3. 해당 `docs/product-specs/*.md`
4. 기존 `docs/generated/openapi.yaml` (충돌·일관성 확인)

**인프라/배포 작업**
1. `AGENTS.md`
2. `ARCHITECTURE.md` (배포 섹션)
3. `docs/RELIABILITY.md`
4. 관련 `docs/references/*-llms.txt`

**제품 의사결정/스펙 작성**
1. `AGENTS.md`
2. `docs/PRODUCT_SENSE.md`
3. `docs/product-specs/index.md`
4. 관련 `docs/references/insurance-*-llms.txt`

---

## 5. 명령어

### 프론트엔드 (`frontend/` 디렉토리에서)

```bash
pnpm dev               # Next.js 개발 서버 (localhost:3000)
pnpm build             # 프로덕션 빌드
pnpm start             # 프로덕션 서버

pnpm typecheck         # tsc --noEmit
pnpm lint              # 린트
pnpm format            # 포맷
pnpm test              # 단위 테스트 (vitest)
pnpm test:e2e          # Playwright E2E
pnpm test:visual       # Playwright 시각 회귀

pnpm api:generate      # ../docs/generated/openapi.yaml → src/api/* 타입/클라이언트 생성
pnpm tokens:check      # DESIGN.md ↔ tailwind config 일관성 검사
```

### 백엔드 (`backend/` 디렉토리에서)

```bash
./gradlew bootRun                # Spring Boot 개발 서버 (localhost:8080)
./gradlew build                  # 빌드 (테스트 포함)
./gradlew test                   # 단위 + 통합 테스트
./gradlew check                  # 정적 분석 + 테스트
./gradlew spotlessApply          # 포맷 (Google Java Format)

./gradlew openApiGenerate        # 소스 → ../docs/generated/openapi.yaml
./gradlew flywayMigrate          # DB 마이그레이션
./gradlew schemaDoc              # ../docs/generated/db-schema.md 재생성
```

### 루트에서 (공통)

```powershell
.\setup.ps1                      # 부트스트랩 (Windows)
Copy-Item .env.example .env      # 첫 셋업 시
```

> dev DB는 **클라우드(Supabase)**를 사용. 세션도 같은 DB에 저장 (Spring Session JDBC). 로컬 컨테이너 불요. `.env`에 연결 정보 박혀 있어야 BE 기동.

---

## 6. 철칙 (절대 깨지 말 것)

LLM이 가장 자주 실수하는 부분. 이 8개는 예외 없음.

1. **`DESIGN.md`를 우회하지 않는다.**
   Tailwind 클래스에 `bg-#…`, `text-[16px]`, `p-[12px]`, `rgb(...)` 같은 raw 값 금지.
   필요한 토큰이 없으면 `DESIGN.md` PR을 먼저 낸다.

2. **API 컨트랙트는 백엔드가 진실의 원천이다.**
   `docs/generated/openapi.yaml`은 백엔드 소스에서 생성된다. 프론트엔드 타입은 이로부터 생성한다.
   FE에서 BE 응답 shape을 임의로 정의하지 않는다. BE 변경 없이 FE에서 새 필드를 가정하는 코드 금지.

3. **mock 데이터로 도메인 용어를 임의 생성하지 않는다.**
   보험 용어·상품명·규제 문구는 반드시 `docs/references/insurance-terms-llms.txt`에서 가져온다.
   목록에 없으면 거기에 먼저 추가.

4. **한국어 콘텐츠로 검증한다.**
   "Lorem ipsum"으로 레이아웃만 보고 끝내지 않는다. 실제 한글 placeholder/본문으로 렌더링 확인.
   글자 수 edge case fixture는 `frontend/tests/fixtures/korean-edge-cases.ts`.

5. **`generated/` 폴더는 수동 편집 금지.**
   스크립트로만 갱신. 수동 편집 PR은 자동 거부.

6. **PII는 로그·에러 메시지·URL 쿼리스트링에 남기지 않는다.**
   고객명, 주민번호, 보험증권번호, 계약번호 — 절대 노출 금지.
   `docs/SECURITY.md`의 PII 정의 표 참고. Spring 측에서는 `@JsonIgnore`·로깅 마스킹 필터 적용.

7. **exec-plan 없이 기능 코드 작성 금지.**
   모든 기능 작업은 `docs/exec-plans/active/`의 plan을 갖는다.
   plan 없으면 plan부터 작성 (`docs/PLANS.md` 따름).
   *예외*: 한 줄 오타 수정, README 문구 수정 등은 plan 불요.

8. **작업 완료 시 plan을 `completed/`로 이동**하고 회고 섹션을 채운다.
   발견한 기술 부채는 `tech-debt-tracker.md`에 등록.

---

## 7. 작업 워크플로

```
[작업 의뢰]
   ↓
[exec-plan 확인 또는 작성]    ← docs/PLANS.md 따라 작성
   ↓
[결정사항(TBD/[결정 필요]) 확인]
   ↓
TBD 남아 있음? ─── Yes ──→ 사용자에게 질문 → plan에 결정 기록
   │
   No
   ↓
[FE/BE 분기]
   ├─ FE 작업: 컨텍스트 로드(UI 순서) → BE의 OpenAPI 확인
   └─ BE 작업: 컨텍스트 로드(BE 순서) → API.md 따라 엔드포인트 설계
   ↓
[작은 단위로 작업 + PR 분리]
   ↓
[QUALITY_SCORE.md 체크리스트 통과]
   ↓
[plan을 completed/로 이동 + 회고 작성]
   ↓
[tech debt 등록]
```

---

## 8. 막혔을 때

| 상황 | 행동 |
|---|---|
| 결정사항이 명확하지 않음 | 사용자에게 질문. 추측해서 코드 작성 금지. |
| 디자인이 모호함 | `docs/design-docs/screens/` reference 확인 → 없으면 사용자에게 질문. |
| API shape이 모호함 | `docs/generated/openapi.yaml` 확인 → 없으면 `docs/API.md` 따라 BE 먼저 정의. |
| 도메인 용어가 헷갈림 | `docs/references/insurance-terms-llms.txt` 확인 → 없으면 추가 PR 먼저. |
| 빌드/테스트 실패 | 에러 그대로 사용자에게 공유. 임의 우회 금지. |
| 본 문서와 다른 문서가 충돌 | `AGENTS.md` 우선. 충돌 자체를 사용자에게 보고. |

---

## 9. 본 문서 변경 정책

- 본 문서는 **변경 빈도가 낮아야 한다**. 자주 바뀌는 내용은 하위 문서로 위임.
- 변경 시 PR description에 *왜 AGENTS.md 수준의 변경인지* 명시.
- 2장(기술 스택), 3장(문서 지도)의 변경은 `ARCHITECTURE.md` 의사결정 로그에도 기재.
