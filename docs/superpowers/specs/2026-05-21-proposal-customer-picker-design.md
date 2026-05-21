# Proposal Customer Picker Design

- **Date**: 2026-05-21
- **Scope**: `새 설계 등록` 모달에서 고객을 자유 입력 대신 **등록된 고객 중에서만 선택**하도록 변경. 백엔드에 `proposals.customer_id` FK 추가 + 생성 시점 스냅샷 유지.
- **Goal**: 설계 생성 시점에 고객 식별을 정확히 하고 customer ↔ proposal 추적 가능하도록 하면서, 과거 설계서의 고객 정보 스냅샷은 보존.
- **Reference**:
  - FSD 컨벤션: `docs/superpowers/specs/2026-05-19-frontend-fsd-migration-design.md`
  - Policy customer_id 패턴: `docs/superpowers/specs/2026-05-20-underwriting-health-analysis-design.md` (V14 마이그레이션)

---

## 1. 사용자 흐름

### 1.1 진입점

- 변경 없음. AGENT1/ADMIN이 `/proposals` 페이지 우상단 "새 설계 등록" 버튼 클릭 → 모달 오픈.

### 1.2 새 모달 구성

```
┌─────────────────────────────────────────────────┐
│  새 설계 등록                              [×]  │
├─────────────────────────────────────────────────┤
│                                                 │
│  고객 *                                         │
│  ┌───────────────────────────────────────────┐  │
│  │ 🔍 고객명 또는 전화번호로 검색…           │  │
│  └───────────────────────────────────────────┘  │
│                                                 │
│  ┌─ 선택된 고객 ──────────────────────────────┐ │
│  │ 김OO                                       │ │
│  │ 010-1234-5678 · 1980-03-15 · 45세 · 남    │ │
│  └────────────────────────────────────────────┘ │
│                                                 │
│  상품명 *           [무배당 종신보험        ]   │
│  보험사 *           [삼성생명               ]   │
│  월 보험료 (원) *   [150000                 ]   │
│                                                 │
│        [취소]              [등록]               │
└─────────────────────────────────────────────────┘
```

### 1.3 콤보박스 동작

- input 칸에 타이핑 → 매칭되는 고객을 dropdown으로 표시
- 매칭 기준: `name.includes(query) || phone.includes(query)`
- dropdown 행: 이름 굵게 + 전화 회색
- 외부 클릭 시 dropdown 닫힘 (`useRef` + document click 리스너)
- 마우스 클릭으로 선택. 키보드 네비게이션은 Out of Scope.

### 1.4 빈 상태 안내

| 상황 | 표시 |
|---|---|
| 등록 고객 0명 | dropdown 클릭 시 "등록된 고객이 없습니다. 먼저 [고객 관리]에서 등록하세요" + `/customers` 링크 |
| 검색 매칭 없음 | dropdown에 "검색 결과가 없습니다. [고객 관리]에서 등록 후 다시 시도하세요" + 링크 |

### 1.5 선택 후

- "선택된 고객" 카드가 콤보박스 아래에 표시
- 표시 정보: 이름 / 전화 · 생년월일 · 나이 · 성별 (있는 정보만 · 구분)
- read-only — 편집 불가
- 콤보박스 input 다시 클릭하면 dropdown 재오픈 → 다른 고객 선택 가능

### 1.6 폼 검증

- `customerId`: 필수 (고객을 선택하세요)
- `productName`, `insurerName`: 비어있지 않음
- `monthlyPremium`: 양수 숫자

선택 미완료 시 "등록" 버튼은 disabled.

---

## 2. Backend 변경

### 2.1 V16 마이그레이션

`backend/src/main/resources/db/migration/V16__add_customer_id_to_proposals.sql`:

