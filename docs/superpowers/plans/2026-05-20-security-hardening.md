# Security Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Next.js middleware SSR auth guard (PR1) and JPA Auditing across all 5 domain entities (PR2) to close two security gaps in `fieldarena2`.

**Architecture:** PR1 introduces `frontend/src/middleware.ts` that intercepts protected routes, validates the session by calling backend `/api/auth/me`, and enforces role-based access — preventing unauthenticated users from receiving protected HTML/JS bundles. PR2 introduces `BaseAuditEntity` + `AuditorAwareImpl` + `@EnableJpaAuditing`, adds missing `created_by`/`updated_by`/`updated_at` columns via Flyway V13, and migrates all 5 entities to use Spring Data JPA audit annotations.

**Tech Stack:** Next.js 16 (App Router, Node runtime), TypeScript, Spring Boot 3, Spring Data JPA, PostgreSQL, Flyway, JUnit 5 + MockMvc.

**Spec:** [docs/superpowers/specs/2026-05-20-security-hardening-design.md](../specs/2026-05-20-security-hardening-design.md)

**Branches:** Both branched from `master`.
- PR1: `feat/ssr-auth-guard`
- PR2: `feat/jpa-auditing`

The two PRs are independent — work order does not matter.

---

## PR1 — Next.js SSR Auth Guard

### Task 1: Create branch for PR1

**Files:** (no file changes)

- [ ] **Step 1: Create and switch to branch**

```bash
git checkout master
git pull origin master
git checkout -b feat/ssr-auth-guard
```

- [ ] **Step 2: Verify clean working tree**

```bash
git status
```

Expected: `On branch feat/ssr-auth-guard ... nothing to commit, working tree clean`

---

### Task 2: Implement `middleware.ts`

**Files:**
- Create: `frontend/src/middleware.ts`

- [ ] **Step 1: Create the middleware file**

Write `frontend/src/middleware.ts`:

```ts
import { NextRequest, NextResponse } from "next/server";

export const runtime = "nodejs";

const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";
const SESSION_COOKIE = process.env.SESSION_COOKIE_NAME ?? "AGENT_SESSION";

type Role = "ADMIN" | "AGENT1" | "AGENT2";

const ROLE_RULES: Array<{ prefix: string; allow: Role[] }> = [
  { prefix: "/admin", allow: ["ADMIN"] },
  { prefix: "/proposals", allow: ["ADMIN", "AGENT1"] },
  { prefix: "/underwriting", allow: ["ADMIN", "AGENT1"] },
  { prefix: "/customers", allow: ["ADMIN", "AGENT1", "AGENT2"] },
  { prefix: "/claims", allow: ["ADMIN", "AGENT1", "AGENT2"] },
  { prefix: "/contracts", allow: ["ADMIN", "AGENT1", "AGENT2"] },
  { prefix: "/dashboard", allow: ["ADMIN", "AGENT1", "AGENT2"] },
];

function redirectToLogin(req: NextRequest): NextResponse {
  const url = req.nextUrl.clone();
  url.pathname = "/login";
  url.search = "";
  return NextResponse.redirect(url);
}

function redirectToDashboard(req: NextRequest): NextResponse {
  const url = req.nextUrl.clone();
  url.pathname = "/dashboard";
  url.search = "";
  return NextResponse.redirect(url);
}

export async function middleware(req: NextRequest): Promise<NextResponse> {
  const sessionCookie = req.cookies.get(SESSION_COOKIE);
  if (!sessionCookie) return redirectToLogin(req);

  const cookieHeader = req.headers.get("cookie") ?? "";

  let meResponse: Response;
  try {
    meResponse = await fetch(`${BACKEND_URL}/api/auth/me`, {
      headers: { cookie: cookieHeader },
      cache: "no-store",
    });
  } catch {
    return redirectToLogin(req);
  }

  if (meResponse.status !== 200) return redirectToLogin(req);

  const body = (await meResponse.json()) as { role: Role };
  const role = body.role;

  const matched = ROLE_RULES.find((rule) =>
    req.nextUrl.pathname.startsWith(rule.prefix),
  );
  if (matched && !matched.allow.includes(role)) {
    return redirectToDashboard(req);
  }

  return NextResponse.next();
}

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

- [ ] **Step 2: Type-check**

```bash
cd frontend && pnpm typecheck
```

Expected: No errors.

- [ ] **Step 3: Lint**

```bash
cd frontend && pnpm lint
```

Expected: No errors.

- [ ] **Step 4: FSD lint**

```bash
cd frontend && pnpm lint:fsd
```

Expected: `No problems found!`

- [ ] **Step 5: Build sanity check**

```bash
cd frontend && pnpm build
```

Expected: Build succeeds. Middleware should appear in output (`ƒ Middleware` or similar).

- [ ] **Step 6: Commit**

```bash
git add frontend/src/middleware.ts
git commit -m "feat(frontend): add SSR auth guard via Next.js middleware

