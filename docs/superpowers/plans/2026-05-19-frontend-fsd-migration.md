# Frontend FSD Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `frontend/src`를 Feature-Sliced Design (Practical variant) 구조로 마이그레이션. `app / widgets / features / entities / shared` 5 레이어로 재배치, features는 도메인 그룹핑(`features/auth/login` 등), 슬라이스 내부는 `api/model/ui/lib/` 세그먼트 폴더 강제. Steiger로 구조 린팅 자동화.

**Architecture:** 4개 PR로 레이어별 단계 진행. PR 1 (shared) → PR 2 (entities) → PR 3 (features + widgets) → PR 4 (Steiger). 각 PR은 단독으로 빌드/타입체크 통과. 기존 `@/*` alias는 PR 1~2 동안 호환 유지, PR 3에서 제거.

**Tech Stack:** Next.js 15 (App Router), React 19, TypeScript 5, React Query 5, react-hook-form + zod, Tailwind v4, pnpm, Steiger (`@feature-sliced/steiger-plugin`).

**Prerequisite:** AGENT2 플랜 (`docs/superpowers/plans/2026-05-19-agent2-customer-claim.md`) 완료. customer/claim 신규 코드가 옛 구조에 만들어진 상태에서 본 마이그레이션이 시작됨.

**Reference Spec:** `docs/superpowers/specs/2026-05-19-frontend-fsd-migration-design.md`

---

## File Structure (마이그레이션 후 최종)

```
frontend/src/
├── app/                                # Next.js App Router (이동 없음, import만 갱신)
│
├── widgets/                            # 페이지 조립 UI 블록
│   ├── app-shell/{ui/, index.ts}
│   ├── sidebar/{ui/, index.ts}
│   ├── top-bar/{ui/, index.ts}
│   └── auth-guard/{ui/, index.ts}
│
├── features/                           # use-case 슬라이스 (도메인별 그룹)
│   ├── auth/
│   │   ├── login/{api/, model/, index.ts}
│   │   ├── register/{api/, model/, index.ts}
│   │   └── logout/{api/, model/, ui/, index.ts}
│   ├── proposal/
│   │   └── create/{api/, model/, ui/, index.ts}
│   ├── customer/
│   │   ├── manage/{api/, model/, ui/, index.ts}     # 생성+편집 모달
│   │   └── delete/{api/, model/, index.ts}
│   └── claim/
│       └── create/{api/, model/, ui/, index.ts}
│
├── entities/                           # 도메인 모델 (DTO + 조회)
│   ├── proposal/{api/, model/, index.ts}
│   ├── contract/{api/, model/, index.ts}
│   ├── claim/{api/, model/, index.ts}
│   ├── customer/{api/, model/, index.ts}
│   ├── dashboard/{api/, model/, index.ts}
│   ├── user/{api/, model/, index.ts}
│   └── session/{api/, model/, lib/, index.ts}       # lib에 server-side getMe
│
└── shared/                             # 도메인 무관 공통
    ├── api/{client.ts, csrf.ts, types.ts, index.ts}
    ├── ui/
    │   ├── button/{ui/, index.ts}
    │   ├── stat-card/{ui/, index.ts}
    │   └── text-field/{ui/, index.ts}
    └── config/                         # (필요 시)
```

**규칙:**

- 각 슬라이스는 루트에 `index.ts` (Public API). 외부는 `@/entities/proposal` 형태로만 import. 내부 segment 직접 import 금지.
- features는 2단계 (`features/<group>/<slice>/`). 그룹 자체는 슬라이스가 아니므로 index.ts 없음.
- 각 슬라이스 내부 segment는 폴더(`api/`, `model/`, `ui/`, `lib/`). 단일 파일이어도 폴더 안에 `index.ts(x)`로 둠. 해당 segment가 없으면 폴더 생략.

---

## Conventions

- **PowerShell** 환경. 파일/폴더 이동은 `New-Item`/`Move-Item`/`Remove-Item`.
- **검증 명령** (모든 PR):
  - `cd frontend; pnpm typecheck`
  - `cd frontend; pnpm lint`
  - `cd frontend; pnpm build`
- **각 PR이 단독 머지 가능.** push/PR 생성은 사용자 승인 후에만 (memory: feedback_git_push_confirm).
- **Public API 규칙:** 모든 슬라이스에 `index.ts` 생성. 외부 import는 슬라이스 루트로.
- 옛 폴더 삭제는 각 PR 끝에서만.

---

# PR 1: `shared` 레이어

## Task 1: `shared/api/types.ts` 생성 (PageResponse 추출)

**Files:**
- Create: `frontend/src/shared/api/types.ts`

`PageResponse<T>`는 `features/contracts/api.ts`에 정의되어 있고 proposals/customers가 cross-feature import. shared로 추출.

- [ ] **Step 1: 폴더 생성**

Run: `New-Item -ItemType Directory -Path frontend/src/shared/api -Force`

- [ ] **Step 2: types.ts 작성**

```ts
// frontend/src/shared/api/types.ts
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  pageNumber: number;
  pageSize: number;
}
```

- [ ] **Step 3: 타입체크**

Run: `cd frontend; pnpm typecheck`
Expected: 통과.

---

## Task 2: `shared/api/client.ts`, `csrf.ts` 이동 + Public API

**Files:**
- Move: `frontend/src/lib/api/client.ts` → `frontend/src/shared/api/client.ts`
- Move: `frontend/src/lib/api/csrf.ts` → `frontend/src/shared/api/csrf.ts`
- Create: `frontend/src/shared/api/index.ts`
- Delete: `frontend/src/lib/`

- [ ] **Step 1: 파일 이동 + 옛 폴더 제거**

Run:
```powershell
Move-Item frontend/src/lib/api/client.ts frontend/src/shared/api/client.ts
Move-Item frontend/src/lib/api/csrf.ts frontend/src/shared/api/csrf.ts
Remove-Item frontend/src/lib -Recurse
```

- [ ] **Step 2: client.ts 외부 노출 식별자 확인**

Run: `cat frontend/src/shared/api/client.ts`

외부 import 사용처 확인:
```powershell
Select-String -Path frontend/src -Recurse -Pattern '@/lib/api/client'
```

- [ ] **Step 3: shared/api/index.ts 작성**

```ts
// frontend/src/shared/api/index.ts
export { apiFetch } from "./csrf";
export type { PageResponse } from "./types";
// client.ts에서 외부 사용되는 export가 있으면 추가:
// export { ApiError } from "./client";
```

(Step 2에서 확인된 모든 외부 사용 식별자를 index.ts에서 export.)

