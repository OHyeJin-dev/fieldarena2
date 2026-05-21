# Underwriting (Policy) Create Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `/underwriting` 페이지에 "+ 새 심사 등록" 버튼을 추가하고, customer 선택 + 상품/보험사/계약일/보험료 입력으로 정책을 등록한다. 정책번호는 백엔드가 `C-YYYY-MMDD-NNNN` 형식으로 자동 생성, 초기 상태는 "심사 중" 고정.

**Architecture:** 백엔드는 `POST /api/policies` 엔드포인트 + PolicyCreateRequest DTO + Policy.create() 정적 팩토리 + 정책번호 자동 생성 로직 + 권한 검증(AGENT1 본인 고객만)을 추가한다. 프론트엔드는 `features/contract/create` 슬라이스를 신설하고 `/underwriting` 페이지에 모달 트리거 버튼을 추가한다. CustomerPicker는 entities/customer에서 재사용.

**Tech Stack:** Spring Boot 3.5, Spring Data JPA, JUnit 5 + MockMvc, Next.js 16 (App Router), React Query, react-hook-form + zod, Tailwind v4, pnpm.

**Reference Spec:** `docs/superpowers/specs/2026-05-21-underwriting-create-design.md`

**Branch:** `feat/underwriting-create` (master에서 분기).

---

## File Structure

### 신규 (Backend)

```
backend/src/main/java/com/agentsupport/policy/dto/PolicyCreateRequest.java
backend/src/test/java/com/agentsupport/policy/PolicyControllerTest.java
```

### 수정 (Backend)

```
backend/src/main/java/com/agentsupport/policy/
├── entity/Policy.java                    # static create() 팩토리 추가
├── repository/PolicyRepository.java      # countByPolicyNumberStartingWith
├── service/PolicyService.java            # createPolicy() 메서드 추가 (CustomerRepository 주입)
└── PolicyController.java                 # @PostMapping
```

### 신규 (Frontend)

```
frontend/src/features/contract/create/
├── api/index.ts
├── model/index.ts
├── ui/index.tsx                          # PolicyFormModal
└── index.ts
```

### 수정 (Frontend)

```
frontend/src/app/underwriting/page.tsx    # "+ 새 심사 등록" 버튼 + 모달 통합
```

---

## Conventions

- **Working dir**: `d:\fieldarena2`. 백엔드 `cd backend`, 프론트 `cd frontend`.
- **Branch**: `git checkout -b feat/underwriting-create` (시작 전 master 최신).
- **Verification**:
  - Backend: `cd backend; ./gradlew.bat build -x spotlessCheck`
  - Frontend: `cd frontend; pnpm typecheck; pnpm lint; pnpm lint:fsd; pnpm build`
- **Pre-commit hook**: Husky가 `pnpm lint:fsd` 자동 실행.
- **Backend**: 마이그레이션 변경 없음 (policies 테이블 기존 그대로).

---

# Phase A: Backend

## Task 1: Policy.create() 팩토리 + PolicyCreateRequest DTO

**Files:**
- Modify: `backend/src/main/java/com/agentsupport/policy/entity/Policy.java`
- Create: `backend/src/main/java/com/agentsupport/policy/dto/PolicyCreateRequest.java`

- [ ] **Step 1: Policy.create() 정적 팩토리 추가**

Edit `backend/src/main/java/com/agentsupport/policy/entity/Policy.java` — `protected Policy() {}` 바로 아래에 추가:

```java
  public static Policy create(
      String policyNumber,
      String agentId,
      UUID customerId,
      String customerName,
      String productName,
      String insurerName,
      String status,
      LocalDate contractDate,
      BigDecimal monthlyPremium
  ) {
    Policy p = new Policy();
    p.policyNumber = policyNumber;
    p.agentId = agentId;
    p.customerId = customerId;
    p.customerName = customerName;
    p.productName = productName;
    p.insurerName = insurerName;
    p.status = status;
    p.contractDate = contractDate;
    p.monthlyPremium = monthlyPremium;
    return p;
  }
```

- [ ] **Step 2: PolicyCreateRequest DTO 작성**

