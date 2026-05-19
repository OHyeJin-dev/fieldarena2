# exec-plan: 03-contracts

> 계약 현황 화면 구현. GA 설계사가 본인의 계약 목록을 조회하는 첫 도메인 기능.
> ORM: Spring Data JPA (ADR-004).

**Status**: Done
**Estimate**: 2~3d
**Depends on**: `01-login-mvp`, `02-dashboard` (완료)

---

## 1. 목표

- 사이드바 "계약 현황" 활성화 → `/contracts` 페이지에서 계약 목록 조회
- 페이지네이션 + 상태 필터
- 대시보드 요약(건수) + 최근 계약 목록을 실제 API로 교체 (TODO(03-contracts) 해소)

## 2. 스코프

### 포함

- Flyway V2: `policies` 테이블 생성
- BE: `Policy` Entity + `PolicyRepository` + `PolicyService` + `PolicyController`
  - `GET /api/policies` — 목록 (페이지네이션, 상태 필터)
  - `GET /api/dashboard/summary` — 이번 달 건수·대기 건수·최근 5건
- FE: `/contracts` 페이지 (목록 테이블 + 페이지네이션 + 상태 필터)
- FE: 대시보드 StatCard·최근 목록 실 API 연동
- 사이드바 "계약 현황" `disabled` 제거

### 비포함

- ❌ 계약 상세 페이지 (`/contracts/[id]`)
- ❌ 계약 등록·수정·삭제
- ❌ 보험사·상품 마스터 테이블 (계약에 문자열로 저장)
- ❌ 엑셀 다운로드

## 3. DB 스키마

```sql
-- V2__create_policies.sql
CREATE TABLE policies (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  policy_number   VARCHAR(20)     NOT NULL UNIQUE,   -- C-2025-0512
  agent_id        VARCHAR(50)     NOT NULL,           -- 로그인 username
  customer_name   VARCHAR(50)     NOT NULL,
  product_name    VARCHAR(100)    NOT NULL,
  insurer_name    VARCHAR(50)     NOT NULL,
  status          VARCHAR(20)     NOT NULL,           -- 심사 중|승인 완료|서류 보완|반려|지급 완료
  contract_date   DATE            NOT NULL,
  monthly_premium NUMERIC(12, 2),
  created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_policies_agent_id      ON policies (agent_id);
CREATE INDEX idx_policies_contract_date ON policies (contract_date DESC);
CREATE INDEX idx_policies_status        ON policies (status);
```

## 4. API 설계

### 4.1 GET /api/policies

Query params:

| 파라미터 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| `page` | int | 0 | 0-indexed |
| `size` | int | 20 | 최대 100 |
| `status` | string | (전체) | 상태 필터 |
| `startDate` | date | (없음) | 계약일 시작 (inclusive) |
| `endDate` | date | (없음) | 계약일 종료 (inclusive) |

Response `200`:

```json
{
  "content": [
    {
      "id": "uuid",
      "policyNumber": "C-2025-0512",
      "customerName": "김○○",
      "productName": "무배당 종신보험",
      "insurerName": "한화생명",
      "status": "심사 중",
      "contractDate": "2025-05-12",
      "monthlyPremium": 120000
    }
  ],
  "totalElements": 42,
  "totalPages": 3,
  "pageNumber": 0,
  "pageSize": 20
}
```

### 4.2 GET /api/dashboard/summary

Response `200`:

```json
{
  "monthlyContracts": 12,
  "pendingCases": 3,
  "recentContracts": [ /* 최근 5건, PolicyDto 형식 동일 */ ]
}
```

## 5. 작업 분해

### BE-1. Flyway V2 마이그레이션

- `backend/src/main/resources/db/migration/V2__create_policies.sql` (3절 스키마 그대로)
- H2 테스트 호환: `application-test.yml` 에 `spring.jpa.database-platform: org.hibernate.dialect.H2Dialect`
  - `gen_random_uuid()` → H2 Mode=PostgreSQL로 해결하거나 Entity에서 `@GeneratedValue` 사용

### BE-2. Policy 도메인

패키지: `com.agentsupport.policy`

```text
policy/
  entity/
    Policy.java              -- @Entity
  dto/
    PolicyDto.java           -- record (response)
    PolicySearchCondition.java -- record (query params)
  repository/
    PolicyRepository.java    -- JpaRepository + @Query
  service/
    PolicyService.java
  PolicyController.java
```

**`Policy` Entity 핵심:**

```java
@Entity
@Table(name = "policies")
public class Policy {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "policy_number", nullable = false, unique = true)
    private String policyNumber;

    @Column(name = "agent_id", nullable = false)
    private String agentId;

    // customerName, productName, insurerName, status, contractDate, monthlyPremium
    // created_at, updated_at (@CreationTimestamp, @UpdateTimestamp)
}
```

