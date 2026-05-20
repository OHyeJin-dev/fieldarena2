# Underwriting Health Analysis Design

- **Date**: 2026-05-20
- **Scope**: 심사(underwriting) 페이지에 건강 데이터 분석 기능 추가. 백엔드 모듈 신설, 프론트 FSD 슬라이스 추가, 기존 policies/dashboard 일부 변경.
- **Goal**: 보험설계사가 고객의 건강 데이터(국민건강보험/건강심사평가원 연동 가정, 현재는 더미)를 수집·분석해 유병 여부와 인수 권고를 확인. 분석 결과는 underwriting 페이지의 행과 대시보드에서 조회.
- **Reference**:
  - 기존 FSD 컨벤션: `docs/superpowers/specs/2026-05-19-frontend-fsd-migration-design.md`
  - 기여 가이드: `CONTRIBUTING.md`

---

## 1. 사용자 흐름

### 1.1 진입점

- 분석 시작은 **`/underwriting` 페이지의 테이블 "분석" 컬럼에서만**. 페이지 상단 글로벌 버튼 없음.
- 권한: ADMIN, AGENT1. AGENT2는 `/underwriting` 비노출 + API 403.

### 1.2 테이블 컬럼 추가

기존 컬럼 끝에 **"분석"** 추가:

| 셀 상태 | 표시 | 동작 |
|---|---|---|
| 분석 없음, `customer_id` 있음 | `+ 분석 요청` 링크 | 클릭 시 그 행의 고객이 사전 선택된 분석 모달 |
| 분석 있음 | 등급 뱃지 (🟢 정상 / 🟡 주의 / 🔴 위험) | 클릭 시 결과 모달 (재분석 가능) |
| `customer_id` 매칭 실패 (NULL) | `-` | 분석 불가 (customers 페이지에서 매핑 보완 필요) |

### 1.3 모달 (Stepper, 2단계)

**Step 1 — 데이터 수집**

```
건강 데이터 분석 — 김OO (45세, 남)

Step 1 ●─────○ Step 2

데이터 수집 시나리오
○ 자동 수집 (RANDOM)         ← 기본 선택
○ 시나리오 선택 (개발/시연용)
  [정상] [고혈압] [당뇨] [복합]

[수집 시작]
```

**Step 2 — 분석 결과**

```
Step 1 ●──────● Step 2

분석 결과 (2026-05-20 14:32 분석)

위험 등급: 🟡 주의
유병 여부: 있음

보유 질환
• 본태성 고혈압 (I10)
  추정 진단 2024-03 · 처방 월 1회
• 제2형 당뇨병 (E11)
  추정 진단 2023-11 · 처방 월 1회

인수 권고: 조건부 (보험료 할증 권고)
의견: 만성질환 관리 양호하나 합병증 모니터링 필요

[닫기]  [재분석]
```

### 1.4 모달 상태 머신

```
[input] ──"수집 시작"──> [collecting] ──성공──> [result]
                                       │
                                       └─실패─> [error] ──"다시 시도"──> [input]

[result] ──"재분석"──> [input]
[result] ──"닫기"───> 모달 닫힘
```

뱃지 클릭으로 진입 시 초기 상태는 `result`. `+ 분석 요청` 클릭은 초기 상태 `input`.

### 1.5 대시보드 (ADMIN/AGENT1)

기존 대시보드에 두 섹션 추가:

**통계 카드**

```
건강 분석 현황
┌──────────┬──────────┬──────────┬───────────┐
│ 분석 완료│  정상    │   주의   │   위험    │
│   N명    │   N명    │   N명    │   N명     │
└──────────┴──────────┴──────────┴───────────┘
```

**최근 분석 5건**

| 고객명 | 등급 | 분석일 | 액션 |
|---|---|---|---|
| 김OO | 🟡 주의 | 2026-05-20 | [상세] |
| 이OO | 🟢 정상 | 2026-05-20 | [상세] |