- [ ] **Step 4: tsconfig.json에 @/shared/* alias 추가**

Edit `frontend/tsconfig.json`:

```jsonc
{
  "compilerOptions": {
    "paths": {
      "@/*": ["./src/*"],
      "@/shared/*": ["./src/shared/*"]
    }
  }
}
```

- [ ] **Step 5: 사용처 import 갱신**

Run:
```powershell
Select-String -Path frontend/src -Recurse -Pattern '@/lib/api/' -List | Select-Object -ExpandProperty Path
```

각 파일에서 치환:
- `@/lib/api/client` → `@/shared/api/client`
- `@/lib/api/csrf` → `@/shared/api/csrf`

- [ ] **Step 6: 타입체크 + 빌드**

Run: `cd frontend; pnpm typecheck; pnpm build`
Expected: 통과.

---

## Task 3: `PageResponse` cross-feature import 제거

**Files:**
- Modify: `frontend/src/features/contracts/api.ts`
- Modify: `frontend/src/features/proposals/api.ts`
- Modify: `frontend/src/features/customers/api.ts` (AGENT2)
- Modify: 기타 `PageResponse` import 사용처

- [ ] **Step 1: contracts/api.ts에서 PageResponse 정의 삭제 + shared import**

Edit `frontend/src/features/contracts/api.ts`:

```ts
import { apiFetch } from "@/shared/api/csrf";
import type { PageResponse } from "@/shared/api/types";

// PolicyDto, PolicyQuery, fetchPolicies 정의는 유지
// PageResponse interface 정의 줄들만 삭제
```

- [ ] **Step 2: 사용처 일괄 갱신**

Run:
```powershell
Select-String -Path frontend/src -Recurse -Pattern 'from "@/features/contracts/api"' -List | Select-Object -ExpandProperty Path
```

각 파일에서 `import type { PageResponse } from "@/features/contracts/api"` → `import type { PageResponse } from "@/shared/api/types"`.

주요 대상:
- `frontend/src/features/proposals/api.ts`
- `frontend/src/features/customers/api.ts`

- [ ] **Step 3: 타입체크 + 빌드**

Run: `cd frontend; pnpm typecheck; pnpm build`
Expected: 통과.

---

## Task 4: `shared/ui/button` 이동 (폴더 구조)

**Files:**
- Create: `frontend/src/shared/ui/button/ui/index.tsx`
- Create: `frontend/src/shared/ui/button/index.ts`
- Delete: `frontend/src/components/ui/button.tsx`

- [ ] **Step 1: 폴더 생성 + 파일 이동**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/shared/ui/button/ui -Force
Move-Item frontend/src/components/ui/button.tsx frontend/src/shared/ui/button/ui/index.tsx
```

- [ ] **Step 2: 슬라이스 Public API 작성**

```ts
// frontend/src/shared/ui/button/index.ts
export { Button } from "./ui";
// 추가 타입 export 필요하면:
// export type { ButtonProps } from "./ui";
```

(실제 export 식별자는 `ui/index.tsx` 내용 확인 후 결정.)

- [ ] **Step 3: 사용처 갱신**

Run:
```powershell
Select-String -Path frontend/src -Recurse -Pattern '@/components/ui/button' -List | Select-Object -ExpandProperty Path
```

각 파일에서 `@/components/ui/button` → `@/shared/ui/button` 치환.

- [ ] **Step 4: 타입체크 + 빌드**

Run: `cd frontend; pnpm typecheck; pnpm build`
Expected: 통과.

---

## Task 5: `shared/ui/stat-card` 이동

**Files:**
- Create: `frontend/src/shared/ui/stat-card/ui/index.tsx`
- Create: `frontend/src/shared/ui/stat-card/index.ts`

- [ ] **Step 1: 폴더 + 파일 이동**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/shared/ui/stat-card/ui -Force
Move-Item frontend/src/components/ui/stat-card.tsx frontend/src/shared/ui/stat-card/ui/index.tsx
```

- [ ] **Step 2: index.ts 작성**

```ts
// frontend/src/shared/ui/stat-card/index.ts
export { StatCard } from "./ui";
```

- [ ] **Step 3: 사용처 갱신**

`@/components/ui/stat-card` → `@/shared/ui/stat-card`.

- [ ] **Step 4: 타입체크 + 빌드**

Run: `cd frontend; pnpm typecheck; pnpm build`
Expected: 통과.

---

## Task 6: `shared/ui/text-field` 이동

**Files:**
- Create: `frontend/src/shared/ui/text-field/ui/index.tsx`
- Create: `frontend/src/shared/ui/text-field/index.ts`

- [ ] **Step 1: 폴더 + 파일 이동**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/shared/ui/text-field/ui -Force
Move-Item frontend/src/components/ui/text-field.tsx frontend/src/shared/ui/text-field/ui/index.tsx
```

- [ ] **Step 2: index.ts 작성**

```ts
// frontend/src/shared/ui/text-field/index.ts
export { TextField } from "./ui";
```

- [ ] **Step 3: 사용처 갱신**

`@/components/ui/text-field` → `@/shared/ui/text-field`.

- [ ] **Step 4: 옛 components/ui 폴더 제거**

Run: `Remove-Item frontend/src/components/ui -Recurse`

(`components/layout`는 PR 3에서 처리. 폴더 자체는 남김.)

- [ ] **Step 5: 타입체크 + 빌드**

Run: `cd frontend; pnpm typecheck; pnpm build`
Expected: 통과.

---

## Task 7: PR 1 최종 검증 및 커밋

- [ ] **Step 1: 옛 경로 잔존 확인**

Run:
```powershell
Select-String -Path frontend/src -Recurse -Pattern '@/lib/api/|@/components/ui/'
```

Expected: 출력 없음.

- [ ] **Step 2: 전체 검증**

Run:
```powershell
cd frontend; pnpm typecheck; pnpm lint; pnpm build
```

Expected: 모두 통과.

- [ ] **Step 3: 커밋**

```powershell
git add frontend/src/shared frontend/tsconfig.json frontend/src/features frontend/src/app frontend/src/components
git status
git commit -m "refactor(frontend): introduce shared layer (FSD PR 1/4)

- Move lib/api -> shared/api with Public API barrel
- Move components/ui/* -> shared/ui/*/{ui,index} folder structure
- Extract PageResponse<T> to shared/api/types (remove cross-feature import)
- Add @/shared/* tsconfig alias

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 4: 사용자 승인 받아 push/PR**

---

# PR 2: `entities` 레이어

## Task 8: `entities/proposal` 생성 (설계)

**Files:**
- Create: `frontend/src/entities/proposal/api/index.ts`
- Create: `frontend/src/entities/proposal/model/index.ts`
- Create: `frontend/src/entities/proposal/index.ts`
- Modify: `frontend/src/features/proposals/api.ts` (조회 부분 제거)
- Modify: `frontend/src/features/proposals/queries.ts` (useProposals 제거)

- [ ] **Step 1: 폴더 생성**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/entities/proposal/api -Force
New-Item -ItemType Directory -Path frontend/src/entities/proposal/model -Force
```

- [ ] **Step 2: api/index.ts 작성**

```ts
// frontend/src/entities/proposal/api/index.ts
import { apiFetch } from "@/shared/api/csrf";
import type { PageResponse } from "@/shared/api/types";

export interface ProposalDto {
  id: string;
  customerName: string;
  phoneNumber: string;
  age: string;
  productName: string;
  insurerName: string;
  monthlyPremium: number | null;
  status: string;
  proposedDate: string;
}

export interface ProposalQuery {
  page?: number;
  size?: number;
  status?: string;
}

export function fetchProposals(query: ProposalQuery = {}): Promise<PageResponse<ProposalDto>> {
  const params = new URLSearchParams();
  if (query.page !== undefined) params.set("page", String(query.page));
  if (query.size !== undefined) params.set("size", String(query.size));
  if (query.status) params.set("status", query.status);
  const qs = params.toString();
  return apiFetch<PageResponse<ProposalDto>>(`/api/proposals${qs ? `?${qs}` : ""}`);
}
```

- [ ] **Step 3: model/index.ts 작성**

```ts
// frontend/src/entities/proposal/model/index.ts
import { useQuery } from "@tanstack/react-query";
import { fetchProposals, type ProposalQuery } from "../api";

export function useProposals(query: ProposalQuery = {}) {
  return useQuery({
    queryKey: ["proposals", query],
    queryFn: () => fetchProposals(query),
  });
}
```

- [ ] **Step 4: 슬라이스 Public API 작성**

```ts
// frontend/src/entities/proposal/index.ts
export type { ProposalDto, ProposalQuery } from "./api";
export { fetchProposals } from "./api";
export { useProposals } from "./model";
```

- [ ] **Step 5: features/proposals/api.ts 정리 (조회 제거)**

Edit `frontend/src/features/proposals/api.ts` — 다음만 남김:

```ts
import { apiFetch } from "@/shared/api/csrf";
import type { ProposalDto } from "@/entities/proposal";
export type { ProposalDto };

export interface ProposalCreateRequest {
  customerName: string;
  phoneNumber: string;
  birthDate: string;
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

(features/proposals 폴더는 PR 3에서 features/proposal/create로 이동.)

- [ ] **Step 6: features/proposals/queries.ts 정리 (useProposals 제거)**

Edit `frontend/src/features/proposals/queries.ts`:

```ts
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createProposal, type ProposalCreateRequest } from "./api";

export function useCreateProposal() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (req: ProposalCreateRequest) => createProposal(req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["proposals"] });
    },
  });
}
```

- [ ] **Step 7: 사용처 갱신**

Run:
```powershell
Select-String -Path frontend/src -Recurse -Pattern 'from "@/features/proposals/queries"' -List | Select-Object -ExpandProperty Path
```

`useProposals` import → `@/entities/proposal`.
`ProposalFormModal`, `useCreateProposal` import는 `@/features/proposals/*` 유지 (PR 3에서 처리).

특히 `frontend/src/app/proposals/page.tsx`:

```ts
import { useProposals } from "@/entities/proposal";
import { ProposalFormModal } from "@/features/proposals/ProposalFormModal";
```

- [ ] **Step 8: 타입체크 + 빌드**

Run: `cd frontend; pnpm typecheck; pnpm build`
Expected: 통과.

---

## Task 9: `entities/contract` 이동 (PolicyDto)

**Files:**
- Create: `frontend/src/entities/contract/api/index.ts`
- Create: `frontend/src/entities/contract/model/index.ts`
- Create: `frontend/src/entities/contract/index.ts`
- Delete: `frontend/src/features/contracts/`

`features/contracts`에 mutation이 없으므로 통째 이동.

- [ ] **Step 1: 폴더 생성**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/entities/contract/api -Force
New-Item -ItemType Directory -Path frontend/src/entities/contract/model -Force
```

- [ ] **Step 2: 파일 이동 + 이름 변경**

Run:
```powershell
Move-Item frontend/src/features/contracts/api.ts frontend/src/entities/contract/api/index.ts
Move-Item frontend/src/features/contracts/queries.ts frontend/src/entities/contract/model/index.ts
Remove-Item frontend/src/features/contracts -Recurse
```

- [ ] **Step 3: model/index.ts의 import 경로 갱신**

Edit `frontend/src/entities/contract/model/index.ts` — `./api` 가 슬라이스 내부 api/ 폴더를 가리키도록:

```ts
import { fetchPolicies, type PolicyQuery } from "../api";
// (기존 ./api → ../api)
```

- [ ] **Step 4: 슬라이스 Public API 작성**

```ts
// frontend/src/entities/contract/index.ts
export type { PolicyDto, PolicyQuery } from "./api";
export { fetchPolicies } from "./api";
export { usePolicies } from "./model";
```

(식별자는 백엔드 DTO와 동기화 위해 `Policy`/`usePolicies` 그대로.)

- [ ] **Step 5: 사용처 갱신**

Run:
```powershell
Select-String -Path frontend/src -Recurse -Pattern '@/features/contracts' -List | Select-Object -ExpandProperty Path
```

치환:
- `@/features/contracts/queries` → `@/entities/contract`
- `@/features/contracts/api` → `@/entities/contract`

주요 대상: `app/contracts/page.tsx`, `app/underwriting/page.tsx`.

- [ ] **Step 6: 타입체크 + 빌드**

Run: `cd frontend; pnpm typecheck; pnpm build`
Expected: 통과.

---

## Task 10: `entities/claim` 생성 (조회만)

**Files:**
- Create: `frontend/src/entities/claim/api/index.ts`
- Create: `frontend/src/entities/claim/model/index.ts`
- Create: `frontend/src/entities/claim/index.ts`
- Modify: `frontend/src/features/claims/api.ts` (createClaim만 남김)
- Modify: `frontend/src/features/claims/queries.ts` (useCreateClaim만 남김)

- [ ] **Step 1: 현재 features/claims/api.ts 내용 확인**

Run: `cat frontend/src/features/claims/api.ts`

AGENT2 산출물에서 `ClaimDto`, `ClaimQuery`, `fetchClaims`, `createClaim` 등 식별. 정확한 필드는 그 파일에서 그대로 복사.

- [ ] **Step 2: 폴더 생성**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/entities/claim/api -Force
New-Item -ItemType Directory -Path frontend/src/entities/claim/model -Force
```

- [ ] **Step 3: api/index.ts 작성 (조회 부분)**

```ts
// frontend/src/entities/claim/api/index.ts
import { apiFetch } from "@/shared/api/csrf";
import type { PageResponse } from "@/shared/api/types";

// ClaimDto, ClaimQuery: features/claims/api.ts 의 정의 그대로 복사
// fetchClaims: 동일

// 예시 (실제 필드는 AGENT2 산출물 확인):
export interface ClaimDto { /* ... */ }
export interface ClaimQuery { page?: number; size?: number; status?: string; }