```sql
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

매칭 실패 시 NULL 유지 (V14 policies 패턴과 동일). `ON DELETE SET NULL`로 customer 삭제 시 proposal은 스냅샷으로 남음.

### 2.2 Proposal 엔티티

`backend/src/main/java/com/agentsupport/proposal/entity/Proposal.java`:
- `customerId` 필드 추가 (`@Column(name = "customer_id")`, UUID, nullable)
- `getCustomerId()` 추가
- `create()` 팩토리에 `customerId` 파라미터 추가
- 기존 `customer_name`, `phone_number`, `birth_date` 필드는 유지 (스냅샷)

### 2.3 DTO 변경

**`ProposalCreateRequest`** (변경 — 입력 API):
```java
public record ProposalCreateRequest(
    @NotNull UUID customerId,
    @NotBlank String productName,
    @NotBlank String insurerName,
    @NotNull @Positive BigDecimal monthlyPremium
) {}
```

기존 필드 (`customerName`, `phoneNumber`, `birthDate`) 제거. 백엔드가 `customerId`로 고객을 조회해서 스냅샷 처리.

**`ProposalDto`** (변경 — 출력 DTO): `UUID customerId` 필드 노출 (customerName 앞).

### 2.4 ProposalService.create 의사 코드

```
input: ProposalCreateRequest req, String agentId, boolean isAdmin
output: ProposalDto

1. customer = customerRepository.findById(req.customerId())
     .orElseThrow(NotFoundException("customer not found: " + req.customerId()))

2. if (!isAdmin && !customer.agent_id == agentId) throw AccessDeniedException

3. proposal = Proposal.create(
     customerId = customer.id,
     customerName = customer.name,               // 생성 시점 스냅샷
     phoneNumber = customer.phone,                // 스냅샷
     birthDate = customer.birthDate,              // 스냅샷 (NULL이면 NULL)
     productName = req.productName,
     insurerName = req.insurerName,
     monthlyPremium = req.monthlyPremium,
     status = "작성 중",
     proposedDate = LocalDate.now(),
     agentId = agentId
   )

4. saved = proposalRepository.save(proposal)