Intercepts protected routes (/dashboard, /customers, /contracts, /claims,
/proposals, /underwriting, /admin) before HTML is sent. Validates session
via backend /api/auth/me and enforces role-based access. Unauthenticated
users redirect to /login; role violations redirect to /dashboard.

Closes spec section 'PR1' from
docs/superpowers/specs/2026-05-20-security-hardening-design.md"
```

---

### Task 3: Manual verification of PR1

**Files:** (no file changes)

This task is manual — start both backend and frontend dev servers and verify the matrix below. Do NOT mark complete until every row passes.

- [ ] **Step 1: Start backend**

```bash
cd backend && ./mvnw spring-boot:run
```

Wait for `Started BackendApplication`. Leave running.

- [ ] **Step 2: Start frontend dev server**

In a second terminal:

```bash
cd frontend && pnpm dev
```

Wait for `Ready in ...`. Leave running.

- [ ] **Step 3: Logged-out → protected route**

Open private/incognito browser window. Navigate to `http://localhost:3000/dashboard`.

Expected: Browser URL changes to `http://localhost:3000/login`. DevTools Network tab: `/dashboard` returns 302 (Location: /login) **without** delivering the dashboard HTML.

- [ ] **Step 4: Logged-out → public routes**

Navigate to `/login`, `/register`, `/pending`. Expected: All load normally with 200.

- [ ] **Step 5: Login + dashboard**

Login as `admin` / `Admin1234!`. Navigate to `/dashboard`. Expected: 200 with dashboard rendered.

- [ ] **Step 6: AGENT2 role guard**

Register a new agent, login as ADMIN, approve as AGENT2 (`/admin/users` page). Logout, login as the AGENT2.

Navigate to `/proposals`. Expected: 302 → `/dashboard`. Same for `/underwriting`.

Navigate to `/admin/users`. Expected: 302 → `/dashboard`.

- [ ] **Step 7: Static assets bypass**

Open DevTools, reload `/dashboard` while logged in. Expected: `/_next/static/*` requests succeed (200) without going through middleware — i.e. no extra `/api/auth/me` call per asset.

- [ ] **Step 8: Stale session**

While logged in, manually delete the `AGENT_SESSION` cookie via DevTools. Reload `/dashboard`. Expected: 302 → `/login`.

- [ ] **Step 9: Stop dev servers and commit notes**

Stop both servers (Ctrl+C). No additional commit — verification only.

---

## PR2 — JPA Auditing

### Task 4: Create branch for PR2

**Files:** (no file changes)

- [ ] **Step 1: Create and switch to branch**

```bash
git checkout master
git pull origin master
git checkout -b feat/jpa-auditing
```

- [ ] **Step 2: Verify clean working tree**

```bash
git status
```

Expected: `On branch feat/jpa-auditing ... nothing to commit, working tree clean`

---

### Task 5: Write `AuditorAwareImpl` test (TDD)

**Files:**
- Create: `backend/src/test/java/com/agentsupport/common/AuditorAwareImplTest.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/agentsupport/common/AuditorAwareImplTest.java`:

```java
package com.agentsupport.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class AuditorAwareImplTest {

  private final AuditorAwareImpl auditorAware = new AuditorAwareImpl();

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void returns_SYSTEM_when_no_authentication() {
    SecurityContextHolder.clearContext();
    Optional<String> result = auditorAware.getCurrentAuditor();
    assertTrue(result.isPresent());
    assertEquals("SYSTEM", result.get());
  }

  @Test
  void returns_SYSTEM_when_anonymous_authentication() {
    SecurityContextHolder.getContext().setAuthentication(
        new AnonymousAuthenticationToken(
            "key",
            "anonymousUser",
            AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
    Optional<String> result = auditorAware.getCurrentAuditor();
    assertEquals("SYSTEM", result.get());
  }

  @Test
  void returns_username_when_authenticated() {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(
            "ohyejin",
            "password",
            AuthorityUtils.createAuthorityList("ROLE_AGENT1")));
    Optional<String> result = auditorAware.getCurrentAuditor();
    assertEquals("ohyejin", result.get());
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd backend && ./mvnw test -Dtest=AuditorAwareImplTest
```