Create `backend/src/main/java/com/agentsupport/policy/dto/PolicyCreateRequest.java`:

```java
package com.agentsupport.policy.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PolicyCreateRequest(
    @NotNull UUID customerId,
    @NotBlank String productName,
    @NotBlank String insurerName,
    @NotNull LocalDate contractDate,
    @NotNull @Positive BigDecimal monthlyPremium
) {}
```

- [ ] **Step 3: 컴파일 확인**

Run: `cd backend; ./gradlew.bat compileJava`
Expected: 성공 (Service는 아직 안 만들었지만 위 변경은 단독 컴파일 가능).

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/agentsupport/policy/entity/Policy.java backend/src/main/java/com/agentsupport/policy/dto/PolicyCreateRequest.java
git commit -m "feat(policy): add create() factory and PolicyCreateRequest DTO"
```

---

## Task 2: PolicyRepository.countByPolicyNumberStartingWith

**Files:**
- Modify: `backend/src/main/java/com/agentsupport/policy/repository/PolicyRepository.java`

- [ ] **Step 1: 메서드 추가**

Edit PolicyRepository — 기존 interface 본문에 추가 (다른 메서드들 사이 아무 곳):

```java
  long countByPolicyNumberStartingWith(String prefix);
```

(Spring Data JPA 쿼리 메서드 명명 규칙에 따라 자동 구현됨.)

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend; ./gradlew.bat compileJava`
Expected: 성공.

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/agentsupport/policy/repository/PolicyRepository.java
git commit -m "feat(policy): add countByPolicyNumberStartingWith for sequence generation"
```

---

## Task 3: PolicyControllerTest 작성 + PolicyService.createPolicy + PolicyController POST

**Files:**
- Create: `backend/src/test/java/com/agentsupport/policy/PolicyControllerTest.java`
- Modify: `backend/src/main/java/com/agentsupport/policy/service/PolicyService.java`
- Modify: `backend/src/main/java/com/agentsupport/policy/PolicyController.java`

TDD: 테스트 먼저 (실패) → 구현 → 통과.

- [ ] **Step 1: PolicyControllerTest 작성 (실패 예상)**

Read `backend/src/test/java/com/agentsupport/customer/CustomerControllerTest.java` 또는 `backend/src/test/java/com/agentsupport/proposal/ProposalControllerTest.java` 먼저 — fixture(세션 셋업, customer 생성 헬퍼) 패턴을 그대로 따라 작성.

Create `backend/src/test/java/com/agentsupport/policy/PolicyControllerTest.java`:

```java
package com.agentsupport.policy;

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
class PolicyControllerTest {

  @Autowired WebApplicationContext webApplicationContext;
  MockMvc mockMvc;
  MockHttpSession adminSession;
  MockHttpSession agent1Session;

  // 세션/사용자 셋업 + customer 생성 헬퍼: CustomerControllerTest/ProposalControllerTest 패턴 그대로 복사.
  // adminSession, agent1Session, createCustomerAs(session, name) 등.

  @BeforeEach
  void setUp() throws Exception {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
        .apply(springSecurity())
        .build();
    // adminSession / agent1Session 셋업: 기존 패턴 복사
  }