5. return ProposalDto.from(saved)
```

### 2.5 권한

- `/api/proposals/**` 권한 룰 (ADMIN/AGENT1) 그대로
- POST 시점에 customer 소유 확인 (위 step 2)

### 2.6 Backend 테스트

| 케이스 | 기대 |
|---|---|
| AGENT1 본인 고객 customerId로 POST | 200 + 응답에 customer 스냅샷 (name/phone/birthDate) 채워짐 |
| AGENT1 타인 고객 customerId | 403 AccessDeniedException |
| 존재하지 않는 customerId | 404 NotFoundException |
| ADMIN 임의 customer | 200 |
| 생성된 proposal의 customer_id가 customers.id와 일치 | DB 확인 |
| V16 backfill | seed 데이터로 매칭 검증, 매칭 실패는 NULL 유지 |
| customer 삭제 시 proposal.customer_id가 NULL로 SET | DB 확인 (ON DELETE SET NULL 동작) |

---

## 3. Frontend 변경

### 3.1 신규 컴포넌트

**`entities/customer/ui/customer-picker/index.tsx`** — 검색 가능한 콤보박스.

props:
```ts
interface Props {
  value: CustomerDto | null;
  onChange: (customer: CustomerDto | null) => void;
  error?: string;
}
```

내부:
- `useCustomers({ size: 1000 })` 호출 — 전체 페치 (현재 클라이언트 사이드 필터)
- 입력 값으로 필터 (`name.includes(query) || phone.includes(query)`)
- 외부 클릭 닫힘
- dropdown 행 컴포넌트 분리 (큰 리스트 대비)

### 3.2 entities/customer Public API 확장

`entities/customer/index.ts`에 추가:
```ts
export { CustomerPicker } from "./ui/customer-picker";
```

### 3.3 features/proposal/create 변경

**`api/index.ts`**:
```ts
export interface ProposalCreateRequest {
  customerId: string;        // 신규
  productName: string;
  insurerName: string;
  monthlyPremium: number;
  // 제거: customerName, phoneNumber, birthDate
}
```

**`ui/index.tsx`**:
- 기존 `customerName` / `phoneNumber` / `birthDate` 입력 제거
- `<CustomerPicker>` (Controller로 래핑) 추가
- 선택 시 `<SelectedCustomerCard>` (파일 내부 작은 컴포넌트) 표시
- zod schema 갱신:
  ```ts
  const schema = z.object({
    customerId: z.string().min(1, "고객을 선택하세요"),
    productName: z.string().min(1, "상품명을 입력하세요"),
    insurerName: z.string().min(1, "보험사를 입력하세요"),
    monthlyPremium: z.string().refine(...)
  });
  ```
- `react-hook-form`의 `Controller`로 `CustomerPicker` 통합:
  ```tsx
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
  ```

### 3.4 `SelectedCustomerCard` (features/proposal/create/ui 내부)

작은 표시용 컴포넌트:
```tsx
function SelectedCustomerCard({ customer }: { customer: CustomerDto }) {
  // 나이 계산, meta 조합, 카드 표시
}
```

### 3.5 변경 없는 파일

- `app/proposals/page.tsx` — `<ProposalFormModal onClose={...} />` 호출 시그니처 동일

---

## 4. 테스트 (Frontend 수동 스모크)

1. AGENT1 로그인 → `/proposals` → "새 설계 등록"
2. 콤보박스 클릭 → dropdown에 본인 고객 표시
3. 이름 일부 타이핑 → 필터 동작 확인
4. 고객 선택 → 미리보기 카드 표시 (이름/전화/생년월일/나이/성별)
5. 상품명/보험사/월 보험료 입력 → "등록" 클릭 → 모달 닫힘 + 목록에 신규 행 표시
6. AGENT1이 등록 고객 0명 상태 → 콤보박스에 "등록된 고객이 없습니다" + `/customers` 링크 표시, 링크 클릭 동작
7. 검색해도 매칭 없음 → "검색 결과가 없습니다…" 안내 표시
8. ADMIN 로그인 → 전체 고객 표시
9. 모달 외부 클릭으로 dropdown 닫힘 확인

---

## 5. 리스크 & 대응

| 리스크 | 대응 |
|---|---|
| V16 backfill 매칭 실패 (이름 오타 등) | customer_id NULL 유지. 기존 proposal 조회/표시는 denormalized 필드 사용 — 영향 없음 |
| 콤보박스 성능 (큰 고객 목록) | 현재 size 1000 클라이언트 필터. 500+ 시점에 서버 사이드 검색 전환 (별도 작업) |
| 외부 클릭 dropdown 닫힘 | useRef + document click 리스너로 처리 |
| 키보드 접근성 | 1차는 마우스 위주. 키보드 (↑/↓/Enter/Esc) 후속 작업 |
| Customer 삭제 후 과거 proposal 표시 | ON DELETE SET NULL — proposal.customer_id NULL, denormalized 필드는 그대로 |
| 기존 proposal 데이터 customerId NULL | 신규 생성만 채움. 표시에 영향 없음 |
| 동시 dropdown 오픈/모달 z-index 충돌 | dropdown은 모달 내부에서만 표시 — 모달 z-index보다 상위 |

---

## 6. Out of Scope

- 콤보박스 키보드 네비게이션 (마우스 우선)
- 서버 사이드 고객 검색 API
- 모달 내 "새 고객 등록" 인라인 흐름 (사용자는 `/customers`에서 등록 후 돌아옴)
- 기존 proposal의 customer 정보 동기화 (생성 시점 스냅샷이 의도된 동작)
- 고객 정보 변경 시 future proposals만 새 정보 사용 — 기존 proposal은 영향 없음
- ProposalDto 응답에서 customer 정보를 customers 테이블 lookup으로 갱신 (스냅샷 유지)

---

## 7. 진행 순서

1. `feat/health-analysis` PR push + 머지 완료 후 시작
2. master 동기화 후 `feat/proposal-customer-picker` 브랜치 분기
3. 본 spec 기반으로 plan 작성
4. 구현 → 검증 → PR