Expected: FAIL with compilation error `cannot find symbol: class AuditorAwareImpl`.

---

### Task 6: Implement `AuditorAwareImpl`

**Files:**
- Create: `backend/src/main/java/com/agentsupport/common/AuditorAwareImpl.java`

- [ ] **Step 1: Write minimal implementation**

Create `backend/src/main/java/com/agentsupport/common/AuditorAwareImpl.java`:

```java
package com.agentsupport.common;

import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("auditorAware")
public class AuditorAwareImpl implements AuditorAware<String> {

  @Override
  public Optional<String> getCurrentAuditor() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null
        || !auth.isAuthenticated()
        || "anonymousUser".equals(auth.getPrincipal())) {
      return Optional.of("SYSTEM");
    }
    return Optional.of(auth.getName());
  }
}
```

- [ ] **Step 2: Run test to verify it passes**

```bash
cd backend && ./mvnw test -Dtest=AuditorAwareImplTest
```

Expected: PASS — all 3 tests green.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/agentsupport/common/AuditorAwareImpl.java \
        backend/src/test/java/com/agentsupport/common/AuditorAwareImplTest.java
git commit -m "feat(backend): add AuditorAwareImpl for JPA auditing

Returns SYSTEM sentinel for unauthenticated/anonymous contexts (covers
registration flow and seed data) and the authenticated user's name
otherwise. Component name 'auditorAware' will be referenced from
@EnableJpaAuditing in a later commit."
```

---

### Task 7: Create `BaseAuditEntity`

**Files:**
- Create: `backend/src/main/java/com/agentsupport/common/BaseAuditEntity.java`

- [ ] **Step 1: Write the MappedSuperclass**

Create `backend/src/main/java/com/agentsupport/common/BaseAuditEntity.java`:

```java
package com.agentsupport.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

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

- [ ] **Step 2: Compile sanity**

```bash
cd backend && ./mvnw compile
```

Expected: BUILD SUCCESS. (No entity uses this yet, so no validation impact.)

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/agentsupport/common/BaseAuditEntity.java
git commit -m "feat(backend): add BaseAuditEntity MappedSuperclass

Provides createdAt/createdBy/updatedAt/updatedBy fields with Spring Data
@CreatedDate/@LastModifiedDate auditing annotations. Entities will extend
this class in a subsequent commit (atomic with Flyway V13 migration to
keep ddl-auto=validate happy)."
```

---

### Task 8: Write Flyway V13 migration

**Files:**
- Create: `backend/src/main/resources/db/migration/V13__add_audit_columns.sql`

- [ ] **Step 1: Write the SQL**

Create `backend/src/main/resources/db/migration/V13__add_audit_columns.sql`:

```sql
-- V13__add_audit_columns.sql
-- Add created_by/updated_by to all 5 tables; add updated_at to users only.
-- All timestamp columns follow V8 KST naive TIMESTAMP convention.

-- users: created_at already exists (V10). Add updated_at + created_by + updated_by.
ALTER TABLE users
  ADD COLUMN updated_at TIMESTAMP,
  ADD COLUMN created_by VARCHAR(50),
  ADD COLUMN updated_by VARCHAR(50);

-- customers / policies / proposals / claims: created_at + updated_at already exist. Only by-columns missing.
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

-- Backfill existing rows with SYSTEM sentinel
UPDATE users
   SET updated_at = COALESCE(created_at, (now() AT TIME ZONE 'Asia/Seoul'))
 WHERE updated_at IS NULL;
UPDATE users     SET created_by = 'SYSTEM', updated_by = 'SYSTEM' WHERE created_by IS NULL;
UPDATE customers SET created_by = 'SYSTEM', updated_by = 'SYSTEM' WHERE created_by IS NULL;
UPDATE policies  SET created_by = 'SYSTEM', updated_by = 'SYSTEM' WHERE created_by IS NULL;
UPDATE proposals SET created_by = 'SYSTEM', updated_by = 'SYSTEM' WHERE created_by IS NULL;
UPDATE claims    SET created_by = 'SYSTEM', updated_by = 'SYSTEM' WHERE created_by IS NULL;

