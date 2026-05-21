# Underwriting (Policy) Create Design

- **Date**: 2026-05-21
- **Scope**: `/underwriting` 페이지에 신규 심사 등록 UI 추가. 백엔드에 POST `/api/policies` 엔드포인트와 PolicyCreateRequest DTO, 자동 정책번호 생성 로직 신설. 프론트는 `features/contract/create` 슬라이스 신설.
- **Goal**: 설계사가 보험사 심사 단계의 정책을 시스템에 직접 등록할 수 있도록 한다. 정책번호는 자동 생성, 초기 상태는 "심사 중" 고정.
- **Reference**:
  - 기존 패턴: `features/proposal/create` (Spec: `docs/superpowers/specs/2026-05-21-proposal-customer-picker-design.md`)
  - FSD 컨벤션: `docs/superpowers/specs/2026-05-19-frontend-fsd-migration-design.md`

---

## 1. 사용자 흐름

### 1.1 진입점

`/underwriting` 페이지 상단 (상태 필터 옆 또는 우측)에 **"+ 새 심사 등록"** 버튼 추가.

권한: ADMIN, AGENT1 (페이지 자체 접근 권한과 동일).

### 1.2 모달 구성

```
┌─────────────────────────────────────────────────┐
│  새 심사 등록                                [×]│
├─────────────────────────────────────────────────┤
│  고객 *                                         │
│  [🔍 고객명 또는 전화번호로 검색…           ]   │
│  ┌─ 선택된 고객 ────────────────────────────┐   │
│  │ 김OO                                     │   │
│  │ 010-1234-5678 · 1980-03-15 · 45세 · 남  │   │
│  └──────────────────────────────────────────┘   │
│                                                 │
│  상품명 *           [무배당 종신보험        ]   │
│  보험사 *           [삼성생명               ]   │
│  계약일 *           [2026-05-21             ]   │
│  월 보험료 (원) *   [150000                 ]   │
│                                                 │
│        [취소]              [등록]               │
└─────────────────────────────────────────────────┘
```

`CustomerPicker` + `SelectedCustomerCard`는 `features/proposal/create`의 패턴/컴포넌트를 그대로 활용. SelectedCustomerCard 컴포넌트는 features/proposal/create와 features/contract/create 양쪽에서 동일하게 사용 — 첫 구현에서는 그대로 복제(중복) 허용. 추후 두 곳 이상에서 재사용이 더 필요해지면 `entities/customer/ui/selected-customer-card`로 추출 (YAGNI 원칙).

### 1.3 필수/자동 필드

| 필드 | 사용자 입력 | 자동 처리 |
|---|---|---|
| 고객 (customerId) | ✓ 필수 | — |
| 상품명 | ✓ 필수 | — |
| 보험사 | ✓ 필수 | — |
| 계약일 | ✓ 필수 (default: 오늘) | — |
| 월 보험료 | ✓ 필수 (양수) | — |
| 정책번호 | — | 백엔드 자동 생성: `C-YYYY-MMDD-NNNN` |
| 상태 | — | "심사 중" 고정 |
| 고객 PII 스냅샷 | — | customer에서 가져와 저장 (proposal과 동일) |

### 1.4 검증

- 필수 필드 비면 zod에서 에러 표시
- 월 보험료: 양수 숫자
- 계약일: 유효한 날짜 (HTML `type="date"` + zod string format)
- "등록" 클릭 후 성공: 모달 닫고 `/underwriting` 테이블 자동 갱신 (React Query invalidate)
- 실패 시: 에러 메시지 (서버 응답 또는 일반 fallback)

---

## 2. Backend 변경

### 2.1 마이그레이션

**없음.** `policies` 테이블은 V2 이후로 모든 필요 컬럼을 가지고 있고, V14에서 `customer_id` FK도 추가됨. 추가 스키마 변경 불요.

### 2.2 DTO 신규

**`PolicyCreateRequest.java`**:
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

### 2.3 Policy entity — create() 시그니처 확인

기존 `Policy` 엔티티의 정적 팩토리에 customerId 파라미터 포함 여부 확인. 없으면 추가 (V14에서 customerId 컬럼이 추가됐는데 entity create()가 못 받으면 신규 정책에 NULL로 저장됨 — 이미 PR #15에서 customerId 필드는 추가되었지만 create() 시그니처 갱신은 별도 작업이었을 수 있음. 구현 시 확인).

`Policy.create()` 시그니처 (필요 시 수정):
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
) { ... }
```

### 2.4 PolicyService.createPolicy

```java
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
      customer.getName(),                // PII 스냅샷
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
```

정책번호 형식: `C-YYYY-MMDD-NNNN` (예: `C-2026-0521-0001`). NNNN은 해당 날짜의 일련번호 (0001부터).

### 2.5 PolicyRepository

기존 인터페이스에 추가:
```java
long countByPolicyNumberStartingWith(String prefix);
```

### 2.6 PolicyController

기존 컨트롤러에 POST 엔드포인트 추가:
```java
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

### 2.7 보안

`/api/policies/**` 권한 룰 (ADMIN/AGENT1) 그대로 적용 — POST도 자동 포함.

### 2.8 Backend 테스트

`PolicyControllerTest.java` 신규 (CustomerControllerTest / ProposalControllerTest 패턴):

| 케이스 | 기대 |
|---|---|
| AGENT1 본인 고객 customerId로 POST | 201 + policyNumber 자동 생성 + status="심사 중" |
| AGENT1 타인 고객 customerId | 403 |
| 존재하지 않는 customerId | 404 |
| ADMIN 임의 customer | 201 |
| 같은 날 2건 등록 시 일련번호 0001, 0002 | DB 검증 |
| 정책번호 형식 정규식 `^C-\d{4}-\d{4}-\d{4}$` | 응답 검증 |
| 응답에 PII 마스킹 적용 (customerName "김○○" 등) | 검증 |