"상세" 클릭 → `/underwriting?analysisId=<id>` 로 라우팅 → 페이지 마운트 시 query param 읽어 해당 분석 모달 자동 오픈.

권한 범위:
- AGENT1: 본인이 분석한 결과만 (`analyzed_by = current user`)
- ADMIN: 전체

---

## 2. 데이터 모델

### 2.1 `policies` 테이블 변경 (마이그레이션)

기존 `policies` 테이블에 `customer_id` FK 추가.

```sql
ALTER TABLE policies ADD COLUMN customer_id UUID NULL;
ALTER TABLE policies ADD CONSTRAINT fk_policies_customer
  FOREIGN KEY (customer_id) REFERENCES customers(id);
CREATE INDEX idx_policies_customer_id ON policies(customer_id);

UPDATE policies p
SET customer_id = c.id
FROM customers c
WHERE p.agent_id = c.agent_id
  AND p.customer_name = c.name
  AND p.customer_id IS NULL;
```

매칭 실패한 행은 NULL 유지. 추후 수동 매핑 또는 신규 customer 등록으로 보완.

### 2.2 `health_data` 테이블 (신규)

수집된 더미 건강 데이터의 raw 저장. **이력 누적 (분석마다 새 row)**.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | UUID PK | gen_random_uuid() |
| `customer_id` | UUID NOT NULL FK → customers(id) ON DELETE CASCADE | |
| `source` | VARCHAR(20) NOT NULL | `NHIS_DUMMY` (실 연계 시 `NHIS`/`HIRA`) |
| `scenario` | VARCHAR(20) NOT NULL | `RANDOM` / `NORMAL` / `HYPERTENSION` / `DIABETES` / `COMPLEX` |
| `payload` | TEXT NOT NULL (PII 암호화) | JSON 직렬화된 raw payload. `@Convert(PiiAttributeConverter.class)` |
| `collected_at` | TIMESTAMPTZ NOT NULL | |
| `collected_by` | VARCHAR(50) NOT NULL | agent_id |

Index: `(customer_id, collected_at DESC)` — 고객별 최신 데이터 조회.

**`payload` 구조 (역직렬화 시):**

```jsonc
{
  "demographics": { "age": 45, "gender": "M" },
  "visits": [
    { "date": "2024-03-15", "diagnosisCode": "I10", "diagnosisName": "본태성 고혈압", "department": "내과" }
  ],
  "prescriptions": [
    { "date": "2024-03-15", "drugClass": "ARB", "days": 30 }
  ],
  "admissions": []
}
```

### 2.3 `health_analyses` 테이블 (신규)

분석 결과. **고객당 1개 (UNIQUE customer_id, 재분석 시 UPSERT)**.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | UUID PK | |
| `customer_id` | UUID NOT NULL UNIQUE FK → customers(id) ON DELETE CASCADE | |
| `health_data_id` | UUID NOT NULL FK → health_data(id) | 분석 입력으로 사용된 raw |
| `risk_grade` | VARCHAR(10) NOT NULL | `NORMAL` / `CAUTION` / `RISK` |
| `has_disease` | BOOLEAN NOT NULL | |
| `diseases` | TEXT NOT NULL (PII 암호화) | JSON `[{ code, name, diagnosedAt, frequency }]` |
| `underwriting_recommendation` | VARCHAR(20) NOT NULL | `APPROVE` / `CONDITIONAL` / `DECLINE` |
| `summary` | TEXT NOT NULL | 한 줄 의견 |
| `analyzed_at` | TIMESTAMPTZ NOT NULL | |
| `analyzed_by` | VARCHAR(50) NOT NULL | agent_id |

Index:
- `UNIQUE(customer_id)` — UPSERT 보장
- `(analyzed_by, analyzed_at DESC)` — 대시보드 "최근 분석" + 권한 필터

### 2.4 암호화 정책

