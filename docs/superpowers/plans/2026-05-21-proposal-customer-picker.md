# Proposal Customer Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 새 설계 등록 모달에서 자유 입력 대신 등록된 고객만 선택하도록 변경. 백엔드에 `proposals.customer_id` FK 추가하고 생성 시점에 고객 정보 스냅샷 유지.

**Architecture:** 백엔드: V16 마이그레이션으로 `proposals.customer_id` FK 추가(`ON DELETE SET NULL`) + name/agent_id 매칭 백필. `ProposalCreateRequest`가 `customerId`만 받도록 변경하고 `ProposalService`가 customer 조회 → 권한 검증 → 비정규화 필드 스냅샷 저장. 프론트엔드: `entities/customer/ui/customer-picker` (검색 콤보박스) 신설, `features/proposal/create` ui 재구성 (수기 입력 제거, picker + 선택 고객 카드).

**Tech Stack:** Spring Boot 3.5, Spring Data JPA, Flyway, JUnit 5 + MockMvc, Next.js 16 (App Router), React Query, react-hook-form + zod, Tailwind v4, pnpm.

**Reference Spec:** `docs/superpowers/specs/2026-05-21-proposal-customer-picker-design.md`

**Branch:** `feat/proposal-customer-picker` (master에서 분기, `feat/health-analysis` 머지 완료 후 시작).

---

## File Structure

### 신규 (Backend)

```
backend/src/main/resources/db/migration/V16__add_customer_id_to_proposals.sql
backend/src/test/java/com/agentsupport/proposal/ProposalControllerTest.java
```

### 수정 (Backend)

```
backend/src/main/java/com/agentsupport/proposal/
├── entity/Proposal.java                  # customerId 필드 + create() 파라미터 추가
├── dto/ProposalCreateRequest.java        # name/phone/birthDate 제거, customerId 추가
├── dto/ProposalDto.java                  # customerId 노출
├── service/ProposalService.java          # createProposal: customer 조회 + 권한 + 스냅샷
└── ProposalController.java               # isAdmin 전달
```

### 신규 (Frontend)

```
frontend/src/entities/customer/ui/customer-picker/index.tsx
```

### 수정 (Frontend)

```
frontend/src/entities/customer/ui/index.ts          # CustomerPicker export
frontend/src/entities/customer/index.ts             # CustomerPicker re-export
frontend/src/features/proposal/create/
├── api/index.ts                          # CreateRequest 필드 변경
└── ui/index.tsx                          # 폼 재구성 (picker + 선택 카드)
```

---

## Conventions

- **Working dir**: `d:\fieldarena2`. 백엔드 명령은 `cd backend`, 프론트엔드는 `cd frontend`.
- **Branch**: `feat/proposal-customer-picker` (사전 조건: `feat/health-analysis` 가 master에 머지된 상태).
- **Verification**:
  - Backend: `cd backend; ./gradlew.bat test --tests <pattern>`
  - Frontend: `cd frontend; pnpm typecheck; pnpm lint; pnpm lint:fsd; pnpm build`
- **Commits**: 각 Task 끝에서 한 번. push는 사용자 승인 후.
- **Pre-commit hook**: Husky가 `pnpm lint:fsd` 자동 실행.

---

# Phase A: Backend

## Task 1: V16 migration — proposals.customer_id

**Files:**
- Create: `backend/src/main/resources/db/migration/V16__add_customer_id_to_proposals.sql`

- [ ] **Step 1: 마이그레이션 SQL 작성**

```sql
-- V16__add_customer_id_to_proposals.sql
ALTER TABLE proposals ADD COLUMN customer_id UUID NULL;
ALTER TABLE proposals ADD CONSTRAINT fk_proposals_customer
  FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE SET NULL;
CREATE INDEX idx_proposals_customer_id ON proposals(customer_id);

UPDATE proposals p
SET customer_id = c.id
FROM customers c
WHERE p.agent_id = c.agent_id
  AND p.customer_name = c.name
  AND p.customer_id IS NULL;
```

