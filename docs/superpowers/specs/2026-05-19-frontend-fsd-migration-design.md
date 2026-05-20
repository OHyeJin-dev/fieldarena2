# Frontend FSD Migration Design

- **Date**: 2026-05-19
- **Scope**: `frontend/src` only. Backend unchanged.
- **Goal**: Migrate the Next.js frontend from a flat `app / components / features / lib` layout to Feature-Sliced Design (Practical variant) with automated structural linting.
- **Prerequisite**: AGENT2 플랜 (`docs/superpowers/plans/2026-05-19-agent2-customer-claim.md`) 완료. customer/claim 신규 코드가 옛 구조에 만들어진 후, 본 마이그레이션이 함께 흡수한다.

## 1. Target Architecture

### 1.1 Layers (Practical FSD)

Five layers, top-down dependency only (a layer may only import from layers below it):

```
app  →  widgets  →  features  →  entities  →  shared
```

- `app/` — Next.js App Router. Routes compose widgets/features/entities. Doubles as the FSD `app` layer; no separate `pages` layer.
- `widgets/` — Page-composing UI blocks (sidebar, top bar, app shell, auth guard).
- `features/` — Use-case slices (mutations / user actions): create-proposal, login, register, logout.
- `entities/` — Domain model slices (DTO + read queries): proposal, contract, claim, dashboard, user, session.
- `shared/` — Domain-agnostic primitives reusable anywhere: API client, generic UI, config.

### 1.2 Slice rules

- Each slice exposes a **Public API** via `index.ts`. External imports must go through it (`@/entities/proposal`), not internal files (`@/entities/proposal/api`).
- Slices within the same layer **cannot import each other** (no `entities/proposal` → `entities/contract`). Shared types belong in `shared/`.
- **Slice internal structure (강제)**: 각 슬라이스는 `model/`, `ui/`, `lib/`, `api/` 폴더로 세그먼트 분리. 단일 파일도 폴더 안에 둠. 해당 세그먼트가 없으면 폴더 생략.
  - `api/` — fetch 함수 + DTO 타입
  - `model/` — React Query 훅, zod 스키마, 상태
  - `ui/` — React 컴포넌트
  - `lib/` — 슬라이스 내부 전용 유틸
- **features는 도메인 그룹으로 묶음**: `features/auth/{login,register,logout}`, `features/customer/{manage,delete}` 같은 2단계 구조. 단일 슬라이스도 그룹 안에 둠(`features/proposal/create`). 그룹은 슬라이스가 아니므로 index.ts 없음.

### 1.3 Final folder tree

```
frontend/src/
├── app/                          # Next.js App Router (unchanged)
│   ├── layout.tsx, providers.tsx, globals.css, page.tsx
│   ├── dashboard/page.tsx
│   ├── proposals/page.tsx        # 설계 관리
│   ├── underwriting/page.tsx     # 심사 현황 (contract entity의 status 필터 뷰)
│   ├── contracts/page.tsx        # 계약 (사이드바 비노출)
│   ├── claims/page.tsx
│   ├── customers/page.tsx        # 고객 관리 (AGENT2)
│   ├── admin/users/...
│   ├── login/page.tsx
│   ├── register/page.tsx
│   └── pending/page.tsx
│
├── widgets/
│   ├── app-shell/{ui/, index.ts}
│   ├── sidebar/{ui/, index.ts}
│   ├── top-bar/{ui/, index.ts}
│   └── auth-guard/{ui/, index.ts}
│
├── features/
│   ├── auth/                     # 인증 그룹
│   │   ├── login/{api/, model/, index.ts}
│   │   ├── register/{api/, model/, index.ts}
│   │   └── logout/{api/, model/, ui/, index.ts}
│   ├── proposal/                 # 설계 그룹
│   │   └── create/{api/, model/, ui/, index.ts}
│   ├── customer/                 # 고객 그룹
│   │   ├── manage/{api/, model/, ui/, index.ts}     # 생성+편집
│   │   └── delete/{api/, model/, index.ts}
│   └── claim/                    # 청구 그룹
│       └── create/{api/, model/, ui/, index.ts}
│
├── entities/
│   ├── proposal/{api/, model/, index.ts}           # ProposalDto, useProposals
│   ├── contract/{api/, model/, index.ts}           # PolicyDto, usePolicies
│   ├── claim/{api/, model/, index.ts}              # ClaimDto, useClaims (조회만)
│   ├── customer/{api/, model/, index.ts}           # CustomerDto, useCustomers (조회만)
│   ├── dashboard/{api/, model/, index.ts}
│   ├── user/{api/, model/, index.ts}               # admin 사용자 관리
│   └── session/{api/, model/, lib/, index.ts}      # me/useMe + getMe(server)
│
└── shared/
    ├── api/{client.ts, csrf.ts, types.ts, index.ts}  # PageResponse<T>
    ├── ui/                       # 디자인 시스템 컴포넌트
    │   ├── button/{ui/, index.ts}
    │   ├── stat-card/{ui/, index.ts}
    │   └── text-field/{ui/, index.ts}
    └── config/                   # (필요 시)
```

