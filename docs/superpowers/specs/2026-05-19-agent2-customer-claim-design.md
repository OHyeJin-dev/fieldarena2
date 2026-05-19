# AGENT2 전용 화면 — 고객 관리 & 청구 등록/관리 설계

## 개요

`AGENT2` 역할은 본인 고객을 관리하고, 그 고객에 대한 청구를 등록/추적하는 업무에 집중한다.
설계(`/proposals`) 및 심사(`/underwriting`) 메뉴는 AGENT2에게 노출되지 않으며, 백엔드 API도 차단된다.

신규 도입:
- 고객 관리(`/customers`) — 모든 로그인 역할이 사용. 본인 등록 고객만 조회/수정/삭제(ADMIN은 전체).
- 청구 등록 — 본인 고객 목록에서 선택해 청구를 등록.

## 역할별 메뉴

| 역할 | 메뉴 |
| --- | --- |
| `ADMIN` | 대시보드, 설계 관리, 심사 현황, 고객 관리, 청구 관리, 가입 관리 |
| `AGENT1` | 대시보드, 설계 관리, 심사 현황, 고객 관리, 청구 관리 |
| `AGENT2` | 대시보드, 고객 관리, 청구 관리 |

---

## 데이터 구조

### Flyway V11: `customers` 테이블

```sql
CREATE TABLE customers (
  id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  agent_id    VARCHAR(50)   NOT NULL,
  name        VARCHAR(500)  NOT NULL,
  phone       VARCHAR(500)  NOT NULL,
  birth_date  DATE,
  gender      VARCHAR(10),
  email       VARCHAR(500),
  address     VARCHAR(1000),
  memo        TEXT,
  created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_customers_agent_id ON customers (agent_id);
```

- `agent_id` — 등록자(소유 AGENT)의 `users.id`. ADMIN이 등록한 경우 ADMIN id.
- `name`, `phone`, `email`, `address` — PII이므로 `PiiAttributeConverter`로 AES/GCM 암호화 저장. 암호문 길이를 고려해 VARCHAR(500~1000).
- `gender` — `M` / `F` / `O` 등 (코드값, 평문). NULL 허용.
- `birth_date` — `DATE` (NULL 허용). 일자 자체는 PII 민감도가 낮으므로 평문.
- `memo` — 평문(설계사 본인 메모). TEXT.
- 검색 기능은 1차 범위에서는 메모리 필터링 수준으로만 제공(이름 부분일치는 암호화 컬럼이라 DB 인덱스 검색 불가). 페이지네이션은 `created_at DESC` 기준.

### Flyway V12: `claims.customer_id` 추가

```sql
ALTER TABLE claims ADD COLUMN customer_id UUID;
ALTER TABLE claims ADD CONSTRAINT fk_claims_customer
  FOREIGN KEY (customer_id) REFERENCES customers(id);
CREATE INDEX idx_claims_customer_id ON claims (customer_id);
```

- `customer_id`는 NULL 허용. 기존 시드/레거시 청구 데이터는 `customer_id IS NULL`로 남는다.
- 신규 청구 등록 시 `customer_id`는 필수. 백엔드에서 검증.
- 표시용 `customer_name`은 유지하되, 신규 등록 시 customer 레코드에서 자동으로 채운다.

---

## 백엔드 (Spring Boot)

### `customer` 모듈 (신규)

```
com.agentsupport.customer/
  entity/Customer.java
  dto/CustomerDto.java
  dto/CustomerCreateRequest.java
  dto/CustomerUpdateRequest.java
  repository/CustomerRepository.java
  service/CustomerService.java
  CustomerController.java
```

- `Customer` 엔티티: 위 테이블 컬럼 매핑. PII 필드는 `@Convert(converter = PiiAttributeConverter.class)`.
- `CustomerRepository`:
  - `Page<Customer> findByAgentId(String agentId, Pageable pageable)`
  - `Page<Customer> findAll(Pageable pageable)` — ADMIN용
  - `Optional<Customer> findByIdAndAgentId(UUID id, String agentId)`
- `CustomerService`:
  - 목록 — ADMIN이면 전체, 그 외엔 본인 것만.
  - 단건 조회/수정/삭제 — ADMIN이 아니면 본인 소유 확인 후 진행. 미소유 시 `403`.
  - 생성 — `agentId = auth.getName()`로 자동 세팅.
- `CustomerController`: `/api/customers` 하위 REST API.