- `health_data.payload`, `health_analyses.diseases`: TEXT + `PiiAttributeConverter` (기존 패턴). JSONB 미사용 (Converter 호환).
- 그 외 컬럼은 평문 (검색/통계용).
- 후속 보존 정책 배치 (별도 작업): `health_data` 오래된 행을 N일 후 삭제.

---

## 3. API

### 3.1 엔드포인트

| Method | Path | 권한 | 동작 |
|---|---|---|---|
| `POST` | `/api/health-analyses` | ADMIN, AGENT1 | Body: `{ customerId, scenario }`. 더미 수집 + 분석 + UPSERT 저장. 응답: HealthAnalysisDto |
| `GET` | `/api/health-analyses?customerIds=uuid1,uuid2,...` | ADMIN, AGENT1 | Map<customerId, HealthAnalysisDto|null>. 권한 없는 customerId는 응답에서 제외 |
| `GET` | `/api/health-analyses/{id}` | ADMIN, AGENT1 | 결과 모달 진입 (dashboard → URL 파라미터) |
| `GET` | `/api/health-analyses/summary` | ADMIN, AGENT1 | `{ total, normal, caution, risk }`. AGENT1은 본인이 분석한 것만, ADMIN 전체 |
| `GET` | `/api/health-analyses/recent?limit=5` | ADMIN, AGENT1 | 최근 분석 리스트. 권한 필터 동일 |

`SecurityConfig`에 `/api/health-analyses/**` → `hasAnyRole("ADMIN", "AGENT1")` 추가.

### 3.2 권한 룰 (Controller 레벨)

- ADMIN: 전체 customer/analysis 접근
- AGENT1: `customer.agent_id = auth.getName()` 일치만 (POST 시점 검증, GET 시점 필터)
- AGENT2: 403 (SecurityConfig에서 차단)

POST 검증:
```java
Customer customer = customerRepo.findById(req.customerId())
    .orElseThrow(NotFoundException::new);
if (!isAdmin(auth) && !customer.getAgentId().equals(auth.getName())) {
    throw new AccessDeniedException();
}
```

GET by-customers 필터: 권한 없는 customerId는 응답 map에서 제외 (404 대신).

### 3.3 시나리오 enum

`POST /api/health-analyses` 의 `scenario`:
- `RANDOM` (기본). 시드 = `customer.id + System.currentTimeMillis()` → 매번 다른 결과
- `NORMAL` / `HYPERTENSION` / `DIABETES` / `COMPLEX`. 시드 = `customer.id + scenario` → 재현 가능

---

## 4. 더미 데이터 생성 + 분석 룰

### 4.1 `DummyHealthDataGenerator`

**입력**: `scenario`, `Customer (age, gender)`
**출력**: `HealthDataPayload` (Java POJO → Jackson JSON)

| 시나리오 | 진료 빈도 (12개월) | 진단 코드 | 처방 | 입원 |
|---|---|---|---|---|
| `NORMAL` | 0~2회 (감기/위장염 등) | `J00`, `K30` 등 비만성 | 없음 또는 단기 (~7일) | 없음 |
| `HYPERTENSION` | 월 1회 내과 | `I10` (본태성 고혈압) | ARB/CCB 30일 매월 | 없음 |
| `DIABETES` | 월 1회 내과 + 분기 안과 | `E11` (제2형 당뇨) | metformin 30일 매월 | 없음 |
| `COMPLEX` | 월 1.5회 다과목 | `I10` + `E11` + 기타 | 다약제 | 1건 (당뇨 합병증) |
| `RANDOM` | 위 4개 가중 랜덤 | 가중치: NORMAL 60% / 단일 만성 30% / COMPLEX 10% | | |

**나이 가중치**: 30대 이하면 NORMAL ↑, 50대 이상이면 만성/복합 ↑.

### 4.2 `HealthAnalysisService.analyze()`

의사 코드:

```
input: HealthDataPayload payload, Customer customer
output: HealthAnalysis (저장 직전 객체)

chronicCodes = {"I10", "E11", "I50", "N18", ...}  // 상수 클래스
diagnoses = payload.visits.map(v -> v.diagnosisCode).distinct()
chronicHeld = diagnoses ∩ chronicCodes
admissionCount = payload.admissions.size()
hasBothChronic = chronicHeld contains both "I10" and "E11"

if (admissionCount > 0 OR hasBothChronic):
    grade = RISK
    recommendation = DECLINE
elif chronicHeld.isNotEmpty():
    grade = CAUTION
    recommendation = CONDITIONAL
else:
    grade = NORMAL
    recommendation = APPROVE

diseases = chronicHeld.map(code -> Disease(
    code,
    diagnosisName for code,
    diagnosedAt = earliest visit date for this code,
    frequency = "월 N회" (12개월 내 진료 횟수 / 12)
))

summary = composeSummary(grade, admissionCount, chronicHeld)  // 룰 기반 한 줄 문구
```

### 4.3 만성 진단 코드 상수

`com.agentsupport.healthanalysis.ChronicConditions` 클래스에 Map<코드, 한글명>으로 분리. 향후 확장 시 한 파일만 수정.

```java
public static final Map<String, String> CHRONIC = Map.ofEntries(
    Map.entry("I10", "본태성 고혈압"),
    Map.entry("E11", "제2형 당뇨병"),
    Map.entry("I50", "심부전"),
    Map.entry("N18", "만성 신장병"),
    // ...
);
```

---

## 5. Backend 파일 구조

```
backend/src/main/java/com/agentsupport/
├── healthanalysis/                       # 신규 모듈
│   ├── entity/
│   │   ├── HealthData.java
│   │   └── HealthAnalysis.java
│   ├── dto/
│   │   ├── HealthAnalysisDto.java
│   │   ├── HealthDataPayload.java        # raw payload POJO (Jackson)
│   │   ├── AnalysisRequestDto.java       # POST body
│   │   ├── AnalysisSummaryDto.java
│   │   └── DiseaseDto.java
│   ├── repository/
│   │   ├── HealthDataRepository.java
│   │   └── HealthAnalysisRepository.java
│   ├── service/
│   │   ├── HealthAnalysisService.java
│   │   └── DummyHealthDataGenerator.java
│   ├── ChronicConditions.java
│   ├── Scenario.java                     # enum
│   ├── RiskGrade.java                    # enum
│   └── HealthAnalysisController.java
├── policy/
│   ├── entity/Policy.java                # customerId 필드 추가
│   ├── dto/PolicyDto.java                # customerId 노출
│   └── service/PolicyService.java        # DTO 매핑 변경
└── config/SecurityConfig.java            # /api/health-analyses/** ADMIN, AGENT1
```

마이그레이션:
- `V14__add_customer_id_to_policies.sql`
- `V15__create_health_data_and_analyses.sql`

(번호는 현재 마이그레이션 최대값 +1, 충돌 시 조정)

---

## 6. Frontend FSD 슬라이스

### 6.1 신규 entity

```
entities/health-analysis/
├── api/index.ts            # types: HealthAnalysisDto, DiseaseDto, RiskGrade, AnalysisSummaryDto
│                           # fns: fetchAnalysesByCustomers, fetchAnalysis, fetchAnalysisSummary, fetchRecentAnalyses
├── model/index.ts          # hooks: useAnalysesByCustomers, useAnalysis, useAnalysisSummary, useRecentAnalyses
├── ui/
│   ├── risk-badge/index.tsx          # 등급 색상 뱃지
│   ├── analysis-result/index.tsx     # 결과 상세 (모달 본문 + 대시보드 카드에서 재사용)
│   └── index.ts
└── index.ts                # Public API
```

### 6.2 신규 feature

```
features/health-analysis/
└── request/
    ├── api/index.ts        # createAnalysis (POST)
    ├── model/index.ts      # useCreateAnalysis (mutation)
    ├── ui/index.tsx        # AnalysisRequestModal (stepper)
    └── index.ts
```