-- Enforce NOT NULL on all new columns
ALTER TABLE users
  ALTER COLUMN updated_at SET NOT NULL,
  ALTER COLUMN created_by SET NOT NULL,
  ALTER COLUMN updated_by SET NOT NULL;
ALTER TABLE customers ALTER COLUMN created_by SET NOT NULL, ALTER COLUMN updated_by SET NOT NULL;
ALTER TABLE policies  ALTER COLUMN created_by SET NOT NULL, ALTER COLUMN updated_by SET NOT NULL;
ALTER TABLE proposals ALTER COLUMN created_by SET NOT NULL, ALTER COLUMN updated_by SET NOT NULL;
ALTER TABLE claims    ALTER COLUMN created_by SET NOT NULL, ALTER COLUMN updated_by SET NOT NULL;

-- Set DEFAULT on users.updated_at for safety (matches V8 KST convention)
ALTER TABLE users ALTER COLUMN updated_at SET DEFAULT (now() AT TIME ZONE 'Asia/Seoul');
```

- [ ] **Step 2: Run the full test suite — verify migration applies cleanly**

```bash
cd backend && ./mvnw test
```

Expected: BUILD SUCCESS. All existing tests still pass. Flyway logs should show `Migrating schema "public" to version 13 - add audit columns`. Tests still use Hibernate `@CreationTimestamp` (entities not migrated yet), so audit columns will receive their DEFAULT/SYSTEM values — no breakage.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V13__add_audit_columns.sql
git commit -m "feat(backend): Flyway V13 — add audit columns to 5 tables

Adds created_by/updated_by to users/customers/policies/proposals/claims
and updated_at to users. Backfills existing rows with SYSTEM sentinel.
All columns NOT NULL after backfill. Type follows V8 KST naive TIMESTAMP
convention."
```

---

### Task 9: Enable JPA Auditing in `BackendApplication`

**Files:**
- Modify: `backend/src/main/java/com/agentsupport/BackendApplication.java`

- [ ] **Step 1: Add `@EnableJpaAuditing`**

Replace contents of `backend/src/main/java/com/agentsupport/BackendApplication.java`:

```java
package com.agentsupport;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class BackendApplication {

  public static void main(String[] args) {
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    SpringApplication.run(BackendApplication.class, args);
  }
}
```

- [ ] **Step 2: Run full test suite**

```bash
cd backend && ./mvnw test
```

Expected: BUILD SUCCESS. Auditing is enabled but no entity extends `BaseAuditEntity` yet, so no behavior change.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/agentsupport/BackendApplication.java
git commit -m "feat(backend): enable JPA auditing with AuditorAware

Activates @EntityListeners(AuditingEntityListener.class) on entities that
extend BaseAuditEntity. No entity uses it yet — wired up in next commit."
```

---

### Task 10: Migrate all 5 entities to extend `BaseAuditEntity`

**Files:**
- Modify: `backend/src/main/java/com/agentsupport/user/entity/User.java`
- Modify: `backend/src/main/java/com/agentsupport/customer/entity/Customer.java`
- Modify: `backend/src/main/java/com/agentsupport/claim/entity/Claim.java`
- Modify: `backend/src/main/java/com/agentsupport/policy/entity/Policy.java`
- Modify: `backend/src/main/java/com/agentsupport/proposal/entity/Proposal.java`

- [ ] **Step 1: Migrate `User.java`**

Replace contents of `backend/src/main/java/com/agentsupport/user/entity/User.java`:

```java
package com.agentsupport.user.entity;

