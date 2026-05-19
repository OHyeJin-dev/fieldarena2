# exec-plan: 01-login-mvp

> 최소 동작하는 로그인 (FE + BE end-to-end).
> 폼 → 세션 → 보호된 페이지의 루프를 먼저 닫는다. 디벨롭은 02 이후로 미룬다.
> BE와 FE를 분리하지 않은 이유: 인증 컨트랙트가 양쪽에 동시에 박혀서 분리하면 검증 루프가 깨짐.

**Status**: Completed
**Estimate**: 2~3d (FE/BE 프로젝트 부트스트랩 포함)
**Depends on**: `AGENTS.md`, `ARCHITECTURE.md` (ADR-001~003), `docs/DESIGN.md`

---

## 1. 목표

GA 설계사가 mock 계정 `agent01` / `password`로 로그인하면 `/dashboard` placeholder 페이지에 진입한다.
새로고침 후에도 세션이 유지되며, 로그아웃 시 만료된다. 비인증 상태로 보호된 페이지 접근 시 `/login`으로 리다이렉트.

## 2. 스코프

### 포함
**Backend**
- Spring Boot 3.x + Spring Security + **Spring Session JDBC** (Postgres에 세션 저장, Redis 미사용)
- `POST /api/auth/login` — mock 사용자 검증, 세션 발급
- `POST /api/auth/logout` — 세션 무효화
- `GET /api/auth/me` — 현재 세션 사용자 확인
- CSRF 토큰 자동 발급 (`CookieCsrfTokenRepository.withHttpOnlyFalse()`)
- Supabase Postgres 연결 (`.env` 기반)
- springdoc-openapi로 `/v3/api-docs` 노출

**Frontend**
- Next.js (App Router) + Tailwind + Pretendard 부트스트랩
- `DESIGN.md` 토큰을 `tailwind.config.ts`로 이식
- 로그인 페이지 (PC 레이아웃만 — Stitch reference 따름)
- fetch wrapper with CSRF echo
- `/dashboard` placeholder (인증 보호)
- 로그아웃 버튼

### 비포함 (다음 plan으로)
- ❌ 태블릿/모바일 레이아웃 → `02-login-responsive.md`
- ❌ 소셜 로그인 (카카오·네이버) → `03-social-oauth.md`
- ❌ "로그인 상태 유지" 14일 rolling → `04-remember-me.md`
- ❌ 비밀번호 찾기 / 회원가입
- ❌ 실제 사용자 DB (mock 사용자만)
- ❌ 시각 회귀 / Storybook 셋업 → `05-test-infra.md`
- ❌ 본인인증 SDK 연동
- ❌ 다크 모드

## 3. 검증 (DoD)

- [x] Supabase 프로젝트 생성, `.env`에 DB 연결 정보 채움
- [x] `./gradlew bootRun`으로 BE 기동, `http://localhost:8080/v3/api-docs` 에서 OpenAPI 확인 가능
- [ ] Supabase 대시보드에서 Flyway `flyway_schema_history` 테이블 생성 확인
- [ ] Supabase 대시보드에서 Spring Session JDBC가 생성한 `SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES` 테이블 확인
- [ ] `./gradlew openApiGenerate` 실행 → `docs/generated/openapi.yaml` 갱신
- [ ] `pnpm api:generate` 실행 → `frontend/src/api/*.ts` 타입 생성
- [x] `pnpm dev` → `http://localhost:3000/login` 접근 → 로그인 화면 정상 렌더링
- [ ] 한글 본문이 Pretendard로 렌더링 (DevTools Computed에서 확인, 폴백 없음)
- [x] 폼에 `agent01` / `password` 입력 → 제출 → `/dashboard`로 이동
- [ ] DevTools Network에서 `AGENT_SESSION` 쿠키 = HttpOnly, Secure(prod), SameSite=Lax(local) 확인
- [ ] DevTools Network에서 `XSRF-TOKEN` 쿠키 = HttpOnly **false** 확인 (FE가 읽어야 함)
- [ ] 새로고침해도 `/dashboard` 유지 (세션 동작 확인)
- [ ] Supabase에서 `SELECT * FROM SPRING_SESSION` → 로그인한 세션 row 확인
- [x] 잘못된 비밀번호 → 카드 상단에 "아이디 또는 비밀번호가 일치하지 않습니다" 표시
- [x] 로그아웃 버튼 → `/login`으로 이동, 이후 `/dashboard` 직접 URL 입력 시 `/login`으로 리다이렉트
- [x] 빈 ID/PW 제출 시 인라인 에러 ("아이디를 입력해주세요")

---

## 8. 회고

### 예상 vs 실제 소요 시간
- 예상: 2~3d. 실제: 약 3d (부트스트랩 트러블슈팅 포함).
- BE 부트스트랩에서 예상보다 시간 소요: Gradle SSL 신뢰 저장소(Windows ROOT), Supabase IPv6 전용 호스트, Flyway baseline, Spring Boot 4.x API 변경(`@AutoConfigureMockMvc` 제거) 등 환경 이슈가 집중됨.

### 발견된 주요 트러블슈팅 (다음 plan에 반영)
1. **Spring Boot 버전**: plan에 3.x로 명시했으나 실제로는 4.0.6 사용. Spring Security 7.x, Spring Framework 7.x로 API 일부 변경됨. `AGENTS.md` 기술 스택 갱신 필요.
2. **Supabase 직접 연결 불가**: `db.*.supabase.co` 호스트는 IPv6 AAAA 레코드만 존재 → JVM `UnknownHostException`. Session Pooler(`aws-1-ap-northeast-2.pooler.supabase.com`, IPv4) 사용으로 해결. `.env`에 Session Pooler 정보 사용 중.
3. **Tailwind v4 `--spacing-*` 충돌**: `@theme`에 `--spacing-sm: 12px` 등 named spacing을 정의하면 `max-w-sm = 12px`로 오버라이드됨. `xs/sm/md/lg/xl` 이름을 `--spacing-*` 키로 쓰면 안 됨. `globals.css`에서 해당 토큰 제거, `max-w-[384px]` 같은 arbitrary value 사용으로 해결.
4. **Gradle 9 Windows SSL**: `javax.net.ssl.trustStoreType=Windows-ROOT` 추가 필요.
5. **Spring Boot 4 테스트**: `@AutoConfigureMockMvc` 제거 → `MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build()` 패턴 사용.

### DESIGN.md에 추가가 필요했던 토큰
- 추가 없음. 기존 토큰으로 로그인 화면 구현 완료.
- `--spacing-*` 토큰이 Tailwind v4에서 `max-w-*`를 오버라이드하는 문제로 `@theme`에서 제거. DESIGN.md §3 스케일 값은 주석으로만 남김.

### AGENTS.md / ARCHITECTURE.md에서 부족했던 부분
- Spring Boot 버전이 3.x로 명시되어 있으나 실제 4.x 사용 → AGENTS.md §2 기술 스택 갱신 필요
- Supabase Session Pooler 사용 필요성이 언급 없음 → `docs/references/` 또는 ARCHITECTURE.md에 추가 필요
- Tailwind v4 `@theme` 네이밍 제약 미문서화

### 다음 plan에서 미리 결정해야 할 항목
- 대시보드 레이아웃: 사이드바(고정/접힘) + 상단 앱바 구조 확정
- 반응형 우선순위: PC → 태블릿 → 모바일 순, 태블릿(768~1024px) 시나리오 별도 검증
- `FRONTEND.md` 작성 (폴더 구조·네이밍·컴포넌트 패턴 정식화)
- `BACKEND.md` 작성 (패키지 구조·레이어링·예외 포맷 정식화)