**`PolicyRepository`:**

```java
@Query("""
    SELECT p FROM Policy p
    WHERE p.agentId = :agentId
      AND (:status IS NULL OR p.status = :status)
      AND (:startDate IS NULL OR p.contractDate >= :startDate)
      AND (:endDate IS NULL OR p.contractDate <= :endDate)
    ORDER BY p.contractDate DESC
    """)
Page<Policy> findByCondition(
    @Param("agentId") String agentId,
    @Param("status") String status,
    @Param("startDate") LocalDate startDate,
    @Param("endDate") LocalDate endDate,
    Pageable pageable
);
```

**`PolicyController`:**

- `@GetMapping("/api/policies")`
- `Authentication` → `authentication.getName()` → agentId
- `Pageable` → Spring Data 자동 바인딩 (`page`, `size` 쿼리 파라미터)
- 응답: `PageResponse<PolicyDto>` (커스텀 record, Spring의 `Page` 직접 노출 금지)

### BE-3. Dashboard Summary

패키지: `com.agentsupport.dashboard`

```text
dashboard/
  dto/
    DashboardSummaryDto.java  -- record
  service/
    DashboardService.java
  DashboardController.java
```

- `DashboardService` 가 `PolicyRepository` 재사용:
  - 이번 달 건수: `contractDate BETWEEN 이번달1일 AND 오늘`
  - 대기 건수: `status IN ('심사 중', '서류 보완')`
  - 최근 5건: `findTop5ByAgentIdOrderByContractDateDesc`
- `@GetMapping("/api/dashboard/summary")`

### FE-1. API 타입 + fetch 훅

`frontend/src/features/contracts/api.ts` — `PolicyDto`, `PageResponse<T>`, `PolicyQuery` 타입 + `fetchPolicies()`

`frontend/src/features/contracts/queries.ts` — `usePolicies(query: PolicyQuery)`

`frontend/src/features/dashboard/api.ts` + `queries.ts` — `DashboardSummaryDto`, `useDashboardSummary()`

### FE-2. /contracts 페이지

`frontend/src/app/contracts/page.tsx` (Client Component):

- 상태 필터 `<select>` (전체 / 심사 중 / 승인 완료 / 서류 보완 / 반려 / 지급 완료)
- 계약 목록 테이블 (계약번호 / 고객명 / 상품 / 보험사 / 상태 / 계약일)
- 페이지네이션 (이전/다음 + 현재 페이지 표시)
- 로딩 skeleton + 빈 결과 empty state

### FE-3. 대시보드 실 API 연동

`frontend/src/app/dashboard/page.tsx`:

- `"use client"` + `useDashboardSummary()` 사용
- StatCard 값 실 API 교체
- 최근 계약 테이블 실 API 교체
- `TODO(03-contracts)` 제거

### FE-4. 사이드바 활성화

`frontend/src/components/layout/sidebar.tsx`:

- `{ label: "계약 현황", ..., disabled: true }` → `disabled` 제거

## 6. 검증 (DoD)

- [x] `./gradlew test` 통과
- [x] `GET /api/policies` — 비인증 401, 로그인 후 200 + 빈 배열
- [x] `GET /api/dashboard/summary` — 로그인 후 200
- [x] `/contracts` 페이지 로드 및 테이블 렌더
- [x] 상태 필터 변경 → 재조회
- [x] 페이지네이션 동작
- [x] 대시보드 StatCard 실 값 표시
- [x] `pnpm typecheck` 통과

## 7. 테스트 데이터

Flyway V3 seed (로컬 전용):

```sql
-- V3__seed_policies.sql
-- spring.flyway.locations에 classpath:db/migration,classpath:db/seed 로 분리하거나
-- 로컬 bootRun 시에만 실행되도록 profile 조건 사용
INSERT INTO policies (policy_number, agent_id, customer_name, product_name, insurer_name, status, contract_date, monthly_premium)
VALUES
  ('C-2025-0512', 'admin', '김○○', '무배당 종신보험',   '한화생명',  '심사 중',   '2025-05-12', 120000),
  ('C-2025-0509', 'admin', '이○○', '건강보험 플러스',   '삼성생명',  '승인 완료', '2025-05-09',  85000),
  ('C-2025-0507', 'admin', '박○○', '암보험 표준형',     'DB손보',    '서류 보완', '2025-05-07',  65000),
  ('C-2025-0503', 'admin', '최○○', '변액연금보험',       '미래에셋',  '승인 완료', '2025-05-03', 300000),
  ('C-2025-0429', 'admin', '정○○', '실손의료보험',       '현대해상',  '지급 완료', '2025-04-29',  45000);
```