**중요**: `proposals.customer_name`은 `PiiAttributeConverter`로 암호화되어 저장됨. backfill `UPDATE`는 원본 평문이 아닌 암호화된 값을 비교하므로, 직접 매칭이 안 될 수 있다. 만약 backfill 결과가 모두 NULL이면 (모든 proposals의 customer_name이 암호화된 값이라 customers.name 평문과 불일치), 다음 두 가지 중 하나:
- 옵션 A: backfill SQL 단순화 (UPDATE 빼고, NULL인 채로 둠 — 신규 생성만 customerId 채움)
- 옵션 B: Java 마이그레이션 (Flyway Java migration)으로 PiiAttributeConverter 복호화 후 매칭

먼저 옵션 A로 진행. SQL은 위 그대로 두되, UPDATE 결과가 0건이어도 OK (단순 신규 흐름만 customerId 사용).

- [ ] **Step 2: 부트 검증**

Run: `cd backend; ./gradlew.bat test --tests BackendApplicationTests`
Expected: Flyway가 V16 적용, 컨텍스트 정상 기동.

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/resources/db/migration/V16__add_customer_id_to_proposals.sql
git commit -m "feat(proposal): add customer_id FK with ON DELETE SET NULL (V16)"
```

---

## Task 2: Proposal 엔티티 — customerId 필드 + create() 시그니처

**Files:**
- Modify: `backend/src/main/java/com/agentsupport/proposal/entity/Proposal.java`

- [ ] **Step 1: customerId 필드 추가 (customer_name 필드 위, agent_id 아래)**

Edit `Proposal.java`. `agentId` 필드 바로 뒤, `customerName` 위에 추가:

```java
  @Column(name = "customer_id")
  private UUID customerId;