import com.agentsupport.common.BaseAuditEntity;
import com.agentsupport.security.PiiAttributeConverter;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User extends BaseAuditEntity {

  @Id
  @Column(length = 50)
  private String id;

  @Column(nullable = false, length = 255)
  private String password;

  @Convert(converter = PiiAttributeConverter.class)
  @Column(nullable = false, length = 500)
  private String name;

  @Convert(converter = PiiAttributeConverter.class)
  @Column(nullable = false, length = 500)
  private String phone;

  @Column(name = "ga_name", nullable = false, length = 100)
  private String gaName;

  @Convert(converter = PiiAttributeConverter.class)
  @Column(nullable = false, length = 500)
  private String email;

  @Column(name = "email_hash", nullable = false, length = 64, unique = true)
  private String emailHash;

  @Column(length = 20)
  private String role;

  @Column(nullable = false, length = 20)
  private String status;

  protected User() {}

  public static User create(
      String id, String password, String name, String phone,
      String gaName, String email, String emailHash) {
    User u = new User();
    u.id = id;
    u.password = password;
    u.name = name;
    u.phone = phone;
    u.gaName = gaName;
    u.email = email;
    u.emailHash = emailHash;
    u.role = null;
    u.status = "PENDING";
    return u;
  }

  public static User createAdmin(
      String id, String password, String name, String phone,
      String gaName, String email, String emailHash) {
    User u = create(id, password, name, phone, gaName, email, emailHash);
    u.role = "ADMIN";
    u.status = "ACTIVE";
    return u;
  }

  public void approve(String role) {
    this.role = role;
    this.status = "ACTIVE";
  }

  public void reject() {
    this.status = "REJECTED";
  }

  public String getId() { return id; }
  public String getPassword() { return password; }
  public String getName() { return name; }
  public String getPhone() { return phone; }
  public String getGaName() { return gaName; }
  public String getEmail() { return email; }
  public String getEmailHash() { return emailHash; }
  public String getRole() { return role; }
  public String getStatus() { return status; }
}
```

Note: `createdAt` field and `getCreatedAt()` removed — provided by `BaseAuditEntity`. `@CreationTimestamp` import and field gone.

- [ ] **Step 2: Migrate `Customer.java`**

Replace contents of `backend/src/main/java/com/agentsupport/customer/entity/Customer.java`:

```java
package com.agentsupport.customer.entity;

import com.agentsupport.common.BaseAuditEntity;
import com.agentsupport.security.PiiAttributeConverter;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer extends BaseAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "agent_id", nullable = false, length = 50)
  private String agentId;

  @Convert(converter = PiiAttributeConverter.class)
  @Column(nullable = false, length = 500)
  private String name;

  @Convert(converter = PiiAttributeConverter.class)
  @Column(nullable = false, length = 500)
  private String phone;

  @Column(name = "birth_date")
  private LocalDate birthDate;

  @Column(length = 10)
  private String gender;

  @Convert(converter = PiiAttributeConverter.class)
  @Column(length = 500)
  private String email;

  @Convert(converter = PiiAttributeConverter.class)
  @Column(length = 1000)
  private String address;

  @Column(columnDefinition = "TEXT")
  private String memo;

  protected Customer() {}

  public static Customer create(
      String agentId, String name, String phone,
      LocalDate birthDate, String gender, String email, String address, String memo) {
    Customer c = new Customer();
    c.agentId = agentId;
    c.name = name;
    c.phone = phone;
    c.birthDate = birthDate;
    c.gender = gender;
    c.email = email;
    c.address = address;
    c.memo = memo;
    return c;
  }

  public void update(
      String name, String phone, LocalDate birthDate,
      String gender, String email, String address, String memo) {
    this.name = name;
    this.phone = phone;
    this.birthDate = birthDate;
    this.gender = gender;
    this.email = email;
    this.address = address;
    this.memo = memo;
  }

  public UUID getId() { return id; }
  public String getAgentId() { return agentId; }
  public String getName() { return name; }
  public String getPhone() { return phone; }
  public LocalDate getBirthDate() { return birthDate; }
  public String getGender() { return gender; }
  public String getEmail() { return email; }
  public String getAddress() { return address; }
  public String getMemo() { return memo; }
}
```

Note: `createdAt`, `updatedAt`, `@CreationTimestamp`, `@UpdateTimestamp` imports and fields removed. `LocalDateTime` import removed (no longer used).

- [ ] **Step 3: Migrate `Claim.java`**

Replace contents of `backend/src/main/java/com/agentsupport/claim/entity/Claim.java`:

```java
package com.agentsupport.claim.entity;

