# Security Hardening — SSR Auth Guard + JPA Auditing

- **Date**: 2026-05-20
- **Author**: <hj8279@coocon.net>
- **Status**: Draft

## Background

`fieldarena2` 의 보안 점검에서 두 가지 갭이 확인됨:

1. **인증 게이트가 클라이언트 전용** — 모든 보호 페이지(`/dashboard`, `/customers`, `/contracts`, `/claims`, `/proposals`, `/underwriting`, `/admin/*`)는 `"use client"` 컴포넌트인 `_auth-guard` 로만 보호된다. 비인증 사용자도 HTML/JS 번들을 일단 수신한 뒤 클라이언트에서 `/login` 으로 리다이렉트되므로, 라우트 구조와 UI 코드가 노출된다.
2. **변경 감사 이력 부재** — PII 컬럼은 AES-256-GCM 으로 암호화되어 있으나, "누가 언제 PII 행을 생성·수정했는가"는 추적되지 않는다. `User.createdAt` 외에 audit 컬럼이 전무하며 JPA Auditing 도 활성화되어 있지 않다.

세 번째 항목(Route Handler 기반 명시 프록시)은 이미 `next.config.ts` 의 `rewrites()` 가 백엔드 URL 을 가리고 있어 ROI 가 낮다고 판단해 스코프에서 제외한다.

## Goals

- 비인증 사용자가 보호 페이지의 HTML 응답을 받기 전에 `/login` 으로 302 리다이렉트시킨다.
- 인증되었으나 권한이 부족한 사용자(예: AGENT2 가 `/proposals` 접근)는 `/dashboard` 로 302 리다이렉트시킨다.
- 5개 도메인 엔티티(User, Customer, Claim, Policy, Proposal)에 `created_at`, `created_by`, `updated_at`, `updated_by` 4 audit 컬럼을 추가하고 JPA Auditing 으로 자동 채운다.
- 기존 시드/존재 행은 `SYSTEM` sentinel 로 백필한다.
- 기존 클라이언트 가드(`_auth-guard`), 세션 메커니즘(Spring Session JDBC), CSRF 흐름, `next.config.ts` 의 `rewrites()` 는 그대로 유지한다.

## Non-Goals

- Route Handler 기반 명시 프록시 도입 (현 `rewrites` 유지).
- 페이지의 CSR → SSR/SSG 전환.
- JPA 엔티티 관계 모델 도입(`@OneToMany`, `@ManyToOne` 등).
- FSD `views`/`pages` 레이어 신설.
- 세션 메커니즘 변경 (JWT 전환 등).
- 감사 로그용 별도 audit 테이블 / 이벤트 스트림.

## Approach

### Branching

- 베이스: `master`
- PR1(프런트): `feat/ssr-auth-guard`
- PR2(백엔드): `feat/jpa-auditing`
- 두 PR 독립 — 어느 쪽 먼저 머지해도 무방.

---

## PR1 — Next.js SSR Auth Guard

### Architecture

- 신규 파일: `frontend/src/middleware.ts`
- Next.js Edge/Node middleware 가 보호 라우트 진입 시 백엔드의 `/api/auth/me` 로 세션 검증
- 미인증/권한위반 시 즉시 302 응답 → HTML/JS 번들 전송 차단
- 클라이언트의 `_auth-guard` 는 유지 (다중 방어, role 변경·세션 만료 후속 처리)

### Matcher

```ts
export const config = {
  matcher: [
    "/dashboard/:path*",
    "/customers/:path*",
    "/contracts/:path*",
    "/claims/:path*",
    "/proposals/:path*",
    "/underwriting/:path*",
    "/admin/:path*",
  ],
};
```

`/login`, `/register`, `/pending`, `/api/*`, `/_next/*`, `/`, 정적 자원은 매처에서 제외.

### Session Validation Flow