export function fetchClaims(query: ClaimQuery = {}): Promise<PageResponse<ClaimDto>> {
  /* features/claims/api.ts에서 그대로 복사 */
}
```

- [ ] **Step 4: model/index.ts 작성**

```ts
// frontend/src/entities/claim/model/index.ts
import { useQuery } from "@tanstack/react-query";
import { fetchClaims, type ClaimQuery } from "../api";

export function useClaims(query: ClaimQuery = {}) {
  return useQuery({
    queryKey: ["claims", query],
    queryFn: () => fetchClaims(query),
  });
}
```

(실제 `useClaims` 시그니처는 현재 `features/claims/queries.ts`를 확인하고 동일하게 옮김.)

- [ ] **Step 5: 슬라이스 Public API**

```ts
// frontend/src/entities/claim/index.ts
export type { ClaimDto, ClaimQuery } from "./api";
export { fetchClaims } from "./api";
export { useClaims } from "./model";
```

- [ ] **Step 6: features/claims/api.ts 정리**

`ClaimDto`, `ClaimQuery`, `fetchClaims` 삭제. `createClaim`, `ClaimCreateRequest` 만 남김:

```ts
import { apiFetch } from "@/shared/api/csrf";
import type { ClaimDto } from "@/entities/claim";
export type { ClaimDto };

export interface ClaimCreateRequest { /* AGENT2 산출물 그대로 */ }

export function createClaim(req: ClaimCreateRequest): Promise<ClaimDto> {
  /* AGENT2 산출물 그대로 */
}
```

- [ ] **Step 7: features/claims/queries.ts 정리**

`useClaims` 삭제, `useCreateClaim` 만 남김.

- [ ] **Step 8: 사용처 갱신**

Run:
```powershell
Select-String -Path frontend/src -Recurse -Pattern '@/features/claims/queries' -List
```

`useClaims` 호출처 → `@/entities/claim`. 특히 `app/claims/page.tsx`.

- [ ] **Step 9: 타입체크 + 빌드**

Run: `cd frontend; pnpm typecheck; pnpm build`
Expected: 통과.

---

## Task 11: `entities/customer` 생성 (AGENT2 기반, 조회만)

**Files:**
- Create: `frontend/src/entities/customer/api/index.ts`
- Create: `frontend/src/entities/customer/model/index.ts`
- Create: `frontend/src/entities/customer/index.ts`
- Modify: `frontend/src/features/customers/api.ts`
- Modify: `frontend/src/features/customers/queries.ts`

- [ ] **Step 1: 폴더 생성**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/entities/customer/api -Force
New-Item -ItemType Directory -Path frontend/src/entities/customer/model -Force
```

- [ ] **Step 2: api/index.ts 작성**

```ts
// frontend/src/entities/customer/api/index.ts
import { apiFetch } from "@/shared/api/csrf";
import type { PageResponse } from "@/shared/api/types";

export interface CustomerDto {
  id: string;
  name: string;
  phone: string;
  birthDate: string | null;
  gender: string | null;
  email: string | null;
  address: string | null;
  memo: string | null;
  createdAt: string;
}

export interface CustomerQuery {
  page?: number;
  size?: number;
}

export function fetchCustomers(query: CustomerQuery = {}): Promise<PageResponse<CustomerDto>> {
  const params = new URLSearchParams();
  if (query.page !== undefined) params.set("page", String(query.page));
  if (query.size !== undefined) params.set("size", String(query.size));
  const qs = params.toString();
  return apiFetch<PageResponse<CustomerDto>>(`/api/customers${qs ? `?${qs}` : ""}`);
}
```

- [ ] **Step 3: model/index.ts 작성**