  @Test
  void agent1CanCreatePolicyForOwnCustomer() throws Exception {
    UUID customerId = createCustomerAs(agent1Session, "김고객");

    String body = """
        {
          "customerId": "%s",
          "productName": "무배당 종신보험",
          "insurerName": "삼성생명",
          "contractDate": "2026-05-21",
          "monthlyPremium": 150000
        }
        """.formatted(customerId);

    mockMvc.perform(post("/api/policies")
            .session(agent1Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.customerId").value(customerId.toString()))
        .andExpect(jsonPath("$.status").value("심사 중"))
        .andExpect(jsonPath("$.policyNumber").exists());
  }

  @Test
  void agent1CannotCreatePolicyForOtherAgentCustomer() throws Exception {
    UUID customerId = createCustomerAs(adminSession, "다른고객");

    String body = """
        {
          "customerId": "%s",
          "productName": "X",
          "insurerName": "Y",
          "contractDate": "2026-05-21",
          "monthlyPremium": 1000
        }
        """.formatted(customerId);

    mockMvc.perform(post("/api/policies")
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
          "contractDate": "2026-05-21",
          "monthlyPremium": 1000
        }
        """.formatted(fakeId);

    mockMvc.perform(post("/api/policies")
            .session(agent1Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isNotFound());
  }

  @Test
  void adminCanCreatePolicyForAnyCustomer() throws Exception {
    UUID customerId = createCustomerAs(agent1Session, "유저");

    String body = """
        {
          "customerId": "%s",
          "productName": "X",
          "insurerName": "Y",
          "contractDate": "2026-05-21",
          "monthlyPremium": 1000
        }
        """.formatted(customerId);

    mockMvc.perform(post("/api/policies")
            .session(adminSession)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated());
  }

  @Test
  void policyNumberFollowsExpectedFormat() throws Exception {
    UUID customerId = createCustomerAs(agent1Session, "포맷테스트");

    String body = """
        {
          "customerId": "%s",
          "productName": "X",
          "insurerName": "Y",
          "contractDate": "2026-05-21",
          "monthlyPremium": 1000
        }
        """.formatted(customerId);

    mockMvc.perform(post("/api/policies")
            .session(agent1Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.policyNumber").value(org.hamcrest.Matchers.matchesPattern("^C-\\d{4}-\\d{4}-\\d{4}$")));
  }

  @Test
  void sequentialPoliciesOnSameDateGetIncrementingNumbers() throws Exception {
    UUID customerId = createCustomerAs(agent1Session, "연번고객");

    String body1 = """
        {
          "customerId": "%s",
          "productName": "X",
          "insurerName": "Y",
          "contractDate": "2026-05-21",
          "monthlyPremium": 1000
        }
        """.formatted(customerId);

    String first = mockMvc.perform(post("/api/policies")
            .session(agent1Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body1))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    String second = mockMvc.perform(post("/api/policies")
            .session(agent1Session)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body1))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    JsonMapper m = JsonMapper.builder().build();
    String n1 = m.readTree(first).get("policyNumber").asText();
    String n2 = m.readTree(second).get("policyNumber").asText();
    org.assertj.core.api.Assertions.assertThat(n1).endsWith("-0001");
    org.assertj.core.api.Assertions.assertThat(n2).endsWith("-0002");
  }

  // createCustomerAs helper: CustomerControllerTest의 동일 메서드 그대로 복사
  private UUID createCustomerAs(MockHttpSession session, String name) throws Exception {
    // 실제 구현은 CustomerControllerTest를 참조하여 mockMvc로 POST /api/customers 호출 후
    // 응답 body에서 id 추출하는 패턴 그대로
    throw new UnsupportedOperationException("copy helper from CustomerControllerTest");
  }
}
```

**중요**: `createCustomerAs` 헬퍼와 세션 셋업 헬퍼는 `CustomerControllerTest.java` (또는 `ProposalControllerTest.java`) 의 패턴을 그대로 복사. 위 placeholder는 실제 구현 시 그 파일의 코드로 대체.

- [ ] **Step 2: 테스트 실행 (실패 예상)**

Run: `cd backend; ./gradlew.bat test --tests PolicyControllerTest`
Expected: 컴파일 에러 (`PolicyService.createPolicy` 미존재) 또는 모든 케이스 실패.

- [ ] **Step 3: PolicyService에 createPolicy 추가**

Read 기존 `backend/src/main/java/com/agentsupport/policy/service/PolicyService.java` 후 다음으로 교체 (CustomerRepository 주입 추가, createPolicy 메서드 추가):

```java
package com.agentsupport.policy.service;

import com.agentsupport.common.dto.PageResponse;
import com.agentsupport.customer.entity.Customer;
import com.agentsupport.customer.repository.CustomerRepository;
import com.agentsupport.policy.dto.PolicyCreateRequest;
import com.agentsupport.policy.dto.PolicyDto;
import com.agentsupport.policy.entity.Policy;
import com.agentsupport.policy.repository.PolicyRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class PolicyService {

  private final PolicyRepository policyRepository;
  private final CustomerRepository customerRepository;

  public PolicyService(PolicyRepository policyRepository, CustomerRepository customerRepository) {
    this.policyRepository = policyRepository;
    this.customerRepository = customerRepository;
  }

  // ... 기존 findPolicies 메서드 그대로 유지. 기존 시그니처/구현 파악 후 보존.

  @Transactional
  public PolicyDto createPolicy(String agentId, boolean isAdmin, PolicyCreateRequest req) {
    Customer customer = customerRepository.findById(req.customerId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "customer not found"));

    if (!isAdmin && !customer.getAgentId().equals(agentId)) {
      throw new AccessDeniedException("not allowed");
    }

    String policyNumber = generatePolicyNumber(req.contractDate());

    Policy policy = Policy.create(
        policyNumber,
        agentId,
        customer.getId(),
        customer.getName(),
        req.productName(),
        req.insurerName(),
        "심사 중",
        req.contractDate(),
        req.monthlyPremium()
    );

    return PolicyDto.from(policyRepository.save(policy));
  }

  private String generatePolicyNumber(LocalDate contractDate) {
    String prefix = "C-" + contractDate.format(DateTimeFormatter.ofPattern("yyyy-MMdd")) + "-";
    long count = policyRepository.countByPolicyNumberStartingWith(prefix);
    return prefix + String.format("%04d", count + 1);
  }
}
```

**중요**: 기존 findPolicies 메서드와 그 시그니처는 보존해야 함. 위 코드는 신규 부분만. 기존 메서드를 삭제하지 말 것.

- [ ] **Step 4: PolicyController POST 엔드포인트 추가**

Edit `backend/src/main/java/com/agentsupport/policy/PolicyController.java` — 기존 GET 메서드 옆에 추가:

```java
import com.agentsupport.policy.dto.PolicyCreateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PolicyDto create(
      Authentication auth,
      @Valid @RequestBody PolicyCreateRequest request
  ) {
    boolean isAdmin = auth.getAuthorities().stream()
        .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    return policyService.createPolicy(auth.getName(), isAdmin, request);
  }
```

(import는 기존 import 블록에 정렬해서 추가)

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd backend; ./gradlew.bat test --tests PolicyControllerTest`
Expected: 6/6 PASS.

- [ ] **Step 6: 전체 빌드 확인**

Run: `cd backend; ./gradlew.bat build -x spotlessCheck`
Expected: 모든 테스트 통과 (기존 + 신규).

- [ ] **Step 7: 커밋**

```bash
git add backend/src/main/java/com/agentsupport/policy backend/src/test/java/com/agentsupport/policy
git commit -m "feat(policy): add POST endpoint with auto policy number generation"
```

---

# Phase B: Frontend

## Task 4: features/contract/create/api/index.ts

**Files:**
- Create: `frontend/src/features/contract/create/api/index.ts`

- [ ] **Step 1: 폴더 생성**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/features/contract/create/api -Force
```

- [ ] **Step 2: api/index.ts 작성**

```ts
// frontend/src/features/contract/create/api/index.ts
import { apiFetch } from "@/shared/api";
import type { PolicyDto } from "@/entities/contract";

export interface CreatePolicyRequest {
  customerId: string;
  productName: string;
  insurerName: string;
  contractDate: string;       // "YYYY-MM-DD"
  monthlyPremium: number;
}

export function createPolicy(req: CreatePolicyRequest): Promise<PolicyDto> {
  return apiFetch<PolicyDto>("/api/policies", {
    method: "POST",
    body: JSON.stringify(req),
  });
}
```

- [ ] **Step 3: 타입체크**

Run: `cd frontend; pnpm typecheck`
Expected: 통과.

---

## Task 5: features/contract/create/model/index.ts

**Files:**
- Create: `frontend/src/features/contract/create/model/index.ts`

- [ ] **Step 1: 폴더 생성**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/features/contract/create/model -Force
```

- [ ] **Step 2: model/index.ts 작성**

```ts
// frontend/src/features/contract/create/model/index.ts
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createPolicy, type CreatePolicyRequest } from "../api";

export function useCreatePolicy() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CreatePolicyRequest) => createPolicy(req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["policies"] });
      qc.invalidateQueries({ queryKey: ["health-analyses"] });
    },
  });
}
```

- [ ] **Step 3: 타입체크**

Run: `cd frontend; pnpm typecheck`
Expected: 통과.

---

## Task 6: features/contract/create/ui/index.tsx (PolicyFormModal)

**Files:**
- Create: `frontend/src/features/contract/create/ui/index.tsx`

- [ ] **Step 1: 폴더 생성**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/features/contract/create/ui -Force
```

- [ ] **Step 2: ui/index.tsx 작성**

```tsx
// frontend/src/features/contract/create/ui/index.tsx
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
import { useCreatePolicy } from "../model";

const schema = z.object({
  customerId: z.string().min(1, "고객을 선택하세요"),
  productName: z.string().min(1, "상품명을 입력하세요"),
  insurerName: z.string().min(1, "보험사를 입력하세요"),
  contractDate: z.string().min(1, "계약일을 선택하세요"),
  monthlyPremium: z
    .string()
    .min(1, "보험료를 입력하세요")
    .refine((v) => !isNaN(Number(v)) && Number(v) > 0, "올바른 금액을 입력하세요"),
});

type FormValues = z.infer<typeof schema>;

interface Props {
  onClose: () => void;
}

export function PolicyFormModal({ onClose }: Props) {
  const { mutate, isPending } = useCreatePolicy();
  const [selectedCustomer, setSelectedCustomer] = useState<CustomerDto | null>(null);

  const today = new Date().toISOString().slice(0, 10);

  const {
    register,
    handleSubmit,
    control,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { contractDate: today },
  });

  function onSubmit(values: FormValues) {
    mutate(
      {
        customerId: values.customerId,
        productName: values.productName,
        insurerName: values.insurerName,
        contractDate: values.contractDate,
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
          <h2 className="text-base font-semibold text-on-surface">새 심사 등록</h2>
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
            <div className="flex flex-col gap-1.5">
              <label className="text-sm text-on-surface-variant">고객 *</label>
              <Controller
                control={control}
                name="customerId"
                render={({ field, fieldState }) => (
                  <CustomerPicker
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
              label="계약일"
              type="date"
              error={errors.contractDate?.message}
              {...register("contractDate")}
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

(`SelectedCustomerCard`는 `features/proposal/create/ui/index.tsx`와 동일한 내용 — YAGNI에 따라 복제 허용. 향후 공용 추출은 별도 작업.)

- [ ] **Step 3: 타입체크**

Run: `cd frontend; pnpm typecheck`
Expected: 통과.

---

## Task 7: features/contract/create Public API

**Files:**
- Create: `frontend/src/features/contract/create/index.ts`

- [ ] **Step 1: index.ts 작성**

```ts
// frontend/src/features/contract/create/index.ts
export { PolicyFormModal } from "./ui";
export { useCreatePolicy } from "./model";
export type { CreatePolicyRequest } from "./api";
```

- [ ] **Step 2: 전체 검증**

Run:
```powershell
cd frontend
pnpm typecheck
pnpm lint
pnpm lint:fsd
pnpm build
```
Expected: 모두 통과.

- [ ] **Step 3: 커밋 (Phase B 1차 — 슬라이스 신설)**

```bash
git add frontend/src/features/contract/create
git commit -m "feat(frontend): add features/contract/create slice with PolicyFormModal"
```

---

## Task 8: app/underwriting/page.tsx — 등록 버튼 + 모달 통합

**Files:**
- Modify: `frontend/src/app/underwriting/page.tsx`

- [ ] **Step 1: imports 추가**

Edit `frontend/src/app/underwriting/page.tsx` — 기존 imports 블록 안에 추가:

```ts
import { Plus } from "lucide-react";
import { PolicyFormModal } from "@/features/contract/create";
```

(이미 `useState`는 임포트되어 있을 가능성 높음. 없으면 추가.)

- [ ] **Step 2: 모달 open state 추가**

페이지 컴포넌트 본문 안 (다른 useState 옆):

```tsx
const [showCreateModal, setShowCreateModal] = useState(false);
```

- [ ] **Step 3: 상태 필터 영역에 "+ 새 심사 등록" 버튼 추가**

기존 상태 필터 select 옆 또는 위 (페이지 헤더 영역). 정확한 위치는 기존 layout 보고 결정. 다음 패턴 사용:

기존:
```tsx
<div className="mb-4">
  <select ...>...</select>
</div>
```

변경:
```tsx
<div className="mb-4 flex items-center justify-between">
  <select
    aria-label="상태 필터"
    value={status || "전체"}
    onChange={(e) => handleStatusChange(e.target.value)}
    className="px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest text-sm text-on-surface outline-none focus:border-primary-container"
  >
    {STATUS_OPTIONS.map((opt) => (
      <option key={opt} value={opt}>{opt}</option>
    ))}
  </select>
  <button
    type="button"
    onClick={() => setShowCreateModal(true)}
    className="flex items-center gap-2 px-4 py-2 rounded-xl bg-primary-container text-on-primary text-sm font-semibold hover:opacity-90 transition-opacity"
  >
    <Plus size={16} />
    새 심사 등록
  </button>
</div>
```

**중요**: 위 select 부분은 기존 코드와 일치해야 함. `cat` 또는 Read로 기존 select를 정확히 확인 후 그 외 부분을 새 div로 감싸는 식으로 변경.

- [ ] **Step 4: 모달 렌더 추가 (페이지 본문 끝부분)**

기존 분석 모달 렌더 옆 또는 페이지 최하단 (closing `</div>` 직전):

```tsx
{showCreateModal && (
  <PolicyFormModal onClose={() => setShowCreateModal(false)} />
)}
```

- [ ] **Step 5: 전체 검증**

Run:
```powershell
cd frontend
pnpm typecheck
pnpm lint
pnpm lint:fsd
pnpm build
```
Expected: 모두 통과.

- [ ] **Step 6: 커밋**

```bash
git add frontend/src/app/underwriting/page.tsx
git commit -m "feat(underwriting): add 새 심사 등록 button and modal trigger"
```

---

# Phase C: Verification & PR

## Task 9: 수동 스모크 테스트

**Files:** N/A

- [ ] **Step 1: 백엔드 + 프론트 실행**

별도 터미널:
- `cd backend; ./gradlew.bat bootRun`
- `cd frontend; pnpm dev` (이미 실행 중이면 그대로)

- [ ] **Step 2: ADMIN 시나리오**

1. ADMIN으로 로그인 → `/underwriting`
2. 상단에 **"+ 새 심사 등록"** 버튼 확인
3. 클릭 → 모달 오픈 ("새 심사 등록")
4. 고객 선택 (CustomerPicker로 검색 + 선택)
5. 선택된 고객 카드 표시 확인
6. 상품명 입력 (예: "테스트 상품")
7. 보험사 입력 (예: "테스트 보험사")
8. 계약일: 기본값 오늘 확인. 다른 날짜로 변경 가능 검증
9. 월 보험료 입력 (예: 100000)
10. "등록" 클릭 → 모달 닫힘
11. 테이블 첫 페이지에 신규 행 표시:
   - 정책번호 = `C-YYYY-MMDD-NNNN` 형식
   - 상태 = "심사 중"
   - 고객명 / 상품 / 보험사 / 계약일 / 보험료 일치
12. 같은 날짜로 한 번 더 등록 → 정책번호 NNNN이 0002 (또는 +1) 증가

- [ ] **Step 3: AGENT1 시나리오 (계정 있을 때)**

1. AGENT1 로그인 → `/underwriting`
2. CustomerPicker에 본인 고객만 보임
3. 등록 정상 동작
4. URL/콘솔에서 다른 사람 customerId 강제 POST → 403

- [ ] **Step 4: 유효성 검증**

1. 필수 비우고 등록 → 각 필드 에러 메시지
2. 보험료에 음수 → 에러
3. 계약일 비우면 → 에러 ("계약일을 선택하세요")

- [ ] **Step 5: 분석 흐름과 통합**

1. 등록된 신규 정책 행의 "분석" 컬럼 = "+ 분석 요청" 링크 (customer_id가 채워졌으므로)
2. 클릭 → AnalysisRequestModal 정상 동작 (기존 흐름)

문제 발생 시 해당 Task로 돌아가 수정.

---

## Task 10: Push + PR + 머지

**Files:** N/A

- [ ] **Step 1: 커밋 검토**

Run: `git log master..HEAD --oneline`
Expected:
- Task 1: feat(policy): add create() factory and PolicyCreateRequest DTO
- Task 2: feat(policy): add countByPolicyNumberStartingWith for sequence generation
- Task 3: feat(policy): add POST endpoint with auto policy number generation
- Task 7: feat(frontend): add features/contract/create slice with PolicyFormModal
- Task 8: feat(underwriting): add 새 심사 등록 button and modal trigger

총 5 commits.

- [ ] **Step 2: 사용자 push 승인 요청**

(memory: feedback_git_push_confirm)

- [ ] **Step 3: push**

```bash
git push -u origin feat/underwriting-create
```

- [ ] **Step 4: PR 생성**

```bash
gh pr create --base master --head feat/underwriting-create --title "feat(underwriting): add 새 심사 등록 modal with auto policy number" --body "$(cat <<'EOF'
## Summary

/underwriting 페이지에 "+ 새 심사 등록" 버튼 + 모달 추가. 정책번호는 백엔드가 \`C-YYYY-MMDD-NNNN\` 형식으로 자동 생성. 초기 상태 "심사 중" 고정.

## Spec / Plan

- Spec: \`docs/superpowers/specs/2026-05-21-underwriting-create-design.md\`
- Plan: \`docs/superpowers/plans/2026-05-21-underwriting-create.md\`

## Changes

### Backend
- \`Policy.create()\` 정적 팩토리 추가
- \`PolicyCreateRequest\` DTO 신규 (customerId / productName / insurerName / contractDate / monthlyPremium)
- \`PolicyRepository.countByPolicyNumberStartingWith\` 일련번호 조회
- \`PolicyService.createPolicy\`: customer 조회 + 권한 검증 + 정책번호 자동 생성 + 스냅샷 저장
- \`PolicyController\` POST \`/api/policies\` 추가
- \`PolicyControllerTest\` 6 케이스 (own/other/admin/404/format/sequence)

### Frontend
- 신규 \`features/contract/create\` 슬라이스 (api/model/ui)
- \`PolicyFormModal\` (CustomerPicker 재사용)
- \`/underwriting\` 페이지에 등록 버튼 + 모달 통합
- React Query: 등록 성공 시 \`policies\` + \`health-analyses\` 캐시 무효화

## Test plan

- [x] Backend tests: PolicyControllerTest 6/6 PASS
- [x] Frontend: typecheck / lint / lint:fsd / build 통과
- [x] 수동 스모크: ADMIN/AGENT1 등록 흐름 + 권한 + 정책번호 형식 + 분석 컬럼 통합

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 5: CI 통과 + 머지**

Run: `gh pr view <PR번호> --json statusCheckRollup --jq '[.statusCheckRollup[] | select(.name == "ci") | .conclusion] | first'`

CI SUCCESS 시:
```bash
gh pr merge <PR번호> --merge
```

- [ ] **Step 6: 브랜치 정리**

```bash
git checkout master
git pull --quiet
git branch -d feat/underwriting-create
git push origin --delete feat/underwriting-create
```

---

# 완료 후 점검

- [ ] master 반영
- [ ] CI (Steiger 포함) 통과
- [ ] Vercel 자동 배포 성공
- [ ] 운영 환경에서 ADMIN/AGENT1으로 "+ 새 심사 등록" 흐름 1회 시연

## Out of Scope (별도 작업)

- 정책 수정/삭제 UI
- 상태 전이 워크플로 (심사 중 → 승인 완료 등)
- proposal → policy 자동 연계
- 정책번호 형식 커스터마이징 UI
- 정책번호 atomic 처리 (race condition)
- 보험상품 마스터 (상품명/보험사 dropdown)
- SelectedCustomerCard 공용 컴포넌트 추출