import com.agentsupport.common.BaseAuditEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "claims")
public class Claim extends BaseAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "agent_id", nullable = false, length = 50)
  private String agentId;

  @Column(name = "customer_id")
  private UUID customerId;

  @Column(name = "policy_number", nullable = false, length = 20)
  private String policyNumber;

  @Column(name = "customer_name", nullable = false, length = 50)
  private String customerName;

  @Column(name = "insurer_name", nullable = false, length = 50)
  private String insurerName;

  @Column(name = "claim_type", nullable = false, length = 50)
  private String claimType;

  @Column(name = "claim_amount", precision = 12, scale = 2)
  private BigDecimal claimAmount;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "claim_date", nullable = false)
  private LocalDate claimDate;

  protected Claim() {}

  public static Claim create(
      String agentId, UUID customerId, String policyNumber, String customerName,
      String insurerName, String claimType, BigDecimal claimAmount,
      String status, LocalDate claimDate) {
    Claim c = new Claim();
    c.agentId = agentId;
    c.customerId = customerId;
    c.policyNumber = policyNumber;
    c.customerName = customerName;
    c.insurerName = insurerName;
    c.claimType = claimType;
    c.claimAmount = claimAmount;
    c.status = status;
    c.claimDate = claimDate;
    return c;
  }

  public UUID getId() { return id; }
  public String getAgentId() { return agentId; }
  public UUID getCustomerId() { return customerId; }
  public String getPolicyNumber() { return policyNumber; }
  public String getCustomerName() { return customerName; }
  public String getInsurerName() { return insurerName; }
  public String getClaimType() { return claimType; }
  public BigDecimal getClaimAmount() { return claimAmount; }
  public String getStatus() { return status; }
  public LocalDate getClaimDate() { return claimDate; }
}
```

- [ ] **Step 4: Migrate `Policy.java`**

Replace contents of `backend/src/main/java/com/agentsupport/policy/entity/Policy.java`:

```java
package com.agentsupport.policy.entity;

import com.agentsupport.common.BaseAuditEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "policies")
public class Policy extends BaseAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "policy_number", nullable = false, unique = true, length = 20)
  private String policyNumber;

  @Column(name = "agent_id", nullable = false, length = 50)
  private String agentId;

  @Column(name = "customer_name", nullable = false, length = 50)
  private String customerName;

  @Column(name = "product_name", nullable = false, length = 100)
  private String productName;

  @Column(name = "insurer_name", nullable = false, length = 50)
  private String insurerName;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "contract_date", nullable = false)
  private LocalDate contractDate;

  @Column(name = "monthly_premium", precision = 12, scale = 2)
  private BigDecimal monthlyPremium;

  protected Policy() {}

  public UUID getId() { return id; }
  public String getPolicyNumber() { return policyNumber; }
  public String getAgentId() { return agentId; }
  public String getCustomerName() { return customerName; }
  public String getProductName() { return productName; }
  public String getInsurerName() { return insurerName; }
  public String getStatus() { return status; }
  public LocalDate getContractDate() { return contractDate; }
  public BigDecimal getMonthlyPremium() { return monthlyPremium; }
}
```

- [ ] **Step 5: Migrate `Proposal.java`**

Replace contents of `backend/src/main/java/com/agentsupport/proposal/entity/Proposal.java`:

```java
package com.agentsupport.proposal.entity;