```ts
// frontend/src/entities/customer/model/index.ts
import { useQuery } from "@tanstack/react-query";
import { fetchCustomers, type CustomerQuery } from "../api";

export function useCustomers(query: CustomerQuery = {}) {
  return useQuery({
    queryKey: ["customers", query],
    queryFn: () => fetchCustomers(query),
  });
}
```

- [ ] **Step 4: 슬라이스 Public API**

```ts
// frontend/src/entities/customer/index.ts
export type { CustomerDto, CustomerQuery } from "./api";
export { fetchCustomers } from "./api";
export { useCustomers } from "./model";
```

- [ ] **Step 5: features/customers/api.ts 정리**

`CustomerDto`, `CustomerQuery`, `fetchCustomers` 삭제. mutation만 남김:

```ts
import { apiFetch } from "@/shared/api/csrf";
import type { CustomerDto } from "@/entities/customer";
export type { CustomerDto };

export interface CustomerWriteRequest { /* AGENT2 산출물 그대로 */ }

export function createCustomer(req: CustomerWriteRequest): Promise<CustomerDto> { /* ... */ }
export function updateCustomer(id: string, req: CustomerWriteRequest): Promise<CustomerDto> { /* ... */ }
export function deleteCustomer(id: string): Promise<void> { /* ... */ }
```

- [ ] **Step 6: features/customers/queries.ts 정리**

`useCustomers` 삭제, `useCreateCustomer`, `useUpdateCustomer`, `useDeleteCustomer` 만 남김.

- [ ] **Step 7: 사용처 갱신**

`useCustomers` 호출처 (특히 `app/customers/page.tsx`) → `@/entities/customer`.

- [ ] **Step 8: 타입체크 + 빌드**

Run: `cd frontend; pnpm typecheck; pnpm build`
Expected: 통과.

---

## Task 12: `entities/dashboard` 이동

**Files:**
- Create: `frontend/src/entities/dashboard/api/index.ts`
- Create: `frontend/src/entities/dashboard/model/index.ts`
- Create: `frontend/src/entities/dashboard/index.ts`
- Delete: `frontend/src/features/dashboard/`

- [ ] **Step 1: 현재 dashboard 파일 내용 확인**

Run: `cat frontend/src/features/dashboard/api.ts frontend/src/features/dashboard/queries.ts`

export 식별자 파악 (예: `DashboardSummaryDto`, `fetchDashboard`, `useDashboard` 등).

- [ ] **Step 2: 폴더 생성**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/entities/dashboard/api -Force
New-Item -ItemType Directory -Path frontend/src/entities/dashboard/model -Force
```

- [ ] **Step 3: 파일 이동 + 경로 변경**

Run:
```powershell
Move-Item frontend/src/features/dashboard/api.ts frontend/src/entities/dashboard/api/index.ts
Move-Item frontend/src/features/dashboard/queries.ts frontend/src/entities/dashboard/model/index.ts
Remove-Item frontend/src/features/dashboard -Recurse
```

- [ ] **Step 4: 이동된 파일들의 import 경로 갱신**

Edit `frontend/src/entities/dashboard/api/index.ts`:
- `@/lib/api/csrf` → `@/shared/api/csrf` (PR 1에서 안 된 경우)

Edit `frontend/src/entities/dashboard/model/index.ts`:
- 같은 슬라이스 내 api 참조: `./api` → `../api`

- [ ] **Step 5: 슬라이스 Public API**

```ts
// frontend/src/entities/dashboard/index.ts
// Step 1에서 확인한 export 식별자 그대로:
export type { /* DashboardSummaryDto 등 */ } from "./api";
export { /* fetchDashboard 등 */ } from "./api";
export { /* useDashboardSummary 등 */ } from "./model";
```

- [ ] **Step 6: 사용처 갱신**

Run:
```powershell
Select-String -Path frontend/src -Recurse -Pattern '@/features/dashboard' -List
```

`@/features/dashboard/queries`, `@/features/dashboard/api` → `@/entities/dashboard`.
특히 `app/dashboard/page.tsx`.

- [ ] **Step 7: 타입체크 + 빌드**

Run: `cd frontend; pnpm typecheck; pnpm build`
Expected: 통과.

---

## Task 13: `entities/user` 이동 (admin → user 리네이밍)

**Files:**
- Create: `frontend/src/entities/user/api/index.ts`
- Create: `frontend/src/entities/user/model/index.ts`
- Create: `frontend/src/entities/user/index.ts`
- Delete: `frontend/src/features/admin/`

- [ ] **Step 1: 현재 admin 파일 내용 확인**

Run: `cat frontend/src/features/admin/api.ts frontend/src/features/admin/queries.ts`

- [ ] **Step 2: 폴더 생성**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/entities/user/api -Force
New-Item -ItemType Directory -Path frontend/src/entities/user/model -Force
```

- [ ] **Step 3: 파일 이동**

Run:
```powershell
Move-Item frontend/src/features/admin/api.ts frontend/src/entities/user/api/index.ts
Move-Item frontend/src/features/admin/queries.ts frontend/src/entities/user/model/index.ts
Remove-Item frontend/src/features/admin -Recurse
```

- [ ] **Step 4: import 경로 갱신**

`@/lib/api/csrf` → `@/shared/api/csrf` (필요 시).
model/index.ts 안의 api 참조: `./api` → `../api`.

- [ ] **Step 5: 슬라이스 Public API**

```ts
// frontend/src/entities/user/index.ts
// Step 1에서 확인한 식별자 export
```

- [ ] **Step 6: 사용처 갱신**

`@/features/admin/queries`, `@/features/admin/api` → `@/entities/user`.
특히 `app/admin/users/page.tsx`.

- [ ] **Step 7: 타입체크 + 빌드**

Run: `cd frontend; pnpm typecheck; pnpm build`
Expected: 통과.

---

## Task 14: `entities/session` 생성 (auth read 부분)

**Files:**
- Create: `frontend/src/entities/session/api/index.ts`
- Create: `frontend/src/entities/session/model/index.ts`
- Create: `frontend/src/entities/session/lib/server.ts`
- Create: `frontend/src/entities/session/index.ts`
- Modify: `frontend/src/features/auth/api.ts`
- Modify: `frontend/src/features/auth/queries.ts`

`me`, `MeResponse`, `useMe`, `getMe` → session entity. `getMe`는 server-only이므로 `lib/server.ts`.

- [ ] **Step 1: 폴더 생성**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/entities/session/api -Force
New-Item -ItemType Directory -Path frontend/src/entities/session/model -Force
New-Item -ItemType Directory -Path frontend/src/entities/session/lib -Force
```

- [ ] **Step 2: api/index.ts 작성**

```ts
// frontend/src/entities/session/api/index.ts
import { apiFetch } from "@/shared/api/csrf";

export interface MeResponse {
  id: string;
  role: string;
}

export function me(): Promise<MeResponse> {
  return apiFetch<MeResponse>("/api/auth/me");
}
```

- [ ] **Step 3: model/index.ts 작성**

```ts
// frontend/src/entities/session/model/index.ts
import { useQuery } from "@tanstack/react-query";
import { me } from "../api";

export function useMe() {
  return useQuery({
    queryKey: ["me"],
    queryFn: me,
    retry: false,
  });
}
```

- [ ] **Step 4: lib/server.ts 작성 (getMe 이동)**

Run: `Move-Item frontend/src/features/auth/server.ts frontend/src/entities/session/lib/server.ts`

Edit `frontend/src/entities/session/lib/server.ts`:

```ts
import { cookies } from "next/headers";
import type { MeResponse } from "../api";