### 6.3 React Query 캐시 정책

| Hook | queryKey | 무효화 트리거 |
|---|---|---|
| `useAnalysesByCustomers(ids)` | `["health-analyses", "by-customers", sortedIds]` | createAnalysis 성공 |
| `useAnalysis(id)` | `["health-analyses", "by-id", id]` | createAnalysis (재분석) → setQueryData |
| `useAnalysisSummary()` | `["health-analyses", "summary"]` | createAnalysis 성공 |
| `useRecentAnalyses(limit)` | `["health-analyses", "recent", limit]` | createAnalysis 성공 |

createAnalysis `onSuccess`:
- `invalidateQueries({ queryKey: ["health-analyses"] })` — prefix 전체 무효화
- `setQueryData(["health-analyses", "by-id", newAnalysis.id], newAnalysis)` — 단일 캐시는 즉시 갱신

`useAnalysesByCustomers` queryKey 안정화: 입력 ids를 dedupe + sort 후 사용 (페이지 단위 렌더링 안정성).

### 6.4 `AnalysisRequestModal` props/상태

```ts
interface Props {
  customer: { id: string; name: string; age?: number; gender?: string };
  existingAnalysis?: HealthAnalysisDto;
  onClose: () => void;
}

type ModalState =
  | { step: "input"; scenario: Scenario }
  | { step: "collecting" }
  | { step: "result"; analysis: HealthAnalysisDto }
  | { step: "error"; message: string };
```

초기 상태: `existingAnalysis ? "result" : "input"`.

### 6.5 변경되는 기존 파일

| 파일 | 변경 |
|---|---|
| `entities/contract/api/index.ts` | `PolicyDto`에 `customerId: string \| null` 추가 |
| `app/underwriting/page.tsx` | "분석" 컬럼 추가. `useAnalysesByCustomers` 호출. `AnalysisCell` 컴포넌트 사용. `searchParams.analysisId` 처리 (대시보드에서 진입 시 자동 모달 오픈) |
| `app/dashboard/page.tsx` | ADMIN/AGENT1 분기 안에 `useAnalysisSummary` + `useRecentAnalyses` 호출 + 카드/리스트 렌더. "상세" 링크는 `/underwriting?analysisId=<id>` |

### 6.6 `AnalysisCell` (underwriting 페이지 내부)

`/underwriting/page.tsx`에서만 쓰는 작은 컴포넌트라 page 파일 안 정의 가능. 또는 별도 파일로 분리하되 page 레벨 모듈로 유지 (FSD 위반 아님).

```tsx
function AnalysisCell({
  analysis,
  customer,
}: {
  analysis: HealthAnalysisDto | null;
  customer: { id: string | null; name: string };
}) {
  const [modalOpen, setModalOpen] = useState(false);
  if (!customer.id) return <td>-</td>;
  if (!analysis) return (
    <td>
      <button onClick={() => setModalOpen(true)}>+ 분석 요청</button>
      {modalOpen && <AnalysisRequestModal customer={...} onClose={...} />}
    </td>
  );
  return (
    <td>
      <RiskBadge grade={analysis.riskGrade} onClick={() => setModalOpen(true)} />
      {modalOpen && <AnalysisRequestModal customer={...} existingAnalysis={analysis} onClose={...} />}
    </td>
  );
}
```

---

## 7. 테스트

### 7.1 Backend