1. `req.cookies.get("AGENT_SESSION")` 부재 → `/login` 으로 302
2. 존재 시 `fetch(${BACKEND_URL}/api/auth/me, { headers: { cookie: req.headers.cookie } })` 호출
3. 200 이 아니면 → `/login` 으로 302 (만료/무효 세션)
4. 200 이면 `MeResponse.role` 추출 → 페이지별 role 매핑 확인:
   - `/admin/*` → `ADMIN`
   - `/proposals/*`, `/underwriting/*` → `ADMIN | AGENT1`
   - `/customers/*`, `/claims/*`, `/contracts/*`, `/dashboard/*` → `ADMIN | AGENT1 | AGENT2`
5. role 위반 시 → `/dashboard` 로 302

### Environment

- `BACKEND_URL` 은 이미 `next.config.ts` 에서 사용 중인 서버 전용 env 를 재사용. middleware 도 동일 변수를 읽는다.
- middleware 는 Node runtime 으로 동작 (`export const runtime = "nodejs"`) — fetch 시 쿠키 forward 안정성 확보.

### Backward Compatibility

- `_auth-guard` 컴포넌트는 그대로 유지.
- Middleware 가 추가됨에 따라 첫 진입 시점의 가드만 서버로 이동.
- 세션 만료가 페이지 사용 중 발생하는 경우는 기존 401 → `/login` 리다이렉트 흐름([frontend/src/shared/api/csrf.ts:36-40](frontend/src/shared/api/csrf.ts#L36))이 그대로 동작.

### PR1 Verification

- 로그아웃 상태에서 `/dashboard` 직접 URL 접근 → 302 to `/login`. DevTools Network 탭에서 `/dashboard` HTML 응답이 전송되지 않는 것을 확인.
- AGENT2 계정으로 `/proposals` 접근 → 302 to `/dashboard`.
- `/login`, `/register`, `/pending` 은 비인증으로 정상 진입.
- `/api/*` 요청은 middleware 비통과 → 기존 rewrites 가 백엔드로 프록시.
- 정상 로그인 후 `/dashboard` → 200.

### PR1 Rollback

`frontend/src/middleware.ts` 삭제 또는 매처 비활성화 한 줄 변경으로 이전 상태 복원. 클라이언트 `_auth-guard` 가 가드 역할 복원.

### PR1 Trade-offs

- 보호 페이지 첫 진입마다 백엔드 `/me` 라운드트립 1회 추가(약 10-50 ms). Spring Session JDBC 조회 부하가 페이지 진입 수만큼 발생.
- middleware 의 Node runtime 사용은 Vercel/Edge 환경에서 cold start 영향이 있을 수 있음 — 현 배포 환경은 Node runtime 사용으로 충분하다고 판단.

---

## PR2 — JPA Auditing

### Architecture

- `BaseAuditEntity` (MappedSuperclass) 도입 → 5개 엔티티가 extend
- `@EnableJpaAuditing` + `AuditorAware<String>` 빈으로 자동 채움
- Flyway V13 마이그레이션으로 누락 컬럼 추가 + 기존 행 백필

### Files

#### Create

- `backend/src/main/java/com/agentsupport/common/BaseAuditEntity.java`
- `backend/src/main/java/com/agentsupport/common/AuditorAwareImpl.java`
- `backend/src/main/resources/db/migration/V13__add_audit_columns.sql`
- `backend/src/test/java/com/agentsupport/common/AuditorAwareImplTest.java`

#### Modify

- `backend/src/main/java/com/agentsupport/BackendApplication.java` — `@EnableJpaAuditing(auditorAwareRef = "auditorAware")` 추가
- `backend/src/main/java/com/agentsupport/user/entity/User.java` — `BaseAuditEntity` extend, 기존 `@CreationTimestamp createdAt` 제거 (베이스 클래스로 흡수)
- `backend/src/main/java/com/agentsupport/customer/entity/Customer.java` — 동일
- `backend/src/main/java/com/agentsupport/claim/entity/Claim.java` — 동일
- `backend/src/main/java/com/agentsupport/policy/entity/Policy.java` — 동일
- `backend/src/main/java/com/agentsupport/proposal/entity/Proposal.java` — 동일

### `BaseAuditEntity`

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseAuditEntity {
  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  protected LocalDateTime createdAt;

  @CreatedBy
  @Column(name = "created_by", nullable = false, updatable = false, length = 50)
  protected String createdBy;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  protected LocalDateTime updatedAt;

  @LastModifiedBy
  @Column(name = "updated_by", nullable = false, length = 50)
  protected String updatedBy;

  public LocalDateTime getCreatedAt() { return createdAt; }
  public String getCreatedBy() { return createdBy; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public String getUpdatedBy() { return updatedBy; }
}
```

### `AuditorAwareImpl`

```java
@Component("auditorAware")
public class AuditorAwareImpl implements AuditorAware<String> {
  @Override
  public Optional<String> getCurrentAuditor() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
      return Optional.of("SYSTEM");
    }
    return Optional.of(auth.getName());
  }
}
```

`SYSTEM` sentinel 은 회원가입(`RegisterController` — 미인증 컨텍스트에서 User insert), 시드 데이터, 백그라운드 작업을 커버한다.

### Flyway V13

기존 마이그레이션 확인 결과(현황):

| 테이블     | created_at | updated_at | 비고                              |
| ---------- | ---------- | ---------- | --------------------------------- |
| users      | ✅ (V10)   | ❌         | created_at 만 존재                |
| customers  | ✅ (V11)   | ✅ (V11)   |                                   |
| policies   | ✅ (V2)    | ✅ (V2)    |                                   |
| proposals  | ✅ (V5)    | ✅ (V5)    |                                   |
| claims     | ✅ (V5)    | ✅ (V5)    |                                   |

- 컬럼 타입은 `TIMESTAMP` (V8이 모든 timestamp 를 `TIMESTAMP` KST naive 로 통일).
- V13 은 누락 컬럼만 ADD: 5개 테이블 모두에 `created_by`, `updated_by` 추가, `users` 에 한해 `updated_at` 추가.

```sql
-- V13__add_audit_columns.sql

-- users: created_at 이미 존재. updated_at, created_by, updated_by 추가
ALTER TABLE users
  ADD COLUMN updated_at TIMESTAMP,
  ADD COLUMN created_by VARCHAR(50),
  ADD COLUMN updated_by VARCHAR(50);

-- customers/policies/proposals/claims: created_at, updated_at 이미 존재. created_by, updated_by 만 추가
ALTER TABLE customers
  ADD COLUMN created_by VARCHAR(50),
  ADD COLUMN updated_by VARCHAR(50);

ALTER TABLE policies
  ADD COLUMN created_by VARCHAR(50),
  ADD COLUMN updated_by VARCHAR(50);

ALTER TABLE proposals
  ADD COLUMN created_by VARCHAR(50),
  ADD COLUMN updated_by VARCHAR(50);

ALTER TABLE claims
  ADD COLUMN created_by VARCHAR(50),
  ADD COLUMN updated_by VARCHAR(50);

-- Backfill
UPDATE users     SET updated_at = COALESCE(created_at, (now() AT TIME ZONE 'Asia/Seoul')) WHERE updated_at IS NULL;
UPDATE users     SET created_by = 'SYSTEM', updated_by = 'SYSTEM' WHERE created_by IS NULL;
UPDATE customers SET created_by = 'SYSTEM', updated_by = 'SYSTEM' WHERE created_by IS NULL;
UPDATE policies  SET created_by = 'SYSTEM', updated_by = 'SYSTEM' WHERE created_by IS NULL;
UPDATE proposals SET created_by = 'SYSTEM', updated_by = 'SYSTEM' WHERE created_by IS NULL;
UPDATE claims    SET created_by = 'SYSTEM', updated_by = 'SYSTEM' WHERE created_by IS NULL;

-- Enforce NOT NULL
ALTER TABLE users
  ALTER COLUMN updated_at SET NOT NULL,
  ALTER COLUMN created_by SET NOT NULL,
  ALTER COLUMN updated_by SET NOT NULL;
ALTER TABLE customers ALTER COLUMN created_by SET NOT NULL, ALTER COLUMN updated_by SET NOT NULL;
ALTER TABLE policies  ALTER COLUMN created_by SET NOT NULL, ALTER COLUMN updated_by SET NOT NULL;
ALTER TABLE proposals ALTER COLUMN created_by SET NOT NULL, ALTER COLUMN updated_by SET NOT NULL;
ALTER TABLE claims    ALTER COLUMN created_by SET NOT NULL, ALTER COLUMN updated_by SET NOT NULL;

-- users.updated_at 기본값(향후 raw INSERT 안전망)
ALTER TABLE users ALTER COLUMN updated_at SET DEFAULT (now() AT TIME ZONE 'Asia/Seoul');
```

### Entity Migration Pattern

기존 `User.java`:

```java
@Entity
@Table(name = "users")
public class User {
  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
  // ...
  public LocalDateTime getCreatedAt() { return createdAt; }
}
```

변경 후:

```java
@Entity
@Table(name = "users")
public class User extends BaseAuditEntity {
  // createdAt 필드와 getter 제거 — BaseAuditEntity 가 제공
}
```

다른 엔티티도 동일하게 `extends BaseAuditEntity` 추가, 기존 `createdAt`/`@CreationTimestamp` 가 있으면 제거.

### Atomicity Constraint

`spring.jpa.hibernate.ddl-auto: validate` 이므로 컬럼 누락 시 부팅 실패. → Flyway V13 + 엔티티 변경 + `BaseAuditEntity` + `AuditorAware` + `@EnableJpaAuditing` 은 **단일 PR 안에서 원자적으로** 적용되어야 한다. 중간 커밋도 부팅 가능해야 함.

### PR2 Verification

- `mvn test` 통과 (모든 통합 테스트)
- `AuditorAwareImplTest` — 인증 컨텍스트 유/무 분기 검증
- `CustomerControllerTest` 의 POST/PATCH 결과에서 `created_by`/`updated_by` 가 테스트 사용자 ID 로 채워졌는지 검증 보강
- 로컬 PostgreSQL 에서 Flyway 마이그레이션 dry-run 후 5개 테이블 모두 4 컬럼 NOT NULL 상태 확인
- 시드 데이터 행(V3, V6) 이 `SYSTEM` 으로 백필되었는지 SELECT 확인

### PR2 Rollback

- Flyway 는 down 마이그레이션을 작성하지 않음. 롤백 필요 시 V14 로 컬럼 DROP 마이그레이션 추가하는 방식.
- V13 자체는 안전 — 컬럼 ADD → 백필 → NOT NULL 순서로 부분 실패 시 nullable 컬럼만 남는 안전 상태.

### PR2 Trade-offs

- 기존 `User.createdAt` 의 `@CreationTimestamp`(Hibernate) → `@CreatedDate`(Spring Data) 로 변경됨. 동작은 동일.
- `updatedAt` 이 모든 SAVE/UPDATE 에서 갱신되어, 변경 없는 SAVE 도 timestamp 만 갱신될 수 있음 (Hibernate dirty-check 으로 일반적으로 회피됨).

---

## Testing Strategy

| 영역              | 테스트                                                          |
| ----------------- | --------------------------------------------------------------- |
| PR1 매처 동작     | 로그아웃 상태 보호 페이지 접근 → 302. 정적 자원·`/api/*` 우회   |
| PR1 role 가드     | AGENT2 가 `/proposals` 접근 → `/dashboard`                      |
| PR1 세션 만료     | 만료된 `AGENT_SESSION` 쿠키로 접근 → `/login`                   |
| PR2 AuditorAware  | 인증 컨텍스트 유/무 unit test                                   |
| PR2 통합          | `CustomerControllerTest` 등에서 created_by/updated_by 확인      |
| PR2 마이그레이션  | 로컬 PG dry-run, 기존 행 `SYSTEM` 백필 확인                     |

## Open Questions

없음 (브레인스토밍에서 모두 해소).

## References

- 분석 근거: 2026-05-20 채팅 세션의 보안 진단
- 기존 관련 마이그레이션: `backend/src/main/resources/db/migration/V8__convert_timestamps_to_kst.sql` (timestamp 컬럼 KST timestamptz 통일)
- 기존 가드: [frontend/src/app/_auth-guard/index.tsx](../../../frontend/src/app/_auth-guard/index.tsx)
- 기존 프록시: [frontend/next.config.ts](../../../frontend/next.config.ts) (rewrites)