import com.agentsupport.common.BaseAuditEntity;
import com.agentsupport.security.PiiAttributeConverter;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "proposals")
public class Proposal extends BaseAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "agent_id", nullable = false, length = 50)
  private String agentId;

  @Convert(converter = PiiAttributeConverter.class)
  @Column(name = "customer_name", nullable = false)
  private String customerName;

  @Convert(converter = PiiAttributeConverter.class)
  @Column(name = "phone_number")
  private String phoneNumber;

  @Convert(converter = PiiAttributeConverter.class)
  @Column(name = "birth_date")
  private String birthDate;

  @Column(name = "product_name", nullable = false, length = 100)
  private String productName;

  @Column(name = "insurer_name", nullable = false, length = 50)
  private String insurerName;

  @Column(name = "monthly_premium", precision = 12, scale = 2)
  private BigDecimal monthlyPremium;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "proposed_date", nullable = false)
  private LocalDate proposedDate;

  protected Proposal() {}

  public static Proposal create(
      String agentId,
      String customerName,
      String phoneNumber,
      String birthDate,
      String productName,
      String insurerName,
      BigDecimal monthlyPremium) {
    Proposal p = new Proposal();
    p.agentId = agentId;
    p.customerName = customerName;
    p.phoneNumber = phoneNumber;
    p.birthDate = birthDate;
    p.productName = productName;
    p.insurerName = insurerName;
    p.monthlyPremium = monthlyPremium;
    p.status = "작성 중";
    p.proposedDate = LocalDate.now();
    return p;
  }

  public UUID getId() { return id; }
  public String getAgentId() { return agentId; }
  public String getCustomerName() { return customerName; }
  public String getPhoneNumber() { return phoneNumber; }
  public String getBirthDate() { return birthDate; }
  public String getProductName() { return productName; }
  public String getInsurerName() { return insurerName; }
  public BigDecimal getMonthlyPremium() { return monthlyPremium; }
  public String getStatus() { return status; }
  public LocalDate getProposedDate() { return proposedDate; }
}
```

- [ ] **Step 6: Run full test suite**

```bash
cd backend && ./mvnw test
```

Expected: BUILD SUCCESS. All existing tests pass — `created_by` and `updated_by` are now populated by `AuditorAwareImpl` (the test's `MockHttpSession` carries the logged-in user's name into `SecurityContextHolder`, which `AuditorAwareImpl` reads).

If any test fails with `not-null property references a null value` for `created_by` or `updated_by`, it means a path is saving an entity without a SecurityContext — debug by checking which call path triggered it.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/agentsupport/user/entity/User.java \
        backend/src/main/java/com/agentsupport/customer/entity/Customer.java \
        backend/src/main/java/com/agentsupport/claim/entity/Claim.java \
        backend/src/main/java/com/agentsupport/policy/entity/Policy.java \
        backend/src/main/java/com/agentsupport/proposal/entity/Proposal.java
git commit -m "feat(backend): migrate 5 entities to BaseAuditEntity

User/Customer/Claim/Policy/Proposal now extend BaseAuditEntity. Removes
Hibernate @CreationTimestamp/@UpdateTimestamp in favor of Spring Data
@CreatedDate/@LastModifiedDate, with @CreatedBy/@LastModifiedBy auto-
populated by AuditorAwareImpl from SecurityContextHolder."
```

---

### Task 11: Add audit field assertions to `CustomerControllerTest`

**Files:**
- Modify: `backend/src/test/java/com/agentsupport/customer/CustomerControllerTest.java`

- [ ] **Step 1: Add assertion test**

Insert a new `@Test` method into `CustomerControllerTest.java` (place after the existing `agent2_creates_customer_returns201` test, before `agent1_lists_only_own_customers`):

```java
  @Test
  void agent2_creates_customer_sets_audit_fields() throws Exception {
    mockMvc.perform(post("/api/customers")
            .session(agent2Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody("감사대상고객")))
        .andExpect(status().isCreated());

    MvcResult listResult = mockMvc.perform(get("/api/customers").session(agent2Session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].name").value("감사대상고객"))
        .andReturn();
    // Customer DTO does not expose audit fields publicly; the assertion is that
    // creation does not fail with not-null violations on created_by/updated_by.
    // (If CustomerResponse later exposes audit fields, assert them here directly.)
    org.junit.jupiter.api.Assertions.assertTrue(
        listResult.getResponse().getContentAsString().contains("감사대상고객"));
  }
```

- [ ] **Step 2: Run the new test**

```bash
cd backend && ./mvnw test -Dtest=CustomerControllerTest#agent2_creates_customer_sets_audit_fields
```

Expected: PASS. The implicit assertion is that the insert succeeds — if `AuditorAware` returned `null` or audit columns weren't NOT NULL-able, this would throw before status 201.

- [ ] **Step 3: Run full backend test suite**

```bash
cd backend && ./mvnw test
```

Expected: BUILD SUCCESS, all tests green.

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/com/agentsupport/customer/CustomerControllerTest.java
git commit -m "test(backend): assert customer creation populates audit fields

Implicit assertion via successful insert with NOT NULL created_by /
updated_by constraints. Will be extended once DTO surfaces audit columns."
```

---

### Task 12: Manual verification of PR2 (database state)

**Files:** (no file changes)

- [ ] **Step 1: Start backend (clean DB)**

```bash
cd backend && ./mvnw spring-boot:run
```

Wait for `Started BackendApplication`. Flyway should log applying V13.

- [ ] **Step 2: Verify schema in psql**

In a second terminal:

```bash
psql "postgres://$DB_USERNAME:$DB_PASSWORD@$DB_HOST:$DB_PORT/$DB_NAME" \
  -c "\d users" \
  -c "\d customers" \
  -c "\d policies" \
  -c "\d proposals" \
  -c "\d claims"