export async function getMe(): Promise<MeResponse | null> {
  // 기존 내용 유지, MeResponse import 경로만 변경
}
```

- [ ] **Step 5: 슬라이스 Public API**

```ts
// frontend/src/entities/session/index.ts
export type { MeResponse } from "./api";
export { me } from "./api";
export { useMe } from "./model";
export { getMe } from "./lib/server";
```

(Next.js: server-only 모듈은 server component에서만 import. client component에서 `getMe` import 시 빌드 에러로 잡힘.)

- [ ] **Step 6: features/auth/api.ts 정리**

`me`, `MeResponse` 삭제. `login`, `logout`, `register` 만 남김.

- [ ] **Step 7: features/auth/queries.ts 정리**

`useMe` 삭제. `useLoginMutation`, `useLogoutMutation`, `useRegisterMutation` 만 남김.

- [ ] **Step 8: 사용처 갱신**

Run:
```powershell
Select-String -Path frontend/src -Recurse -Pattern '@/features/auth/(server|queries)' -List
```

- `useMe` import → `@/entities/session`
- `getMe` import → `@/entities/session` (server components에서만)

주요 대상: `app/layout.tsx`, `app/dashboard/page.tsx`, `widgets/auth-guard`(아직 components/layout) 등.

- [ ] **Step 9: 타입체크 + 빌드**

Run: `cd frontend; pnpm typecheck; pnpm build`
Expected: 통과.

---

## Task 15: PR 2 최종 검증 및 커밋

- [ ] **Step 1: tsconfig에 entities alias 추가**

Edit `frontend/tsconfig.json`:

```jsonc
{
  "compilerOptions": {
    "paths": {
      "@/*": ["./src/*"],
      "@/shared/*": ["./src/shared/*"],
      "@/entities/*": ["./src/entities/*"]
    }
  }
}
```

- [ ] **Step 2: 잔존 옛 경로 검사**

Run:
```powershell
Select-String -Path frontend/src -Recurse -Pattern '@/features/(contracts|dashboard|admin)|@/features/auth/server'
```

Expected: 출력 없음.

- [ ] **Step 3: 전체 검증**

Run: `cd frontend; pnpm typecheck; pnpm lint; pnpm build`
Expected: 통과.

- [ ] **Step 4: 수동 스모크 (개발 서버)**

Run: `cd frontend; pnpm dev`

ADMIN 로그인 → 대시보드, /proposals, /contracts, /underwriting, /claims, /customers, /admin/users 페이지 데이터 로드 확인. SSR 인증(getMe) 정상 동작 확인.

- [ ] **Step 5: 커밋**

```powershell
git add frontend/src/entities frontend/tsconfig.json frontend/src/features frontend/src/app
git status
git commit -m "refactor(frontend): introduce entities layer (FSD PR 2/4)