| 대상 | 시나리오 |
|---|---|
| `DummyHealthDataGenerator` | 각 시나리오 출력이 룰 만족 (진단 코드, 처방 빈도, 입원 개수) |
| `HealthAnalysisService.analyze()` | NORMAL → APPROVE, 단일 만성 → CAUTION/CONDITIONAL, 복합/입원 → RISK/DECLINE |
| `HealthAnalysisService.upsertAnalysis()` | 동일 customer_id 재분석 시 health_analyses 덮어쓰기, health_data 새 row |
| `HealthAnalysisController` POST | ADMIN/AGENT1 본인 고객 ✓, AGENT1 타인 고객 403, AGENT2 403 |
| `HealthAnalysisController` GET by-customers | 권한 없는 customerId는 응답에서 제외 |
| `HealthAnalysisController` GET summary/recent | AGENT1은 `analyzed_by = self` 필터, ADMIN 전체 |
| 마이그레이션 V14 (customer_id 추가 + backfill) | seed 데이터로 backfill 매칭 검증. 매칭 실패 → NULL |
| 마이그레이션 V15 (health 테이블) | 컬럼/제약 정상, UNIQUE customer_id |
| PII 암호화 | payload/diseases가 DB에 암호화된 형태로 저장 (raw SQL select로 확인) |

### 7.2 Frontend 수동 스모크

1. ADMIN 로그인 → `/underwriting` 행에 "분석" 컬럼 표시
2. 빈 셀 "분석 요청" 클릭 → 모달 stepper:
   - Step 1 시나리오 선택 → "수집 시작"
   - 로딩 (~1-2초)
   - Step 2 결과 표시
3. 모달 닫기 → 행 컬럼이 등급 뱃지로 갱신
4. 뱃지 클릭 → 결과 모달 재표시 → "재분석" → step 1 → 다른 시나리오 → 결과 변경
5. AGENT1 로그인 → 본인 고객 정책만 분석 가능
6. AGENT2 로그인 → `/api/health-analyses/*` 403
7. ADMIN/AGENT1 `/dashboard` → 통계 카드 + 최근 5건 표시
8. 대시보드 "상세" 클릭 → `/underwriting?analysisId=<id>` → 모달 자동 오픈
9. customer_id 매칭 안 된 policy 행은 "분석" 컬럼 `-`

---

## 8. 리스크

| 리스크 | 대응 |
|---|---|
| Backfill 매칭 실패 (이름 오타/공백/null) | customer_id NULL 유지. UI에서 `-` 표시. 사용자가 customers에서 수동 매핑 또는 신규 등록 후 자연 해결 |
| 더미 RANDOM이 단조로움 (시연 시 매번 같은 결과) | timestamp 시드 → 매번 다른 결과. 시연용은 명시 시나리오 사용 |
| payload 암호화 성능 | 1건당 ~10KB, INSERT 한 번/조회 한 번 패턴 — 영향 미미 |
| 어드민 raw 조회 시 복호화 필요 | 현재 기능 범위 밖. 별도 어드민 페이지 만들 때 디크립트 권한 분리 |
| customer 삭제 시 health 데이터 | ON DELETE CASCADE로 자동 정리 |
| AGENT1이 UI 우회로 타인 고객 분석 시도 | Controller에서 customer.agent_id 검증 → 403 |
| underwriting 페이지 N+1 호출 | useAnalysesByCustomers 단일 호출, staleTime 30s |
| 동시에 같은 customer 재분석 (race) | UNIQUE(customer_id) + UPSERT (ON CONFLICT). 마지막 write가 이김. 데이터 손실 위험 미미 |
| 같은 customer가 여러 policy 보유 시 같은 분석 결과 공유 | 의도된 동작 (B 옵션). 시점 다르면 재분석으로 갱신 |

---

## 9. Out of Scope

- 실제 NHIS/HIRA 연계 (인증, 스크래핑, 의료 마이데이터)
- 분석 이력 view (UI에서 최신 1건만, history는 health_data 기반 별도 작업)
- health_data 자동 삭제 배치 (보존 정책)
- 분석 결과 PDF 출력
- 동의서 흐름 (마이데이터 동의 UI)
- 위험 등급 알림 (push/email)
- AGENT2 분석 권한 (요건 변경 시)
- 만성 진단 코드 목록 관리 UI (현재는 코드 상수)
- 어드민용 raw payload 조회 UI
- 분석 결과를 외부 시스템(보험사 심사 시스템 등)으로 전송