### 1.4 Naming decisions

- **`entities/contract`** (not `policy`): "계약"이 도메인 흐름(설계→심사→계약→청구)에서 더 큰 개념. 백엔드 DTO명 `PolicyDto`/`policyNumber`는 그대로 유지 (내부 식별자), 폴더와 외부 노출명만 `contract`.
- **`/contracts` 라우트 유지**: 사이드바 비노출이라 외부 영향 없음. entity 폴더와 라우트가 1:1.
- **`entities/user`** (not `admin`): admin은 권한 단어, 도메인은 user.
- **`entities/underwriting` 생성 X**: 백엔드가 `/api/policies` 단일 엔드포인트이고 `PolicyDto.status`로 심사/계약 단계 구분. 심사 페이지는 contract entity의 status 필터 뷰.

## 2. Slice Mapping (현재 → 대상)

| 현재 경로 | 대상 경로 |
|---|---|
| `lib/api/client.ts` | `shared/api/client.ts` |
| `lib/api/csrf.ts` | `shared/api/csrf.ts` |
| `features/contracts/api.ts` 의 `PageResponse<T>` | `shared/api/types.ts` |
| `components/ui/button.tsx` | `shared/ui/button/ui/index.tsx` + `shared/ui/button/index.ts` |
| `components/ui/stat-card.tsx` | `shared/ui/stat-card/ui/index.tsx` + `index.ts` |
| `components/ui/text-field.tsx` | `shared/ui/text-field/ui/index.tsx` + `index.ts` |
| `components/layout/app-shell.tsx` | `widgets/app-shell/ui/index.tsx` + `index.ts` |
| `components/layout/sidebar.tsx` | `widgets/sidebar/ui/index.tsx` + `index.ts` |
| `components/layout/top-bar.tsx` | `widgets/top-bar/ui/index.tsx` + `index.ts` |
| `components/layout/auth-guard.tsx` | `widgets/auth-guard/ui/index.tsx` + `index.ts` |
| `features/proposals/api.ts` (ProposalDto + fetchProposals) | `entities/proposal/api/` |
| `features/proposals/queries.ts` 의 `useProposals` | `entities/proposal/model/` |
| `features/proposals/api.ts` 의 `createProposal` | `features/proposal/create/api/` |
| `features/proposals/queries.ts` 의 `useCreateProposal` | `features/proposal/create/model/` |
| `features/proposals/ProposalFormModal.tsx` | `features/proposal/create/ui/` |
| `features/contracts/{api,queries}.ts` | `entities/contract/{api,model}/` (식별자 `PolicyDto`, `usePolicies` 유지) |
| `features/claims/api.ts` 의 `ClaimDto`, `fetchClaims` | `entities/claim/api/` |
| `features/claims/queries.ts` 의 `useClaims` | `entities/claim/model/` |
| `features/claims/api.ts` 의 `createClaim` (AGENT2) | `features/claim/create/api/` |
| `features/claims/queries.ts` 의 `useCreateClaim` (AGENT2) | `features/claim/create/model/` |
| `features/claims/ClaimFormModal.tsx` (AGENT2) | `features/claim/create/ui/` |
| `features/customers/api.ts` 의 `CustomerDto`, `fetchCustomers` (AGENT2) | `entities/customer/api/` |
| `features/customers/queries.ts` 의 `useCustomers` (AGENT2) | `entities/customer/model/` |
| `features/customers/api.ts` 의 `createCustomer`, `updateCustomer` (AGENT2) | `features/customer/manage/api/` |
| `features/customers/queries.ts` 의 `useCreateCustomer`, `useUpdateCustomer` (AGENT2) | `features/customer/manage/model/` |
| `features/customers/CustomerFormModal.tsx` (AGENT2) | `features/customer/manage/ui/` |
| `features/customers/api.ts` 의 `deleteCustomer` (AGENT2) | `features/customer/delete/api/` |
| `features/customers/queries.ts` 의 `useDeleteCustomer` (AGENT2) | `features/customer/delete/model/` |
| `features/dashboard/{api,queries}.ts` | `entities/dashboard/{api,model}/` |
| `features/admin/{api,queries}.ts` | `entities/user/{api,model}/` |
| `features/auth/server.ts` + 세션 조회 부분 (`me`, `useMe`) | `entities/session/{api,model,lib}/` |
| `features/auth/` 의 login API | `features/auth/login/{api,model}/` |
| `features/auth/` 의 register API | `features/auth/register/{api,model}/` |
| `features/auth/logout-button.tsx` + logout API | `features/auth/logout/{api,model,ui}/` |
| `app/**/page.tsx` | 이동 없음, import 경로만 갱신 |