| Method | Path | 권한 |
| --- | --- | --- |
| GET | `/api/customers?page&size` | ADMIN, AGENT1, AGENT2 |
| POST | `/api/customers` | ADMIN, AGENT1, AGENT2 |
| GET | `/api/customers/{id}` | ADMIN, AGENT1, AGENT2 (본인 또는 ADMIN) |
| PUT | `/api/customers/{id}` | ADMIN, AGENT1, AGENT2 (본인 또는 ADMIN) |
| DELETE | `/api/customers/{id}` | ADMIN, AGENT1, AGENT2 (본인 또는 ADMIN) |

### `claim` 모듈 (변경)

- 엔티티에 `customerId` 필드 + getter 추가.
- `ClaimCreateRequest` DTO 신규: `customerId(UUID, required)`, `policyNumber`, `insurerName`, `claimType`, `claimAmount`, `claimDate`, `status(기본 "접수")`.
- `ClaimService.create(agentId, req)`:
  1. `CustomerRepository.findByIdAndAgentId(req.customerId, agentId)` — 본인 고객이 아니면 `403`.
  2. `customer_name`은 해당 customer의 name 복호화 값으로 자동 세팅.
  3. `agent_id = agentId`로 저장.
- `POST /api/claims` 추가.

### `SecurityConfig` 변경 (F1)

`authorizeHttpRequests` 블록에 다음 규칙을 `/api/admin/**` 다음에 추가(`anyRequest().authenticated()` 앞):

```java
.requestMatchers("/api/proposals/**", "/api/underwriting/**")
    .hasAnyRole("ADMIN", "AGENT1")
.requestMatchers("/api/customers/**", "/api/claims/**")
    .hasAnyRole("ADMIN", "AGENT1", "AGENT2")
```

- AGENT2가 `/api/proposals/**` 또는 `/api/underwriting/**` 호출 시 `403`.
- 대시보드 요약(`/api/dashboard/summary`)은 인증만 요구(`authenticated()`). 응답 내부는 역할에 따라 다르게 구성(아래 참조).

### `dashboard` 모듈 (변경)

- `DashboardSummaryDto`에 필드 추가:
  - `myCustomers` (Long) — 본인 고객 수.
  - `monthlyClaims` (Long) — 이번 달 본인이 등록한 청구 건수.
- `DashboardService.summary(agentId, role)`:
  - 항상 위 두 필드를 채운다(역할에 따라 분기하지 않고 동일 응답).
  - 기존 필드(`activeProposals`, `underwritingPending`, `claimsInProgress`, `monthlyProposals`, `recentProposals`)는 유지. 프런트에서 역할에 따라 렌더 분기.

---

## 프런트엔드 (Next.js)

### 사이드바 — [sidebar.tsx](frontend/src/components/layout/sidebar.tsx)

`NAV_ITEMS`를 다음으로 갱신:

```ts
{ label: "대시보드",   href: "/dashboard",   icon: LayoutDashboard, roles: ["ADMIN","AGENT1","AGENT2"] },
{ label: "설계 관리",  href: "/proposals",   icon: ClipboardList,   roles: ["ADMIN","AGENT1"] },
{ label: "심사 현황",  href: "/underwriting",icon: FileSearch,      roles: ["ADMIN","AGENT1"] },
{ label: "고객 관리",  href: "/customers",   icon: Users,           roles: ["ADMIN","AGENT1","AGENT2"] }, // 신규
{ label: "청구 관리",  href: "/claims",      icon: Receipt,         roles: ["ADMIN","AGENT1","AGENT2"] },
{ label: "가입 관리",  href: "/admin/users", icon: Settings,        roles: ["ADMIN"] },
```

### 신규 `/customers`

- `frontend/src/app/customers/page.tsx`, `layout.tsx`
- `frontend/src/features/customers/api.ts`, `queries.ts`, `CustomerFormModal.tsx`
- Layout: `<AuthGuard allowedRoles={["ADMIN","AGENT1","AGENT2"]}>`
- 페이지 구성:
  - 헤더: 제목 `고객 관리` + `신규 고객 등록` 버튼
  - 테이블: 이름, 전화, 생년월일, 성별, 이메일, 등록일, 액션(수정/삭제)
  - 페이지네이션 (`/claims`와 동일 패턴)
- 모달(CustomerFormModal): 신규/수정 공용. 필드 — 이름, 전화, 생년월일, 성별(라디오: 남/여), 이메일, 주소, 메모.

### `/claims` 변경