```

`import java.util.UUID;`가 이미 있으므로 추가 import 불요.

- [ ] **Step 2: create() 팩토리에 customerId 첫 파라미터로 추가**

```java
public static Proposal create(
    String agentId,
    UUID customerId,
    String customerName,
    String phoneNumber,
    String birthDate,
    String productName,
    String insurerName,
    BigDecimal monthlyPremium) {
  Proposal p = new Proposal();
  p.agentId = agentId;
  p.customerId = customerId;
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
```

- [ ] **Step 3: getter 추가**

기존 getter 블록에 추가:
```java
  public UUID getCustomerId() { return customerId; }
```

- [ ] **Step 4: 컴파일 확인 — ProposalService는 일시적으로 깨짐**

Run: `cd backend; ./gradlew.bat compileJava`
Expected: 컴파일 실패 — `ProposalService.createProposal()`이 `Proposal.create()` 호출 시 7개 인자만 전달 (customerId 누락). Task 5에서 서비스를 업데이트하면서 해결.

(이 단계는 의도된 중간 상태. 커밋 안 함.)

---

## Task 3: ProposalCreateRequest DTO 변경

**Files:**
- Modify: `backend/src/main/java/com/agentsupport/proposal/dto/ProposalCreateRequest.java`

- [ ] **Step 1: 필드 교체**

전체 내용 교체:

```java
package com.agentsupport.proposal.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record ProposalCreateRequest(
    @NotNull UUID customerId,
    @NotBlank String productName,
    @NotBlank String insurerName,
    @NotNull @Positive BigDecimal monthlyPremium) {}
```

기존 `customerName`, `phoneNumber`, `birthDate` 제거. 검증 어노테이션도 함께 제거.

- [ ] **Step 2: 컴파일 확인 — Service는 여전히 깨짐**

Run: `cd backend; ./gradlew.bat compileJava`
Expected: 컴파일 실패 — `ProposalService.createProposal()`이 `req.customerName()` 등 없는 메서드 호출. Task 5에서 해결.

---

## Task 4: ProposalDto에 customerId 노출

**Files:**
- Modify: `backend/src/main/java/com/agentsupport/proposal/dto/ProposalDto.java`

- [ ] **Step 1: customerId 필드 + from() 업데이트**

전체 내용 교체:

```java
package com.agentsupport.proposal.dto;

import com.agentsupport.proposal.entity.Proposal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

public record ProposalDto(
    UUID id,
    UUID customerId,
    String customerName,
    String phoneNumber,
    String age,
    String productName,
    String insurerName,
    BigDecimal monthlyPremium,
    String status,
    LocalDate proposedDate) {

  public static ProposalDto from(Proposal p) {
    return new ProposalDto(
        p.getId(),
        p.getCustomerId(),
        maskName(p.getCustomerName()),
        maskPhone(p.getPhoneNumber()),
        maskAge(p.getBirthDate()),
        p.getProductName(),
        p.getInsurerName(),
        p.getMonthlyPremium(),
        p.getStatus(),
        p.getProposedDate());
  }

  private static String maskName(String name) {
    if (name == null || name.isBlank()) return "-";
    return name.substring(0, 1) + "○".repeat(Math.max(1, name.length() - 1));
  }

  private static String maskPhone(String phone) {
    if (phone == null || phone.isBlank()) return "-";
    return phone.replaceAll("^(\\d{3}-)\\d{3,4}(-\\d{4})$", "$1****$2");
  }

  private static String maskAge(String birthDate) {
    if (birthDate == null || birthDate.isBlank()) return "-";
    try {
      int age = Period.between(LocalDate.parse(birthDate), LocalDate.now()).getYears();
      return String.valueOf(age).charAt(0) + "*세";
    } catch (Exception e) {
      return "-";
    }
  }
}
```

- [ ] **Step 2: 컴파일 확인 (서비스는 여전히 깨짐)**

Run: `cd backend; ./gradlew.bat compileJava`
Expected: 컴파일 실패 — 서비스 부분만. DTO 자체는 OK.

---

## Task 5: ProposalControllerTest 작성 (failing) + ProposalService 업데이트

**Files:**
- Create: `backend/src/test/java/com/agentsupport/proposal/ProposalControllerTest.java`
- Modify: `backend/src/main/java/com/agentsupport/proposal/service/ProposalService.java`
- Modify: `backend/src/main/java/com/agentsupport/proposal/ProposalController.java`

### Step 1: Test 작성 (실패 예상)

`CustomerControllerTest` 와 `HealthAnalysisControllerTest`의 패턴을 따라 작성. fixture는 동일.

```java
// backend/src/test/java/com/agentsupport/proposal/ProposalControllerTest.java
package com.agentsupport.proposal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProposalControllerTest {

  @Autowired WebApplicationContext webApplicationContext;
  MockMvc mockMvc;
  MockHttpSession adminSession;
  MockHttpSession agent1Session;

  // CustomerControllerTest의 세션 셋업 패턴 그대로 복사:
  //   1. ADMIN/AGENT1 사용자를 미리 DB에 INSERT (UserRepository) 또는 SecurityMockMvc 헬퍼
  //   2. login POST 호출 후 세션 쿠키 저장
  //   3. agent1로 customer 1명 등록 (POST /api/customers)
  //   4. adminSession 으로 다른 agent2의 customer 1명 등록

  @BeforeEach
  void setUp() throws Exception {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
        .apply(springSecurity())
        .build();
    // 세션 셋업: CustomerControllerTest 참조
  }

  @Test
  void agent1CanCreateProposalForOwnCustomer() throws Exception {
    // 1. agent1Session 으로 customer 등록 → customerId 획득
    UUID customerId = createCustomerAs(agent1Session, "김고객");

    // 2. agent1 으로 POST /api/proposals with { customerId, productName, insurerName, monthlyPremium }
    String body = """
        {
          "customerId": "%s",
          "productName": "무배당 종신보험",
          "insurerName": "삼성생명",
          "monthlyPremium": 150000
        }
        """.formatted(customerId);

    mockMvc.perform(post("/api/proposals")
            .session(agent1Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.customerId").value(customerId.toString()))
        .andExpect(jsonPath("$.customerName").exists())
        .andExpect(jsonPath("$.productName").value("무배당 종신보험"));
  }

  @Test
  void agent1CannotCreateProposalForOtherAgentCustomer() throws Exception {
    // 1. adminSession 으로 customer 등록 (agent_id = admin) → customerId 획득
    UUID customerId = createCustomerAs(adminSession, "다른고객");

    // 2. agent1 으로 POST with that customerId → 403 expected
    String body = """
        {
          "customerId": "%s",
          "productName": "X",
          "insurerName": "Y",
          "monthlyPremium": 1000
        }
        """.formatted(customerId);

    mockMvc.perform(post("/api/proposals")
            .session(agent1Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isForbidden());
  }

  @Test
  void postWithNonexistentCustomerIdReturns404() throws Exception {
    UUID fakeId = UUID.randomUUID();
    String body = """
        {
          "customerId": "%s",
          "productName": "X",
          "insurerName": "Y",
          "monthlyPremium": 1000
        }
        """.formatted(fakeId);

    mockMvc.perform(post("/api/proposals")
            .session(agent1Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isNotFound());
  }

  @Test
  void adminCanCreateProposalForAnyCustomer() throws Exception {
    UUID customerId = createCustomerAs(agent1Session, "유저");

    String body = """
        {
          "customerId": "%s",
          "productName": "X",
          "insurerName": "Y",
          "monthlyPremium": 1000
        }
        """.formatted(customerId);

    mockMvc.perform(post("/api/proposals")
            .session(adminSession)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated());
  }

  // Helper: customer 등록 후 id 반환
  private UUID createCustomerAs(MockHttpSession session, String name) throws Exception {
    // CustomerControllerTest의 customer 생성 패턴 따라 구현
    // POST /api/customers, parse response body for "id"
    return UUID.randomUUID();  // placeholder until concrete helper
  }
}
```

**중요**: `CustomerControllerTest.java`의 fixture 패턴(세션 빌더, customer/user 생성 헬퍼)을 정확히 따라 작성. 위 코드는 골격만. 실제 구현 시 `CustomerControllerTest`를 먼저 읽고 `adminSession`/`agent1Session` 셋업 코드와 customer 생성 helper를 그대로 복사.

### Step 2: Test 실행 (실패 확인)

Run: `cd backend; ./gradlew.bat test --tests ProposalControllerTest`
Expected: 4/4 FAIL — 컴파일 에러 (ProposalService.createProposal 서명이 안 맞음) 또는 런타임 에러 (customer 검증 없음).

### Step 3: ProposalService.createProposal 구현

`backend/src/main/java/com/agentsupport/proposal/service/ProposalService.java` — 전체 교체:

```java
package com.agentsupport.proposal.service;

import com.agentsupport.common.dto.PageResponse;
import com.agentsupport.customer.entity.Customer;
import com.agentsupport.customer.repository.CustomerRepository;
import com.agentsupport.proposal.dto.ProposalCreateRequest;
import com.agentsupport.proposal.dto.ProposalDto;
import com.agentsupport.proposal.entity.Proposal;
import com.agentsupport.proposal.repository.ProposalRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
@Transactional(readOnly = true)
public class ProposalService {

  private final ProposalRepository proposalRepository;
  private final CustomerRepository customerRepository;

  public ProposalService(ProposalRepository proposalRepository, CustomerRepository customerRepository) {
    this.proposalRepository = proposalRepository;
    this.customerRepository = customerRepository;
  }

  public PageResponse<ProposalDto> findProposals(String agentId, String status, int page, int size) {
    Pageable pageable = PageRequest.of(page, Math.min(size, 100));
    String statusFilter = (status == null || status.isBlank()) ? null : status;
    return PageResponse.from(
        proposalRepository.findByCondition(agentId, statusFilter, pageable).map(ProposalDto::from));
  }

  @Transactional
  public ProposalDto createProposal(String agentId, boolean isAdmin, ProposalCreateRequest req) {
    Customer customer = customerRepository.findById(req.customerId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "customer not found: " + req.customerId()));

    if (!isAdmin && !customer.getAgentId().equals(agentId)) {
      throw new AccessDeniedException("not allowed to create proposal for customer not owned by this agent");
    }

    Proposal proposal = Proposal.create(
        agentId,
        customer.getId(),
        customer.getName(),
        customer.getPhone(),
        customer.getBirthDate() == null ? null : customer.getBirthDate().toString(),
        req.productName(),
        req.insurerName(),
        req.monthlyPremium());
    return ProposalDto.from(proposalRepository.save(proposal));
  }
}
```

### Step 4: ProposalController 업데이트

`backend/src/main/java/com/agentsupport/proposal/ProposalController.java` — `create` 메서드만 수정:

```java
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ProposalDto create(
      Authentication auth,
      @Valid @RequestBody ProposalCreateRequest request) {
    boolean isAdmin = auth.getAuthorities().stream()
        .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    return proposalService.createProposal(auth.getName(), isAdmin, request);
  }
```

(필요 시 `import org.springframework.security.core.GrantedAuthority;` 등은 IDE/컴파일러가 안내)

### Step 5: 테스트 통과 확인

Run: `cd backend; ./gradlew.bat test --tests ProposalControllerTest`
Expected: 4/4 PASS.

전체 빌드:
Run: `cd backend; ./gradlew.bat build -x spotlessCheck`
Expected: 모든 테스트 통과.

### Step 6: 커밋

```bash
git add backend/src/main/java/com/agentsupport/proposal backend/src/test/java/com/agentsupport/proposal
git commit -m "feat(proposal): require customerId and snapshot customer info on create"
```

(Task 2-4 변경 사항도 같이 staged됨 — 이 시점까지 미커밋이었던 entity/DTO 변경 + 서비스 업데이트 + 컨트롤러 + 테스트가 한 커밋에 묶임. 이 묶음이 의미 있는 단위.)

---

# Phase B: Frontend

## Task 6: CustomerPicker 컴포넌트

**Files:**
- Create: `frontend/src/entities/customer/ui/customer-picker/index.tsx`

- [ ] **Step 1: 폴더 + 파일 생성**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/entities/customer/ui/customer-picker -Force
```

```tsx
// frontend/src/entities/customer/ui/customer-picker/index.tsx
"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { Search } from "lucide-react";
import type { CustomerDto } from "../../api";
import { useCustomers } from "../../model";

interface Props {
  value: CustomerDto | null;
  onChange: (customer: CustomerDto | null) => void;
  error?: string;
}

export function CustomerPicker({ value, onChange, error }: Props) {
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement | null>(null);

  const { data, isLoading } = useCustomers({ size: 1000 });
  const customers = data?.content ?? [];

  const filtered = useMemo(() => {
    if (!query.trim()) return customers;
    const q = query.trim().toLowerCase();
    return customers.filter(
      (c) =>
        c.name.toLowerCase().includes(q) ||
        c.phone.toLowerCase().includes(q),
    );
  }, [customers, query]);

  useEffect(() => {
    function onDocClick(e: MouseEvent) {
      if (!containerRef.current?.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", onDocClick);
    return () => document.removeEventListener("mousedown", onDocClick);
  }, []);

  function handleSelect(c: CustomerDto) {
    onChange(c);
    setQuery("");
    setOpen(false);
  }

  return (
    <div ref={containerRef} className="relative flex flex-col gap-1">
      <div className="relative">
        <Search
          size={16}
          className="absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant pointer-events-none"
        />
        <input
          type="text"
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          placeholder="고객명 또는 전화번호로 검색…"
          className={`w-full pl-9 pr-3 py-2.5 rounded-lg border ${
            error ? "border-status-error" : "border-outline-variant"
          } bg-surface-container-lowest text-sm text-on-surface outline-none focus:border-primary-container`}
        />
      </div>
      {error && <span className="text-xs text-status-error">{error}</span>}

      {open && (
        <div className="absolute top-full left-0 right-0 mt-1 z-10 bg-surface-container-lowest border border-outline-variant rounded-lg shadow-card max-h-64 overflow-y-auto">
          {isLoading && (
            <div className="px-3 py-2 text-sm text-on-surface-variant">로딩 중…</div>
          )}
          {!isLoading && customers.length === 0 && (
            <div className="px-3 py-3 text-sm text-on-surface-variant">
              등록된 고객이 없습니다. 먼저{" "}
              <Link href="/customers" className="text-primary hover:underline">
                고객 관리
              </Link>
              에서 등록하세요.
            </div>
          )}
          {!isLoading && customers.length > 0 && filtered.length === 0 && (
            <div className="px-3 py-3 text-sm text-on-surface-variant">
              검색 결과가 없습니다.{" "}
              <Link href="/customers" className="text-primary hover:underline">
                고객 관리
              </Link>
              에서 등록 후 다시 시도하세요.
            </div>
          )}
          {!isLoading &&
            filtered.map((c) => (
              <button
                key={c.id}
                type="button"
                onClick={() => handleSelect(c)}
                className="block w-full text-left px-3 py-2 hover:bg-surface-container-low transition-colors"
              >
                <div className="text-sm font-medium text-on-surface">{c.name}</div>
                <div className="text-xs text-on-surface-variant">{c.phone}</div>
              </button>
            ))}
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 2: 타입체크**

Run: `cd frontend; pnpm typecheck`
Expected: 통과.

---

## Task 7: entities/customer Public API 확장

**Files:**
- Modify: `frontend/src/entities/customer/ui/index.ts` (없으면 Create)
- Modify: `frontend/src/entities/customer/index.ts`

- [ ] **Step 1: ui/index.ts 작성/확장**

Edit/Create `frontend/src/entities/customer/ui/index.ts`:

```ts
export { CustomerPicker } from "./customer-picker";
```

(이미 다른 export가 있으면 함께 유지.)

- [ ] **Step 2: 슬라이스 Public API에 CustomerPicker 추가**

Edit `frontend/src/entities/customer/index.ts` — 기존 export 유지하고 추가:

```ts
export { CustomerPicker } from "./ui";
```

(이미 ui re-export가 있는 패턴이면 그 줄 갱신.)

- [ ] **Step 3: 타입체크 + Steiger**

Run:
```powershell
cd frontend
pnpm typecheck
pnpm lint:fsd
```
Expected: 통과.

- [ ] **Step 4: 커밋 (Phase B 첫 commit — picker + entity API)**

```bash
git add frontend/src/entities/customer
git commit -m "feat(frontend): add CustomerPicker combobox in entities/customer/ui"
```

---

## Task 8: features/proposal/create API 변경

**Files:**
- Modify: `frontend/src/features/proposal/create/api/index.ts`

- [ ] **Step 1: CreateRequest 시그니처 변경**

전체 교체:

```ts
import { apiFetch } from "@/shared/api";
import type { ProposalDto } from "@/entities/proposal";

export interface ProposalCreateRequest {
  customerId: string;
  productName: string;
  insurerName: string;
  monthlyPremium: number;
}

export function createProposal(req: ProposalCreateRequest): Promise<ProposalDto> {
  return apiFetch<ProposalDto>("/api/proposals", {
    method: "POST",
    body: JSON.stringify(req),
  });
}
```

- [ ] **Step 2: 타입체크 (UI는 일시적으로 깨짐 — Task 9에서 해결)**

Run: `cd frontend; pnpm typecheck`
Expected: 실패 — `features/proposal/create/ui/index.tsx`에서 `customerName` 등 필드 참조. Task 9에서 UI 갱신.

(커밋은 Task 9 끝에서 한 번에.)

---

## Task 9: features/proposal/create UI 재구성

**Files:**
- Modify: `frontend/src/features/proposal/create/ui/index.tsx`
- Possibly: `frontend/src/entities/proposal/api/index.ts` (ProposalDto에 customerId 추가)

- [ ] **Step 1: ProposalDto에 customerId 필드 추가 (필요 시)**

`frontend/src/entities/proposal/api/index.ts` 확인. ProposalDto에 `customerId`가 이미 있으면 skip. 없으면 추가:

```ts
export interface ProposalDto {
  id: string;
  customerId: string | null;       // 신규
  customerName: string;
  phoneNumber: string;
  age: string;
  productName: string;
  insurerName: string;
  monthlyPremium: number | null;
  status: string;
  proposedDate: string;
}
```

- [ ] **Step 2: ProposalFormModal 전체 교체**

```tsx
// frontend/src/features/proposal/create/ui/index.tsx
"use client";

import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { X } from "lucide-react";
import { useForm, Controller } from "react-hook-form";
import { z } from "zod";
import type { CustomerDto } from "@/entities/customer";
import { CustomerPicker } from "@/entities/customer";
import { Button } from "@/shared/ui/button";
import { TextField } from "@/shared/ui/text-field";
import { useCreateProposal } from "../model";

const schema = z.object({
  customerId: z.string().min(1, "고객을 선택하세요"),
  productName: z.string().min(1, "상품명을 입력하세요"),
  insurerName: z.string().min(1, "보험사를 입력하세요"),
  monthlyPremium: z
    .string()
    .min(1, "보험료를 입력하세요")
    .refine((v) => !isNaN(Number(v)) && Number(v) > 0, "올바른 금액을 입력하세요"),
});

type FormValues = z.infer<typeof schema>;

interface Props {
  onClose: () => void;
}

export function ProposalFormModal({ onClose }: Props) {
  const { mutate, isPending } = useCreateProposal();
  const [selectedCustomer, setSelectedCustomer] = useState<CustomerDto | null>(null);

  const {
    register,
    handleSubmit,
    control,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  function onSubmit(values: FormValues) {
    mutate(
      {
        customerId: values.customerId,
        productName: values.productName,
        insurerName: values.insurerName,
        monthlyPremium: Number(values.monthlyPremium),
      },
      { onSuccess: onClose },
    );
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm"
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <div className="bg-surface-container-lowest rounded-2xl shadow-lg w-full max-w-lg mx-4">
        <div className="flex items-center justify-between px-6 py-4 border-b border-outline-variant">
          <h2 className="text-base font-semibold text-on-surface">새 설계 등록</h2>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-lg text-on-surface-variant hover:bg-surface-container-low transition-colors"
            aria-label="닫기"
          >
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          <div className="px-6 py-5 flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <label className="text-xs font-medium text-on-surface-variant">고객 *</label>
              <Controller
                control={control}
                name="customerId"
                render={({ field, fieldState }) => (
                  <CustomerPicker
                    value={selectedCustomer}
                    onChange={(c) => {
                      setSelectedCustomer(c);
                      field.onChange(c?.id ?? "");
                    }}
                    error={fieldState.error?.message}
                  />
                )}
              />
              {selectedCustomer && <SelectedCustomerCard customer={selectedCustomer} />}
            </div>

            <TextField
              label="상품명"
              placeholder="무배당 종신보험"
              error={errors.productName?.message}
              {...register("productName")}
            />

            <TextField
              label="보험사"
              placeholder="삼성생명"
              error={errors.insurerName?.message}
              {...register("insurerName")}
            />

            <TextField
              label="월 보험료 (원)"
              type="number"
              min="0"
              step="1"
              placeholder="150000"
              error={errors.monthlyPremium?.message}
              {...register("monthlyPremium")}
            />
          </div>

          <div className="px-6 pb-5 flex gap-3">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 py-3 px-6 rounded-xl border border-outline-variant text-sm font-semibold text-on-surface hover:bg-surface-container-low transition-colors"
            >
              취소
            </button>
            <Button type="submit" loading={isPending} className="flex-1">
              등록
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}

function SelectedCustomerCard({ customer }: { customer: CustomerDto }) {
  const age =
    customer.birthDate && /^\d{4}-\d{2}-\d{2}$/.test(customer.birthDate)
      ? new Date().getFullYear() - new Date(customer.birthDate).getFullYear()
      : null;
  const genderLabel =
    customer.gender === "M" ? "남" : customer.gender === "F" ? "여" : null;
  const meta = [
    customer.phone,
    customer.birthDate,
    age ? `${age}세` : null,
    genderLabel,
  ]
    .filter(Boolean)
    .join(" · ");

  return (
    <div className="bg-surface-container rounded-lg p-3 border border-outline-variant">
      <div className="text-sm font-semibold text-on-surface">{customer.name}</div>
      <div className="text-xs text-on-surface-variant mt-1">{meta}</div>
    </div>
  );
}
```

- [ ] **Step 3: 전체 검증**

Run:
```powershell
cd frontend
pnpm typecheck
pnpm lint
pnpm lint:fsd
pnpm build
```
Expected: 모두 통과.

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/features/proposal/create frontend/src/entities/proposal
git commit -m "feat(proposal): replace manual entry with CustomerPicker in ProposalFormModal"
```

---

# Phase C: Verification & PR

## Task 10: 수동 스모크 테스트

**Files:** N/A

- [ ] **Step 1: 백엔드 + 프론트엔드 실행**

별도 터미널:
- `cd backend; ./gradlew.bat bootRun`
- `cd frontend; pnpm dev`

- [ ] **Step 2: AGENT1 시나리오**

1. AGENT1 로그인 → `/proposals` → "새 설계 등록"
2. 콤보박스 클릭 → dropdown 표시 (본인 고객 목록)
3. 일부 이름 입력 → 필터 동작
4. 고객 선택 → 미리보기 카드 표시 (이름/전화/생년월일/나이/성별)
5. 상품명/보험사/월 보험료 입력 → "등록"
6. 모달 닫힘, 목록에 신규 행 추가 (마스킹된 이름/전화 표시)

- [ ] **Step 3: 등록 고객 0명 케이스**

1. 신규 AGENT1 계정 또는 본인 고객 다 삭제한 상태로 진입
2. 콤보박스 dropdown → "등록된 고객이 없습니다. 먼저 [고객 관리]에서 등록하세요" 표시
3. 링크 클릭 → `/customers` 이동

- [ ] **Step 4: 검색 매칭 없음 케이스**

1. AGENT1로 어떤 고객 등록되어 있을 때
2. 콤보박스에 매칭 안 되는 문자열 입력 (예: "zzzzz")
3. "검색 결과가 없습니다…" 안내 + 링크 표시

- [ ] **Step 5: ADMIN 시나리오**

1. ADMIN 로그인 → `/proposals` → "새 설계 등록"
2. 콤보박스에 전체 고객 표시 (다른 agent의 고객도)
3. 임의 선택 후 등록 가능

- [ ] **Step 6: 모달 외부 클릭 dropdown 닫힘**

콤보박스 dropdown 열린 상태에서 모달 다른 부분 클릭 → dropdown 닫힘 (모달은 그대로).

문제 발견 시 해당 Task로 돌아가 수정.

---

## Task 11: PR 생성

**Files:** N/A

- [ ] **Step 1: 모든 커밋 검토**

Run: `git log master..HEAD --oneline`
Expected:
- V16 마이그레이션
- 백엔드 변경 (entity/DTO/service/controller/test)
- 프론트 CustomerPicker + entity API
- 프론트 ProposalFormModal 재구성

총 3~4 commits.

- [ ] **Step 2: 사용자에게 push 승인 요청**

(memory: feedback_git_push_confirm)

- [ ] **Step 3: push**

```bash
git push -u origin feat/proposal-customer-picker
```

- [ ] **Step 4: PR 생성**

```bash
gh pr create --base master --head feat/proposal-customer-picker --title "feat(proposal): require selecting registered customer in new proposal modal" --body "$(cat <<'EOF'
## Summary

새 설계 등록 모달에서 수기 입력 대신 등록된 고객 중에서만 선택. 백엔드에 \`proposals.customer_id\` FK 추가하고 생성 시점에 customer 정보를 스냅샷으로 보존.

## Spec / Plan

- Spec: \`docs/superpowers/specs/2026-05-21-proposal-customer-picker-design.md\`
- Plan: \`docs/superpowers/plans/2026-05-21-proposal-customer-picker.md\`

## Changes

### Backend
- V16 마이그레이션: \`proposals.customer_id\` FK with ON DELETE SET NULL + name/agent 매칭 백필
- \`Proposal\` entity: customerId 필드 + create() 시그니처 갱신
- \`ProposalCreateRequest\`: customerId 만 받음 (name/phone/birthDate 제거)
- \`ProposalDto\`: customerId 노출
- \`ProposalService.createProposal\`: customer 조회 + 권한 검증 + 스냅샷 저장
- \`ProposalController.create\`: isAdmin 전달
- 신규 \`ProposalControllerTest\` (4 케이스)

### Frontend
- 신규 \`entities/customer/ui/customer-picker\`: 검색 가능 콤보박스, 빈/매칭 없음 케이스 안내
- \`entities/customer\` Public API에 CustomerPicker export
- \`features/proposal/create/api\`: CreateRequest 시그니처 변경
- \`features/proposal/create/ui\`: 수기 입력 필드 제거, CustomerPicker + SelectedCustomerCard 추가
- \`entities/proposal\` PolicyDto에 customerId 노출

## Test plan

- [x] Backend tests: ProposalControllerTest (AGENT1 own/other/admin/404)
- [x] Frontend: typecheck / lint / lint:fsd / build 통과
- [x] 수동 스모크: AGENT1 신규 흐름, 빈/매칭 없음 안내, ADMIN, 외부 클릭 닫힘

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 5: CI 통과 + 머지**

```bash
gh pr view --json statusCheckRollup
```

통과 후 사용자에게 머지 방식 확인 (merge commit 권장).

```bash
gh pr merge <PR번호> --merge
```

- [ ] **Step 6: 브랜치 정리**

```bash
git checkout master
git pull --quiet
git branch -d feat/proposal-customer-picker
git push origin --delete feat/proposal-customer-picker
```

---

# 완료 후 점검

- [ ] master에 변경 반영
- [ ] CI (Steiger 포함) 통과
- [ ] Vercel 자동 배포 성공
- [ ] 운영 환경에서 ADMIN 또는 AGENT1 계정으로 새 설계 등록 한 번 시연

## Out of Scope (별도 작업)

- 콤보박스 키보드 네비게이션 (↑/↓/Enter/Esc)
- 서버 사이드 고객 검색
- 모달 내 "새 고객 등록" 인라인 흐름
- 기존 proposal의 customer 정보 동기화 (스냅샷 유지가 의도)
- proposal 페이지의 unmask 권한 (현재 마스킹된 표시 유지)