### 2.9 동시성

같은 날 동시 등록 시 일련번호 race 가능. MVP에선 트랜잭션 + UNIQUE constraint(`policy_number`)로 충돌 시 예외 → 재시도 안 함 (희박한 케이스). 추후 본격 필요 시 atomic sequence 또는 DB function으로 개선.

---

## 3. Frontend 변경

### 3.1 신규 슬라이스 `features/contract/create`

```
frontend/src/features/contract/create/
├── api/index.ts        # createPolicy(req) + CreatePolicyRequest
├── model/index.ts      # useCreatePolicy() mutation
├── ui/index.tsx        # PolicyFormModal
└── index.ts            # Public API
```

이름 규칙: entity 폴더가 `contract`이므로 features도 `contract/create` (FSD 일관성).

### 3.2 api/index.ts

```ts
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

### 3.3 model/index.ts

```ts
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createPolicy, type CreatePolicyRequest } from "../api";

export function useCreatePolicy() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CreatePolicyRequest) => createPolicy(req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["policies"] });
      qc.invalidateQueries({ queryKey: ["health-analyses"] });  // 새 정책의 행에 분석 컬럼 갱신
    },
  });
}
```

### 3.4 ui/index.tsx (PolicyFormModal)

`features/proposal/create/ui/index.tsx`와 거의 동일 패턴. 차이점:
- 제목: "새 심사 등록"
- 필드에 계약일 추가 (`type="date"`, default 오늘)
- mutation: `useCreatePolicy`

기본값 처리:
```ts
const defaultContractDate = new Date().toISOString().slice(0, 10);  // "2026-05-21"
```

zod schema:
```ts
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
```

`SelectedCustomerCard`는 proposal 모듈과 동일한 내용 — 본 모듈에 복제(YAGNI). 추출 필요 시 별도 작업.

### 3.5 index.ts (Public API)

```ts
export { PolicyFormModal } from "./ui";
export { useCreatePolicy } from "./model";
export type { CreatePolicyRequest } from "./api";
```

### 3.6 app/underwriting/page.tsx 변경

페이지 상단 (상태 필터 옆 또는 위)에 "+ 새 심사 등록" 버튼 추가:

```tsx
const [showCreateModal, setShowCreateModal] = useState(false);

// 헤더 영역
<div className="flex items-center justify-between mb-4">
  <select value={status} onChange={handleStatusChange}>...</select>
  <button
    type="button"
    onClick={() => setShowCreateModal(true)}
    className="flex items-center gap-2 px-4 py-2 rounded-xl bg-primary-container text-on-primary text-sm font-semibold"
  >
    <Plus size={16} />
    새 심사 등록
  </button>
</div>

// 모달 (페이지 본문 끝에)
{showCreateModal && (
  <PolicyFormModal onClose={() => setShowCreateModal(false)} />
)}
```

기존 분석 모달 흐름 그대로 유지.

---

## 4. 테스트

### 4.1 Backend 통합 테스트 (PolicyControllerTest)

위 2.8의 7개 케이스. CustomerControllerTest 패턴 따라 작성.

### 4.2 Frontend 수동 스모크

1. ADMIN 또는 AGENT1으로 로그인 → `/underwriting`
2. "+ 새 심사 등록" 버튼 클릭 → 모달 오픈
3. 고객 선택 → 미리보기 카드
4. 상품명 / 보험사 / 계약일 / 보험료 입력
5. "등록" 클릭 → 모달 닫힘
6. 테이블 상단(혹은 페이지 갱신 후)에 신규 행 표시: 정책번호 `C-2026-MMDD-NNNN`, 상태 "심사 중"
7. 그 행의 "분석" 컬럼은 빈 상태 ("+ 분석 요청") — customer_id가 연결되어 있어야 분석 가능 (customer 등록 후 등록한 정책이므로 정상)
8. AGENT1으로 본인 고객으로 등록 → 정상
9. AGENT1으로 다른 사람 고객 id 직접 호출 (URL 조작) → 403

---

## 5. 리스크 & 대응

| 리스크 | 대응 |
|---|---|
| 같은 날 동시 등록 시 정책번호 일련번호 충돌 | `policies.policy_number` UNIQUE 제약 활용. 충돌 시 예외 (드문 케이스) — 재시도 로직은 YAGNI |
| 정책번호 형식 향후 변경 | `generatePolicyNumber` 한 메서드만 수정. 추후 prefix/format 설정 외부화 검토 |
| customer 삭제 후 신규 정책 등록 시도 | customer 조회 실패 → 404. 정상 동작 |
| SelectedCustomerCard 중복 (proposal/create와 contract/create 양쪽) | 의도된 YAGNI. 향후 추출 |

---

## 6. Out of Scope

- 정책 수정/삭제 UI
- 상태 전이 워크플로 (심사 중 → 승인 완료 등)
- proposal → policy 자동 연계
- 정책번호 형식 커스터마이징 UI
- 정책번호 일련번호 atomic 처리 (race condition)
- 보험상품 마스터 (상품명/보험사 dropdown)
- SelectedCustomerCard 공용 컴포넌트 추출

---

## 7. 진행 순서

- 별도 PR: `feat/underwriting-create`
- master에서 분기 (다른 in-flight 작업 없음)
- 순차: Backend → Frontend → 검증 → PR