- `frontend/src/app/claims/page.tsx`에 `청구 등록` 버튼 + `ClaimFormModal` 추가.
- `ClaimFormModal`:
  - 고객 선택 — 본인 고객 목록에서 검색·선택 (드롭다운 또는 콤보박스).
  - 계약번호, 보험사, 청구 유형, 청구 금액, 청구일 입력.
  - 제출 시 `POST /api/claims` 호출 후 목록 리프레시.

### 대시보드 — [page.tsx](frontend/src/app/dashboard/page.tsx)

`useMe()`로 role 얻고 분기. `role === "AGENT2"`일 때 변경 사항:

1. **상단 카드 3개**
   - A: `본인 고객` (값: `myCustomers`, 아이콘: Users, 보더: info)
   - B: `청구 처리 중` (기존 카드 유지)
   - C: `이번 달 청구 등록` (값: `monthlyClaims`, 아이콘: Receipt, 보더: success)

2. **빠른 작업 (3개 버튼)**
   - AGENT2: `새 고객 등록`(/customers 이동) / `청구 등록`(/claims 이동) / `청구 관리`(/claims 이동)
   - 기존(`새 설계 작성`, `심사 현황`)은 노출되지 않음.

3. **업무 현황**
   - AGENT2: `청구 처리 중` 행만 표시.

4. **오른쪽 탭 패널**
   - AGENT2: `TABS`에서 `최근 설계` 제거 → `공지사항`, `일정` 2개만.

5. **하단 `이번 달 설계 작성 현황`**
   - AGENT2: 영역 전체 숨김.

AGENT1/ADMIN의 대시보드는 현재 모습 그대로(필드 추가만 있음, UI 영향 없음).

### 일정 탭

일정 데이터는 현재 컴포넌트 내 상수(`SCHEDULES`)이며 더미. AGENT2도 동일하게 노출. 일정 모듈이 정식 도입되는 시점에 역할별 필터링을 재검토.

---

## 보안 / 권한 요약

- 백엔드: `SecurityConfig`에서 `/api/proposals/**`, `/api/underwriting/**`를 `ADMIN+AGENT1`로 제한. AGENT2 호출 시 `403`.
- 백엔드: `customer`, `claim` 서비스 계층에서 소유권 검증(`agentId == auth.getName()` 또는 `role == ADMIN`). DTO나 컨트롤러에 우회 경로가 없도록 단일 진입점 유지.
- 프런트: 사이드바·대시보드·페이지 가드(`AuthGuard allowedRoles`)에서 메뉴/페이지 가시성 분리.

---

## 테스트 전략

### 백엔드
- `CustomerService` 단위 테스트
  - AGENT1이 본인 고객만 조회되는지
  - AGENT1이 타 AGENT 고객 조회·수정·삭제 시도 → `403`
  - ADMIN은 전체 조회 가능
  - 생성 시 `agent_id`가 인증 사용자로 채워지는지
- `ClaimService.create` 단위 테스트
  - 본인 고객 ID로 생성 → 성공, `customer_name` 자동 채움
  - 타인 고객 ID로 생성 → `403`
- `SecurityConfig` 통합 테스트
  - AGENT2가 `/api/proposals` 호출 → `403`
  - AGENT2가 `/api/customers`, `/api/claims` 호출 → 200

### 프런트
- 사이드바: role별로 보이는 메뉴 항목 검증.
- 대시보드: `role=AGENT2`일 때 설계/심사 관련 영역 미노출, 신규 카드 노출.
- `/customers` 페이지: 모달 CRUD 흐름.

---

## 마이그레이션·배포 순서

1. V11 `customers` 테이블 생성
2. V12 `claims.customer_id` 컬럼 추가
3. 백엔드 코드 (Customer 모듈 + Claim 등록 API + SecurityConfig)
4. 프런트엔드 (사이드바 → /customers → /claims 모달 → 대시보드 분기)
5. 통합 확인 후 머지

기존 시드 청구(`customer_id IS NULL`)는 UI에서 그대로 표시(고객 미연동 표시). 이후 관리 작업으로 정리.

---

## 범위 외 (이번 작업에서 제외)

- 일정 모듈 구축 / 역할별 일정 필터링
- 청구 등록 시 고객 신규 등록(인라인) 흐름 — 1차에서는 별도 `/customers` 페이지에서 먼저 등록
- 고객 PII 검색(이름 부분일치 등) 인덱싱 개선
- 청구 수정/삭제 — 1차에서는 등록과 조회만