### Public API 예시

```ts
// entities/proposal/api/index.ts
export type { ProposalDto, ProposalQuery } from "./types";
export { fetchProposals } from "./fetch";

// entities/proposal/model/index.ts
export { useProposals } from "./queries";

// entities/proposal/index.ts (Public API)
export type { ProposalDto, ProposalQuery } from "./api";
export { fetchProposals } from "./api";
export { useProposals } from "./model";
```

```ts
// features/proposal/create/index.ts
export { ProposalFormModal } from "./ui";
export { useCreateProposal } from "./model";
export type { ProposalCreateRequest } from "./api";
```

```ts
// features/customer/manage/index.ts
export { CustomerFormModal } from "./ui";
export { useCreateCustomer, useUpdateCustomer } from "./model";
export type { CustomerWriteRequest } from "./api";
```

### Slice 내부 파일 배치 예시

```
features/customer/manage/
├── api/
│   ├── create.ts        # createCustomer fn
│   ├── update.ts        # updateCustomer fn
│   ├── types.ts         # CustomerWriteRequest
│   └── index.ts         # barrel
├── model/
│   ├── use-create.ts    # useCreateCustomer
│   ├── use-update.ts    # useUpdateCustomer
│   └── index.ts
├── ui/
│   ├── customer-form-modal.tsx
│   └── index.ts         # barrel: export { CustomerFormModal }
└── index.ts             # 슬라이스 Public API
```

단순 슬라이스는 segment 내부 파일을 1개만 두고 `index.ts`로 직접 export 가능:

```
features/customer/delete/
├── api/
│   └── index.ts         # export function deleteCustomer ...
├── model/
│   └── index.ts         # export function useDeleteCustomer ...
└── index.ts
```

### `CustomerFormModal` 분리 이유

AGENT2의 `CustomerFormModal`은 `initial?: CustomerDto` prop으로 생성/편집 양쪽 모드를 처리한다. FSD use-case 원칙대로 `customer/create`/`customer/update`로 쪼개면 한쪽이 다른 쪽 mutation을 import해야 해서 같은 레이어 슬라이스 간 import 금지 룰에 위배된다. 그래서 **`features/customer/manage`로 통합** (생성+편집 모달+mutation). 삭제는 UI affordance가 달라(목록의 삭제 버튼) **`features/customer/delete`로 분리**.

## 3. Migration Sequence (4 PRs)

각 PR은 단독으로 `pnpm typecheck && pnpm build` 통과해야 머지.

### PR 1: `shared` 레이어
- `lib/api/*` → `shared/api/`
- `components/ui/*` → `shared/ui/*/`
- `PageResponse<T>` 추출 → `shared/api/types.ts`
- `tsconfig.json` paths에 `@/shared/*` 추가
- Public API (`index.ts`) 작성
- 기존 import 경로 일괄 갱신
- 옛 `lib/api/`, `components/ui/` 폴더 제거

### PR 2: `entities` 레이어
- `features/proposals/api.ts`의 DTO + `fetchProposals` → `entities/proposal/`
- `features/proposals/queries.ts`의 `useProposals` → `entities/proposal/`
- `features/contracts/*` → `entities/contract/` (식별자 유지, import 경로만 변경)
- `features/claims/` 의 조회 부분 (ClaimDto, fetchClaims, useClaims) → `entities/claim/`
- `features/customers/` 의 조회 부분 (CustomerDto, fetchCustomers, useCustomers) → `entities/customer/`
- `features/dashboard/*` → `entities/dashboard/`
- `features/admin/*` → `entities/user/`
- `features/auth/` 의 세션 조회 부분 → `entities/session/`
- `tsconfig.json` paths에 `@/entities/*` 추가
- Public API 작성
- `app/**/page.tsx`의 import 경로 갱신