- Split features/proposals -> entities/proposal (read) + features/proposals (create)
- Split features/claims -> entities/claim + features/claims (mutations)
- Split features/customers -> entities/customer + features/customers (mutations)
- Move features/contracts -> entities/contract
- Move features/dashboard -> entities/dashboard
- Move features/admin -> entities/user (domain naming)
- Split features/auth read -> entities/session (api/model/lib server)
- Each slice uses api/ + model/ segment folders
- Add @/entities/* tsconfig alias

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 6: 사용자 승인 후 push/PR**

---

# PR 3: `features` + `widgets` 레이어

## Task 16: `features/proposal/create` 생성

**Files:**
- Create: `frontend/src/features/proposal/create/api/index.ts`
- Create: `frontend/src/features/proposal/create/model/index.ts`
- Create: `frontend/src/features/proposal/create/ui/index.tsx`
- Create: `frontend/src/features/proposal/create/index.ts`
- Delete: `frontend/src/features/proposals/`

- [ ] **Step 1: 폴더 생성**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/features/proposal/create/api -Force
New-Item -ItemType Directory -Path frontend/src/features/proposal/create/model -Force
New-Item -ItemType Directory -Path frontend/src/features/proposal/create/ui -Force
```

- [ ] **Step 2: api/index.ts 작성**

```ts
// frontend/src/features/proposal/create/api/index.ts
import { apiFetch } from "@/shared/api/csrf";
import type { ProposalDto } from "@/entities/proposal";

export interface ProposalCreateRequest {
  customerName: string;
  phoneNumber: string;
  birthDate: string;
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

- [ ] **Step 3: model/index.ts 작성**

```ts
// frontend/src/features/proposal/create/model/index.ts
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createProposal, type ProposalCreateRequest } from "../api";

export function useCreateProposal() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (req: ProposalCreateRequest) => createProposal(req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["proposals"] });
    },
  });
}
```

- [ ] **Step 4: ui 이동**

Run: `Move-Item frontend/src/features/proposals/ProposalFormModal.tsx frontend/src/features/proposal/create/ui/index.tsx`

Edit `frontend/src/features/proposal/create/ui/index.tsx`:
- `import { useCreateProposal } from "../model";`
- `import { Button } from "@/shared/ui/button";`
- `import { TextField } from "@/shared/ui/text-field";`
- 다른 entity 참조 시 `@/entities/proposal` 사용

- [ ] **Step 5: 슬라이스 Public API**

```ts
// frontend/src/features/proposal/create/index.ts
export { ProposalFormModal } from "./ui";
export { useCreateProposal } from "./model";
export type { ProposalCreateRequest } from "./api";
```

- [ ] **Step 6: 사용처 갱신**

`frontend/src/app/proposals/page.tsx`:

```ts
import { ProposalFormModal } from "@/features/proposal/create";
```

(`useProposals`는 이미 `@/entities/proposal`로 변경됨.)

- [ ] **Step 7: 옛 features/proposals 삭제**

Run: `Remove-Item frontend/src/features/proposals -Recurse`

- [ ] **Step 8: 타입체크 + 빌드**

Run: `cd frontend; pnpm typecheck; pnpm build`
Expected: 통과.

---

## Task 17: `features/customer/manage` 생성 (생성+편집)

**Files:**
- Create: `frontend/src/features/customer/manage/api/index.ts`
- Create: `frontend/src/features/customer/manage/model/index.ts`
- Create: `frontend/src/features/customer/manage/ui/index.tsx`
- Create: `frontend/src/features/customer/manage/index.ts`

- [ ] **Step 1: 폴더 생성**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/features/customer/manage/api -Force
New-Item -ItemType Directory -Path frontend/src/features/customer/manage/model -Force
New-Item -ItemType Directory -Path frontend/src/features/customer/manage/ui -Force
```

- [ ] **Step 2: api/index.ts 작성**

```ts
// frontend/src/features/customer/manage/api/index.ts
import { apiFetch } from "@/shared/api/csrf";
import type { CustomerDto } from "@/entities/customer";

export interface CustomerWriteRequest {
  name: string;
  phone: string;
  birthDate?: string | null;
  gender?: string | null;
  email?: string | null;
  address?: string | null;
  memo?: string | null;
}

export function createCustomer(req: CustomerWriteRequest): Promise<CustomerDto> {
  return apiFetch<CustomerDto>("/api/customers", {
    method: "POST",
    body: JSON.stringify(req),
  });
}

export function updateCustomer(id: string, req: CustomerWriteRequest): Promise<CustomerDto> {
  return apiFetch<CustomerDto>(`/api/customers/${id}`, {
    method: "PUT",
    body: JSON.stringify(req),
  });
}
```

- [ ] **Step 3: model/index.ts 작성**

```ts
// frontend/src/features/customer/manage/model/index.ts
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createCustomer, updateCustomer, type CustomerWriteRequest } from "../api";

export function useCreateCustomer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CustomerWriteRequest) => createCustomer(req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["customers"] }),
  });
}

export function useUpdateCustomer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: string; req: CustomerWriteRequest }) => updateCustomer(id, req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["customers"] }),
  });
}
```

- [ ] **Step 4: ui 이동**

Run: `Move-Item frontend/src/features/customers/CustomerFormModal.tsx frontend/src/features/customer/manage/ui/index.tsx`

Edit `frontend/src/features/customer/manage/ui/index.tsx`:
- `import { useCreateCustomer, useUpdateCustomer } from "../model";`
- `import type { CustomerDto } from "@/entities/customer";`
- `import { Button } from "@/shared/ui/button";`
- `import { TextField } from "@/shared/ui/text-field";`
- export 이름 `CustomerFormModal` 유지

- [ ] **Step 5: 슬라이스 Public API**

```ts
// frontend/src/features/customer/manage/index.ts
export { CustomerFormModal } from "./ui";
export { useCreateCustomer, useUpdateCustomer } from "./model";
export type { CustomerWriteRequest } from "./api";
```

- [ ] **Step 6: 사용처 갱신**

`frontend/src/app/customers/page.tsx`:

```ts
import { CustomerFormModal } from "@/features/customer/manage";
import { useCustomers } from "@/entities/customer";
```

- [ ] **Step 7: 타입체크 + 빌드**

Run: `cd frontend; pnpm typecheck; pnpm build`
Expected: 통과.

---

## Task 18: `features/customer/delete` 생성

**Files:**
- Create: `frontend/src/features/customer/delete/api/index.ts`
- Create: `frontend/src/features/customer/delete/model/index.ts`
- Create: `frontend/src/features/customer/delete/index.ts`

- [ ] **Step 1: 폴더 생성**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/features/customer/delete/api -Force
New-Item -ItemType Directory -Path frontend/src/features/customer/delete/model -Force
```

- [ ] **Step 2: api/index.ts 작성**

```ts
// frontend/src/features/customer/delete/api/index.ts
import { apiFetch } from "@/shared/api/csrf";

export function deleteCustomer(id: string): Promise<void> {
  return apiFetch<void>(`/api/customers/${id}`, { method: "DELETE" });
}
```

- [ ] **Step 3: model/index.ts 작성**

```ts
// frontend/src/features/customer/delete/model/index.ts
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { deleteCustomer } from "../api";

export function useDeleteCustomer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteCustomer(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["customers"] }),
  });
}
```

- [ ] **Step 4: 슬라이스 Public API**

```ts
// frontend/src/features/customer/delete/index.ts
export { useDeleteCustomer } from "./model";
```

- [ ] **Step 5: 사용처 갱신**

`frontend/src/app/customers/page.tsx`:

```ts
import { useDeleteCustomer } from "@/features/customer/delete";
```

- [ ] **Step 6: 옛 features/customers 삭제**

Run:
```powershell
Select-String -Path frontend/src -Recurse -Pattern '@/features/customers'
```
Expected: 출력 없음.

Run: `Remove-Item frontend/src/features/customers -Recurse`

- [ ] **Step 7: 타입체크 + 빌드**

Run: `cd frontend; pnpm typecheck; pnpm build`
Expected: 통과.

---

## Task 19: `features/claim/create` 생성 (AGENT2 기반)

**Files:**
- Create: `frontend/src/features/claim/create/api/index.ts`
- Create: `frontend/src/features/claim/create/model/index.ts`
- Create: `frontend/src/features/claim/create/ui/index.tsx`
- Create: `frontend/src/features/claim/create/index.ts`
- Delete: `frontend/src/features/claims/`

- [ ] **Step 1: 폴더 생성**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/features/claim/create/api -Force
New-Item -ItemType Directory -Path frontend/src/features/claim/create/model -Force
New-Item -ItemType Directory -Path frontend/src/features/claim/create/ui -Force
```

- [ ] **Step 2: api/index.ts 작성**

`features/claims/api.ts`의 `createClaim`, `ClaimCreateRequest`를 그대로 옮김:

```ts
// frontend/src/features/claim/create/api/index.ts
import { apiFetch } from "@/shared/api/csrf";
import type { ClaimDto } from "@/entities/claim";

export interface ClaimCreateRequest {
  // AGENT2 산출물에서 정확한 필드 가져오기
}

export function createClaim(req: ClaimCreateRequest): Promise<ClaimDto> {
  return apiFetch<ClaimDto>("/api/claims", {
    method: "POST",
    body: JSON.stringify(req),
  });
}
```

- [ ] **Step 3: model/index.ts 작성**

```ts
// frontend/src/features/claim/create/model/index.ts
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createClaim, type ClaimCreateRequest } from "../api";

export function useCreateClaim() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: ClaimCreateRequest) => createClaim(req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["claims"] }),
  });
}
```

- [ ] **Step 4: ui 이동**

Run: `Move-Item frontend/src/features/claims/ClaimFormModal.tsx frontend/src/features/claim/create/ui/index.tsx`

Edit:
- `import { useCreateClaim } from "../model";`
- `import { Button } from "@/shared/ui/button";`
- `import { TextField } from "@/shared/ui/text-field";`
- customer 선택 등 cross-entity 참조 시 `@/entities/customer`

- [ ] **Step 5: 슬라이스 Public API**

```ts
// frontend/src/features/claim/create/index.ts
export { ClaimFormModal } from "./ui";
export { useCreateClaim } from "./model";
export type { ClaimCreateRequest } from "./api";
```

- [ ] **Step 6: 사용처 갱신**

`frontend/src/app/claims/page.tsx`:

```ts
import { ClaimFormModal } from "@/features/claim/create";
import { useClaims } from "@/entities/claim";
```

- [ ] **Step 7: 옛 features/claims 삭제**

Run:
```powershell
Select-String -Path frontend/src -Recurse -Pattern '@/features/claims'
```
Expected: 출력 없음.

Run: `Remove-Item frontend/src/features/claims -Recurse`

- [ ] **Step 8: 타입체크 + 빌드**

Run: `cd frontend; pnpm typecheck; pnpm build`
Expected: 통과.

---

## Task 20: `features/auth/login` 생성

**Files:**
- Create: `frontend/src/features/auth/login/api/index.ts`
- Create: `frontend/src/features/auth/login/model/index.ts`
- Create: `frontend/src/features/auth/login/index.ts`

- [ ] **Step 1: 폴더 생성**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/features/auth/login/api -Force
New-Item -ItemType Directory -Path frontend/src/features/auth/login/model -Force
```

- [ ] **Step 2: api/index.ts 작성**

```ts
// frontend/src/features/auth/login/api/index.ts
import { apiFetch } from "@/shared/api/csrf";

export interface LoginRequest {
  username: string;
  password: string;
}

export function login(body: LoginRequest): Promise<void> {
  return apiFetch<void>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify(body),
  });
}
```

(응답이 `MeResponse`였다면 `import type { MeResponse } from "@/entities/session"`로 추가.)

- [ ] **Step 3: model/index.ts 작성**

```ts
// frontend/src/features/auth/login/model/index.ts
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { login, type LoginRequest } from "../api";

export function useLoginMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: LoginRequest) => login(body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["me"] });
    },
  });
}
```

- [ ] **Step 4: 슬라이스 Public API**

```ts
// frontend/src/features/auth/login/index.ts
export { useLoginMutation } from "./model";
export type { LoginRequest } from "./api";
```

- [ ] **Step 5: 사용처 갱신**

`frontend/src/app/login/page.tsx`:

```ts
import { useLoginMutation } from "@/features/auth/login";
```

- [ ] **Step 6: 타입체크 + 빌드**

Run: `cd frontend; pnpm typecheck; pnpm build`
Expected: 통과.

---

## Task 21: `features/auth/register` 생성

**Files:**
- Create: `frontend/src/features/auth/register/api/index.ts`
- Create: `frontend/src/features/auth/register/model/index.ts`
- Create: `frontend/src/features/auth/register/index.ts`

- [ ] **Step 1: 폴더 생성**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/features/auth/register/api -Force
New-Item -ItemType Directory -Path frontend/src/features/auth/register/model -Force
```

- [ ] **Step 2: api/index.ts 작성**

```ts
// frontend/src/features/auth/register/api/index.ts
import { apiFetch } from "@/shared/api/csrf";

export interface RegisterRequest {
  id: string;
  password: string;
  name: string;
  phone: string;
  gaName: string;
  email: string;
}

export function register(body: RegisterRequest): Promise<void> {
  return apiFetch<void>("/api/auth/register", {
    method: "POST",
    body: JSON.stringify(body),
  });
}
```

- [ ] **Step 3: model/index.ts 작성**

```ts
// frontend/src/features/auth/register/model/index.ts
import { useMutation } from "@tanstack/react-query";
import { register, type RegisterRequest } from "../api";

export function useRegisterMutation() {
  return useMutation({
    mutationFn: (body: RegisterRequest) => register(body),
  });
}
```

- [ ] **Step 4: 슬라이스 Public API**

```ts
// frontend/src/features/auth/register/index.ts
export { useRegisterMutation } from "./model";
export type { RegisterRequest } from "./api";
```

- [ ] **Step 5: 사용처 갱신**

`frontend/src/app/register/page.tsx`:

```ts
import { useRegisterMutation } from "@/features/auth/register";
```

- [ ] **Step 6: 타입체크 + 빌드**

Run: `cd frontend; pnpm typecheck; pnpm build`
Expected: 통과.

---

## Task 22: `features/auth/logout` 생성

**Files:**
- Create: `frontend/src/features/auth/logout/api/index.ts`
- Create: `frontend/src/features/auth/logout/model/index.ts`
- Create: `frontend/src/features/auth/logout/ui/index.tsx`
- Create: `frontend/src/features/auth/logout/index.ts`
- Delete: `frontend/src/features/auth/api.ts`, `queries.ts`, `logout-button.tsx`

- [ ] **Step 1: 폴더 생성**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/features/auth/logout/api -Force
New-Item -ItemType Directory -Path frontend/src/features/auth/logout/model -Force
New-Item -ItemType Directory -Path frontend/src/features/auth/logout/ui -Force
```

- [ ] **Step 2: api/index.ts 작성**

```ts
// frontend/src/features/auth/logout/api/index.ts
import { apiFetch } from "@/shared/api/csrf";

export function logout(): Promise<void> {
  return apiFetch<void>("/api/auth/logout", { method: "POST" });
}
```

- [ ] **Step 3: model/index.ts 작성**

```ts
// frontend/src/features/auth/logout/model/index.ts
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { logout } from "../api";

export function useLogoutMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: logout,
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: ["me"] });
    },
  });
}
```

- [ ] **Step 4: ui 이동**

Run: `Move-Item frontend/src/features/auth/logout-button.tsx frontend/src/features/auth/logout/ui/index.tsx`

Edit `frontend/src/features/auth/logout/ui/index.tsx`:

```tsx
"use client";

import { useRouter } from "next/navigation";
import { Button } from "@/shared/ui/button";
import { useLogoutMutation } from "../model";

export function LogoutButton() {
  const router = useRouter();
  const logoutMutation = useLogoutMutation();

  return (
    <Button
      onClick={() =>
        logoutMutation.mutate(undefined, {
          onSuccess: () => router.push("/login"),
        })
      }
      loading={logoutMutation.isPending}
      className="!w-auto px-8"
    >
      로그아웃
    </Button>
  );
}
```

- [ ] **Step 5: 슬라이스 Public API**

```ts
// frontend/src/features/auth/logout/index.ts
export { LogoutButton } from "./ui";
export { useLogoutMutation } from "./model";
```

- [ ] **Step 6: 옛 features/auth 잔존 파일 삭제**

옛 `features/auth/{api.ts, queries.ts}` 가 이 시점에 남아있다면 (PR 2 후 잔여 mutation들):
- login/register/logout 함수와 mutation은 모두 새 슬라이스로 이동 완료해야 함
- 잔존 파일에 다른 내용이 없는지 확인

Run:
```powershell
cat frontend/src/features/auth/api.ts
cat frontend/src/features/auth/queries.ts
```

내용이 비었거나 새 슬라이스로 다 옮겨졌으면:
```powershell
Remove-Item frontend/src/features/auth/api.ts
Remove-Item frontend/src/features/auth/queries.ts
```

(`features/auth/` 폴더 자체는 login/register/logout 슬라이스를 담은 group이므로 유지.)

- [ ] **Step 7: 사용처 갱신**

Run:
```powershell
Select-String -Path frontend/src -Recurse -Pattern '@/features/auth/(api|queries|logout-button)' -List
```

각 파일에서:
- `LogoutButton` import → `@/features/auth/logout`
- `useLoginMutation`, `useLogoutMutation`, `useRegisterMutation` → 각 새 슬라이스 경로

- [ ] **Step 8: 타입체크 + 빌드**

Run: `cd frontend; pnpm typecheck; pnpm build`
Expected: 통과.

---

## Task 23: `widgets/app-shell` 이동

**Files:**
- Create: `frontend/src/widgets/app-shell/ui/index.tsx`
- Create: `frontend/src/widgets/app-shell/index.ts`

- [ ] **Step 1: 폴더 + 파일 이동**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/widgets/app-shell/ui -Force
Move-Item frontend/src/components/layout/app-shell.tsx frontend/src/widgets/app-shell/ui/index.tsx
```

- [ ] **Step 2: import 경로 갱신**

Edit `frontend/src/widgets/app-shell/ui/index.tsx`:
- 만약 sidebar, top-bar 등 다른 widget을 직접 import하고 있다면 → cross-widget import는 Steiger에서 잡힘. 해결: app-shell이 sidebar/top-bar를 직접 import하지 않고 children prop으로 받도록 리팩토링. 또는 `app/layout.tsx`에서 조립.

이 단계에서는 동작 우선 — 일단 import 경로만 갱신:
- `@/components/layout/sidebar` → `@/widgets/sidebar` (Task 24 이후)
- `@/components/layout/top-bar` → `@/widgets/top-bar` (Task 25 이후)

순서 의존 문제 발생 시 Task 23~26을 한 번에 처리.

- [ ] **Step 3: 슬라이스 Public API**

```ts
// frontend/src/widgets/app-shell/index.ts
export { AppShell } from "./ui";
```

(정확한 export 이름은 파일 확인.)

- [ ] **Step 4: 사용처 갱신**

`@/components/layout/app-shell` → `@/widgets/app-shell`.

- [ ] **Step 5: 타입체크 + 빌드**

(다른 widgets가 아직 안 옮겨졌으면 일시적 import 오류 가능. Task 26 종료 시 모든 widgets가 자리잡으면 통과.)

---

## Task 24: `widgets/sidebar` 이동

**Files:**
- Create: `frontend/src/widgets/sidebar/ui/index.tsx`
- Create: `frontend/src/widgets/sidebar/index.ts`

- [ ] **Step 1: 폴더 + 파일 이동**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/widgets/sidebar/ui -Force
Move-Item frontend/src/components/layout/sidebar.tsx frontend/src/widgets/sidebar/ui/index.tsx
```

- [ ] **Step 2: 슬라이스 Public API**

```ts
// frontend/src/widgets/sidebar/index.ts
export { Sidebar } from "./ui";
```

- [ ] **Step 3: 사용처 갱신**

`@/components/layout/sidebar` → `@/widgets/sidebar`.

- [ ] **Step 4: 타입체크 + 빌드**

Run: `cd frontend; pnpm typecheck; pnpm build`

---

## Task 25: `widgets/top-bar` 이동

**Files:**
- Create: `frontend/src/widgets/top-bar/ui/index.tsx`
- Create: `frontend/src/widgets/top-bar/index.ts`

- [ ] **Step 1: 폴더 + 파일 이동**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/widgets/top-bar/ui -Force
Move-Item frontend/src/components/layout/top-bar.tsx frontend/src/widgets/top-bar/ui/index.tsx
```

- [ ] **Step 2: 슬라이스 Public API**

```ts
// frontend/src/widgets/top-bar/index.ts
export { TopBar } from "./ui";
```

- [ ] **Step 3: 사용처 갱신**

`@/components/layout/top-bar` → `@/widgets/top-bar`.

- [ ] **Step 4: 타입체크 + 빌드**

Run: `cd frontend; pnpm typecheck; pnpm build`

---

## Task 26: `widgets/auth-guard` 이동 + components 폴더 제거

**Files:**
- Create: `frontend/src/widgets/auth-guard/ui/index.tsx`
- Create: `frontend/src/widgets/auth-guard/index.ts`
- Delete: `frontend/src/components/` (전체)

- [ ] **Step 1: 폴더 + 파일 이동**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/widgets/auth-guard/ui -Force
Move-Item frontend/src/components/layout/auth-guard.tsx frontend/src/widgets/auth-guard/ui/index.tsx
```

- [ ] **Step 2: 슬라이스 Public API**

```ts
// frontend/src/widgets/auth-guard/index.ts
export { AuthGuard } from "./ui";
```

- [ ] **Step 3: 사용처 갱신**

`@/components/layout/auth-guard` → `@/widgets/auth-guard`.

- [ ] **Step 4: 옛 components 폴더 완전 제거**

Run:
```powershell
Select-String -Path frontend/src -Recurse -Pattern '@/components/'
```
Expected: 출력 없음.

Run: `Remove-Item frontend/src/components -Recurse`

- [ ] **Step 5: 타입체크 + 빌드**

Run: `cd frontend; pnpm typecheck; pnpm build`
Expected: 통과.

---

## Task 27: tsconfig 최종 정리

**Files:**
- Modify: `frontend/tsconfig.json`

- [ ] **Step 1: paths 최종 형태**

Edit `frontend/tsconfig.json`:

```jsonc
{
  "compilerOptions": {
    "paths": {
      "@/app/*": ["./src/app/*"],
      "@/widgets/*": ["./src/widgets/*"],
      "@/features/*": ["./src/features/*"],
      "@/entities/*": ["./src/entities/*"],
      "@/shared/*": ["./src/shared/*"]
    }
  }
}
```

(기존 `@/*` 제거. `@/api/*` 같은 generated 스키마용 alias가 필요하면 별도 추가.)

- [ ] **Step 2: 잔존 `@/*` 검사**

Run:
```powershell
Select-String -Path frontend/src -Recurse -Pattern 'from "@/(?!app/|widgets/|features/|entities/|shared/)'
```

Expected: 출력 없음. (있으면 추가 alias가 필요한 경우.)

`frontend/src/api/schema.d.ts` 가 있는 경우 `@/api/*` alias 추가:

```jsonc
"paths": {
  "@/api/*": ["./src/api/*"],
  "@/app/*": ["./src/app/*"],
  ...
}
```

- [ ] **Step 3: 타입체크 + 빌드**

Run: `cd frontend; pnpm typecheck; pnpm build`
Expected: 통과.

---

## Task 28: PR 3 수동 스모크 테스트

**Files:** N/A

- [ ] **Step 1: 백엔드 + 프론트엔드 실행**

별도 터미널:
- `cd backend; ./gradlew bootRun`
- `cd frontend; pnpm dev`

- [ ] **Step 2: ADMIN 시나리오**

1. `/login` → ADMIN 로그인 → `/dashboard` 진입
2. 사이드바 모든 메뉴 표시
3. `/proposals` — 목록 로드 + 신규 설계 등록 모달 제출
4. `/underwriting` — 목록 + 상태 필터
5. `/contracts` 직접 URL — 목록 로드
6. `/claims` — 목록 + 청구 등록 모달
7. `/admin/users` — 사용자 목록
8. 로그아웃 → `/login` 리다이렉트

- [ ] **Step 3: AGENT2 시나리오**

1. AGENT2 로그인
2. 사이드바 메뉴: 대시보드/고객/청구 (설계/심사 미노출)
3. `/customers` — 목록 + 신규 등록 + 편집 + 삭제
4. `/claims` — 본인 고객 청구 등록
5. `/proposals` 직접 URL → 차단

- [ ] **Step 4: AGENT1 시나리오**

기존 AGENT1 메뉴 동작.

- [ ] **Step 5: 가입 흐름**

1. `/register` 폼 제출 → `/pending`
2. ADMIN으로 `/admin/users` 승인
3. 새 계정 로그인

문제 발생 시 해당 Task로 돌아가 수정.

---

## Task 29: PR 3 커밋

- [ ] **Step 1: 최종 구조 확인**

Run:
```powershell
Get-ChildItem frontend/src -Directory
Get-ChildItem frontend/src/features -Directory
Get-ChildItem frontend/src/features/auth -Directory
Get-ChildItem frontend/src/features/customer -Directory
```

Expected:
- `frontend/src`: app, entities, features, shared, widgets
- `frontend/src/features`: auth, claim, customer, proposal
- `frontend/src/features/auth`: login, logout, register
- `frontend/src/features/customer`: delete, manage

- [ ] **Step 2: 전체 검증**

Run: `cd frontend; pnpm typecheck; pnpm lint; pnpm build`
Expected: 통과.

- [ ] **Step 3: 커밋**

```powershell
git add frontend/src frontend/tsconfig.json
git status
git commit -m "refactor(frontend): introduce features and widgets layers (FSD PR 3/4)

- Group features by domain: auth/{login,register,logout}, proposal/create,
  customer/{manage,delete}, claim/create
- Each slice uses api/, model/, ui/ segment folders
- Move components/layout/* -> widgets/{app-shell,sidebar,top-bar,auth-guard}/ui/
- Remove legacy @/* alias, keep only @/{app,widgets,features,entities,shared}/*
- Delete legacy lib/, components/, features/{proposals,contracts,claims,customers,
  dashboard,admin,auth top-level files} folders

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 4: 사용자 승인 후 push/PR**

---

# PR 4: Steiger 도입 + CI 통합

## Task 30: Steiger 설치 및 설정

**Files:**
- Create: `frontend/steiger.config.ts`
- Modify: `frontend/package.json`

- [ ] **Step 1: 패키지 설치**

Run:
```powershell
cd frontend
pnpm add -D steiger @feature-sliced/steiger-plugin
```

- [ ] **Step 2: steiger.config.ts 작성**

```ts
// frontend/steiger.config.ts
import { defineConfig } from "steiger";
import fsd from "@feature-sliced/steiger-plugin";

export default defineConfig([
  ...fsd.configs.recommended,
]);
```

- [ ] **Step 3: package.json 스크립트 추가**

Edit `frontend/package.json` — `scripts`:

```jsonc
"lint:fsd": "steiger ./src"
```

- [ ] **Step 4: 첫 실행 + 위반 확인**

Run: `cd frontend; pnpm lint:fsd`

위반 발생 시:
- 레이어 위반 → 해당 import를 shared로 옮김
- 슬라이스 cross-import → 공통 타입을 shared로 추출
- Public API 위반 → 슬라이스 루트 import로 변경

룰을 끄지 않고 코드 수정으로 해결.

- [ ] **Step 5: 재실행 + 0건 확인**

Run: `cd frontend; pnpm lint:fsd`
Expected: 0건.

- [ ] **Step 6: 타입체크 + 빌드 재확인**

Run: `cd frontend; pnpm typecheck; pnpm build`
Expected: 통과.

---

## Task 31: CI 통합 + PR 4 커밋

**Files:**
- Modify: `.github/workflows/*.yml` (있는 경우)

- [ ] **Step 1: CI 파일 확인**

Run: `Get-ChildItem -Path .github/workflows -ErrorAction SilentlyContinue`

없으면 수동 `pnpm lint:fsd` 만 사용. 이 단계 생략.

- [ ] **Step 2: CI 파일 수정 (있는 경우)**

frontend 검증 단계에 `pnpm lint:fsd` 추가:

```yaml
- name: FSD lint
  working-directory: frontend
  run: pnpm lint:fsd
```

- [ ] **Step 3: 커밋**

```powershell
git add frontend/steiger.config.ts frontend/package.json frontend/pnpm-lock.yaml
git add .github/workflows  # CI 변경이 있으면
git commit -m "ci(frontend): add Steiger FSD lint (PR 4/4)

- Install steiger + @feature-sliced/steiger-plugin
- Add steiger.config.ts with recommended ruleset
- Add pnpm lint:fsd script
- Integrate into CI pipeline

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 4: 사용자 승인 후 push/PR**

---

# 완료 후 점검

- [ ] 4개 PR 모두 머지됨
- [ ] `frontend/src` 구조: app/widgets/features/entities/shared 5 레이어
- [ ] features 그룹핑: auth, proposal, customer, claim
- [ ] 각 슬라이스 내부에 api/model/ui/lib 폴더 segment
- [ ] `pnpm lint:fsd` 0건
- [ ] CI에 FSD 린트 통합
- [ ] ADMIN/AGENT1/AGENT2 역할별 스모크 테스트 통과

## Out of Scope

- 백엔드 식별자 리네이밍 (`Policy` → `Contract` 등)
- 라우트 URL 변경
- 테스트 코드 추가
- 새 use-case feature 추가