```

Expected: All 5 tables show `created_at`, `created_by`, `updated_at`, `updated_by`, all NOT NULL.

- [ ] **Step 3: Verify seed data backfill**

```bash
psql "..." -c "SELECT id, created_by, updated_by FROM users WHERE created_by = 'SYSTEM' LIMIT 5;"
psql "..." -c "SELECT id, created_by, updated_by FROM policies WHERE created_by = 'SYSTEM' LIMIT 5;"
```

Expected: Returns the seed rows from V3 / V6 with `SYSTEM` sentinel.

- [ ] **Step 4: Verify live audit population**

Login as `admin` via API, then create a customer:

```bash
# Get CSRF cookie + session cookie via login (curl with -c jar)
curl -i -c jar -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin1234!"}'

# Extract XSRF-TOKEN from jar and create a customer
XSRF=$(grep XSRF-TOKEN jar | awk '{print $NF}')
curl -b jar -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $XSRF" \
  -d '{"name":"감사확인","phone":"010-9999-9999","birthDate":"1990-01-01","gender":"M","email":"a@b.com","address":"서울","memo":""}'
```

Then in psql:

```sql
SELECT created_by, updated_by FROM customers WHERE name LIKE '%감사확인%' ORDER BY created_at DESC LIMIT 1;
```

Expected: `created_by = 'admin'`, `updated_by = 'admin'`.

- [ ] **Step 5: Stop backend**

Ctrl+C the backend. No commit — verification only.

---

## Final Verification Checklist

Run this checklist before requesting code review or merging either PR.

- [ ] **PR1 lint/typecheck/build/FSD pass**

```bash
cd frontend && pnpm typecheck && pnpm lint && pnpm lint:fsd && pnpm build
```

- [ ] **PR2 full test pass**

```bash
cd backend && ./mvnw test
```

- [ ] **PR1 manual matrix passed** (Task 3)
- [ ] **PR2 DB state verified** (Task 12)
- [ ] **Both branches based on latest `master`**

```bash
git log --oneline master..feat/ssr-auth-guard
git log --oneline master..feat/jpa-auditing
```

Expected: Each branch shows only its own commits, no unrelated drift.

---

## Push and PR Creation (requires user authorization)

Per project convention, do NOT push or create PRs without explicit user confirmation.

When the user authorizes:

- [ ] **Push PR1**

```bash
git checkout feat/ssr-auth-guard
git push -u origin feat/ssr-auth-guard
```

- [ ] **Open PR1**

```bash
gh pr create --title "feat: SSR auth guard via Next.js middleware" --body "$(cat <<'EOF'
## Summary

- Adds `frontend/src/middleware.ts` intercepting protected routes
- Validates session via backend `/api/auth/me` (server-to-server)
- Enforces role-based access (ADMIN-only `/admin`, AGENT2 blocked from `/proposals|/underwriting`)

## Test plan

- [ ] Logged-out → `/dashboard` returns 302 to `/login` without HTML
- [ ] AGENT2 → `/proposals` returns 302 to `/dashboard`
- [ ] `/login`, `/register`, `/pending` accessible without auth
- [ ] Static `/_next/*` assets bypass middleware

Spec: docs/superpowers/specs/2026-05-20-security-hardening-design.md

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Push PR2**

```bash
git checkout feat/jpa-auditing
git push -u origin feat/jpa-auditing
```

- [ ] **Open PR2**

```bash
gh pr create --title "feat: JPA auditing — createdBy/updatedBy on all entities" --body "$(cat <<'EOF'
## Summary

- Adds `BaseAuditEntity` MappedSuperclass with created_at/created_by/updated_at/updated_by
- Adds `AuditorAwareImpl` reading SecurityContextHolder (SYSTEM sentinel for anonymous)
- Flyway V13 backfills 5 tables with SYSTEM and enforces NOT NULL
- Migrates User/Customer/Claim/Policy/Proposal to extend BaseAuditEntity
- Replaces Hibernate @CreationTimestamp/@UpdateTimestamp with Spring Data @CreatedDate/@LastModifiedDate

## Test plan

- [ ] `./mvnw test` green
- [ ] Flyway V13 applies cleanly on fresh DB
- [ ] Live customer creation populates created_by with logged-in username
- [ ] Seed rows backfilled to 'SYSTEM'

Spec: docs/superpowers/specs/2026-05-20-security-hardening-design.md

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```