### PR 3: `features` + `widgets` 레이어
- `features/proposals/ProposalFormModal` + `createProposal` mutation → `features/proposal/create/{api,model,ui}/`
- `features/customers/CustomerFormModal` + create/updateCustomer mutation → `features/customer/manage/{api,model,ui}/`
- `features/customers/` 의 deleteCustomer + useDeleteCustomer → `features/customer/delete/{api,model}/`
- `features/claims/ClaimFormModal` + createClaim + useCreateClaim → `features/claim/create/{api,model,ui}/`
- `features/auth/` 의 login/register/logout 분해 → `features/auth/{login,register,logout}/{api,model[,ui]}/`
- `components/layout/*` → `widgets/{app-shell,sidebar,top-bar,auth-guard}/ui/`
- `tsconfig.json` paths에 `@/features/*`, `@/widgets/*` 추가
- 옛 `features/`, `components/` 폴더 삭제
- 기존 `@/*` alias 제거 또는 정리
- 수동 스모크 테스트 통과

### PR 4: Steiger 도입 + CI 통합
- `pnpm add -D steiger @feature-sliced/steiger-plugin`
- `steiger.config.ts` 작성 (recommended 룰셋)
- `package.json`에 `"lint:fsd": "steiger ./src"` 추가
- `npx steiger ./src` 위반 0건 확인
- CI에 lint:fsd 단계 추가

## 4. Tooling & Config

### 4.1 tsconfig paths

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

PR 1~2 동안 기존 `@/*` alias 호환 유지, PR 3에서 제거.

### 4.2 Steiger config

```ts
// steiger.config.ts
import { defineConfig } from "steiger";
import fsd from "@feature-sliced/steiger-plugin";

export default defineConfig([
  ...fsd.configs.recommended,
]);
```

검사 항목:
- 레이어 단방향 import (`app → widgets → features → entities → shared`)
- 같은 레이어 슬라이스 간 import 금지
- Public API 경유 강제
- 슬라이스 세그먼트 권장 (`ui/`, `model/`, `api/`, `lib/`)

### 4.3 CI

`pnpm typecheck && pnpm lint && pnpm lint:fsd && pnpm build`

ESLint와 Steiger는 독립 도구, 충돌 없음.

## 5. Risks & Validation

### 5.1 Risks

| 리스크 | 대응 |
|---|---|
| 동시 진행 작업과 import 경로 충돌 | PR 짧게 유지, 머지 직후 rebase |
| `proposals → contracts`, `customers → contracts`(AGENT2가 PageResponse import) 같은 cross-feature import 누락 | PR 1에서 `PageResponse` 추출 시 grep으로 사용처 전수 확인 |
| Next.js 서버 컴포넌트의 `entities/session/server.ts` 경로 변경 → SSR 인증 깨짐 | PR 2 머지 전 로그인/로그아웃 수동 검증 |
| Steiger recommended 룰이 너무 엄격 | 룰을 끄지 않고 코드를 룰에 맞춤. PR 1~3에서 구조를 맞춰뒀으므로 0건이 정상 |
| `register/page.tsx`에 실제 API 호출 부재 가능성 | PR 3 작업 시 코드 확인 후 API 없으면 `features/register` 생략, page에서 직접 처리 |
| import 경로 변경이 다른 page에서 누락 | PR 2에서 grep으로 `@/features/contracts` 호출처 전수 갱신 (`app/underwriting/page.tsx`, `app/contracts/page.tsx`) |

### 5.2 Manual smoke test (PR 3 머지 전 필수)

1. 로그인 → 대시보드 진입 (ADMIN/AGENT1/AGENT2 각 역할별)
2. 대시보드 통계 카드 표시 (역할별 분기 렌더링)
3. 설계 관리(`/proposals`) 목록 + 신규 설계 등록 모달 제출
4. 심사 현황(`/underwriting`) 목록 + 상태 필터
5. 고객 관리(`/customers`) 목록 + 신규 등록 + 편집 + 삭제 (AGENT2)
6. 청구 관리(`/claims`) 목록 + 신규 청구 등록 모달
7. 가입 관리(`/admin/users`) 진입
8. 로그아웃

### 5.3 Rollback

각 PR이 독립 머지 → 문제 시 해당 PR revert. PR 1~2 동안은 옛 `@/lib/*`, `@/features/*` 경로가 일부 공존 가능(점진 마이그레이션 안전망), PR 3에서 비로소 제거.

## 6. Out of Scope

- 백엔드 변경 (DTO 명, 엔드포인트 변경 없음)
- 새 기능 추가
- 테스트 코드 추가 (현재 프론트 테스트 부재. 있다면 import 경로만 갱신)
- 라우트 URL 변경
- 내부 식별자 리네이밍 (`PolicyDto`, `usePolicies` 등 백엔드 DTO와 동기화된 이름은 그대로)