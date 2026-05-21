# CSR → SSR Migration (Data Pages)

- **Date**: 2026-05-21
- **Author**: <hj8279@coocon.net>
- **Status**: Draft

## Background

`fieldarena2` 의 모든 11개 `app/` 페이지가 `"use client"` 로 선언되어 CSR 로 렌더된다.
인증 게이트는 [proxy.ts](../../../frontend/src/proxy.ts) 가 SSR 에서 차단하지만, 페이지 *본문*은 여전히 빈 골격 → JS hydration → API 호출 → 데이터 도착 순서로 보여진다.

결과: (1) 첫 화면이 비어있다가 로드 표시 후 채워지는 사용자 경험, (2) 페이지 HTML 에 데이터가 포함되지 않아 SEO/share-preview 시 무의미, (3) 모바일/저속망에서 TTFB 이후 추가 RTT 1회 소요.

이번 작업은 데이터 메인 7개 페이지(`dashboard`, `customers`, `claims`, `contracts`, `proposals`, `underwriting`, `admin/users`)에 한해 **Server Component 진입점 + Client island + TanStack Query hydration** 패턴을 도입해 첫 응답 HTML 에 초기 데이터를 포함시킨다.

## Goals

- 7개 데이터 페이지 진입 시 첫 HTML 에 초기 페이지 데이터가 직접 포함되도록 만든다.
- 클라이언트 hydration 후 *추가 네트워크 요청 없이* 즉시 인터랙티브 상태로 진입.
- 페이지네이션·필터·모달·mutation 등 기존 클라이언트 인터랙션 동작 그대로 유지.
- 모든 entity 의 query key 를 factory 패턴(`customerKeys.list(query)` 등)으로 정규화해 server prefetch / client useQuery 가 정확히 같은 key 를 사용하도록 한다.
- 인증 만료 race 시 server fetch 401 → `/login` 으로 redirect.

## Non-Goals

- `login` / `register` / `pending` / `_auth-guard` / `providers` 페이지의 SSR 전환 (데이터 fetch 없음, 인터랙션 위주 — CSR 유지가 적절).
- SSG 도입 (모든 데이터 페이지가 인증 바운드이므로 부적합).
- Server Actions 도입 (mutation 은 기존 RQ mutation 훅 유지).
- TanStack Query 제거 / RSC 전면 리팩터링.
- 컴포넌트 / FSD 레이어 구조 변경 (entities/features/widgets 그대로).

## Approach

### Architecture

```
app/{page}/page.tsx              ← Server Component (async, no "use client")
   │
   ├─ QueryClient 생성 (request scope)
   ├─ serverFetch() 로 초기 데이터 prefetch (cookies forward)
   ├─ dehydrate(queryClient)
   └─ <HydrationBoundary state={...}>
        <{Page}PageClient />     ← Client island ("use client", 기존 page.tsx 본문 이동)
          └─ useXxx() hooks → hydrated cache 에서 즉시 데이터 반환
```

### Branch / PR strategy

- Base: `master`
- 단일 PR `feat/csr-to-ssr-data-pages` 로 7개 페이지 변환을 묶어서 제출.
- 변환 자체는 페이지마다 독립이라 PR 안에서 commit 을 페이지 단위로 분리.
- 공통 인프라(serverFetch, query key factory) 커밋이 가장 먼저, 페이지 변환은 그 위에 차곡차곡.

---

## File Changes

### Create

- `frontend/src/shared/api/server-fetch.ts` — 서버 전용 fetch 헬퍼 (cookies forward + BACKEND_URL + 401 redirect)
- `frontend/src/app/dashboard/_client.tsx` — 기존 `dashboard/page.tsx` 본문 이동
- `frontend/src/app/customers/_client.tsx`
- `frontend/src/app/claims/_client.tsx`
- `frontend/src/app/contracts/_client.tsx`
- `frontend/src/app/proposals/_client.tsx`
- `frontend/src/app/underwriting/_client.tsx`
- `frontend/src/app/admin/users/_client.tsx`

### Modify

- `frontend/src/shared/api/index.ts` — `server-fetch` 재노출 추가 (서버 전용 import path 명확화)
- 각 entity 의 `api/index.ts` — query key factory 추가 (`customerKeys`, `claimKeys`, …) + `fetch{Xxx}` 함수의 query key 출처 통일
- 각 entity 의 `model/index.ts` — `useXxx` 훅이 query key factory 를 사용하도록 변경
- 7개 `app/{page}/page.tsx` — Server Component 로 재작성 (prefetch + HydrationBoundary)

---

## Component Specifications

### `shared/api/server-fetch.ts` (신규)

```ts
import { cookies } from "next/headers";

const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

export async function serverFetch<T>(path: string): Promise<T> {
  const cookieStore = await cookies();
  const cookieHeader = cookieStore
    .getAll()
    .map((c) => `${c.name}=${c.value}`)
    .join("; ");

  const res = await fetch(`${BACKEND_URL}${path}`, {
    headers: { cookie: cookieHeader },
    cache: "no-store",
  });

  if (res.status === 401) {
    const { redirect } = await import("next/navigation");
    redirect("/login");
  }
  if (!res.ok) throw new Error(`Server fetch ${path} failed: ${res.status}`);

  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}
```

핵심 결정:

- `cache: "no-store"` — 인증 바운드 데이터의 캐시 누출 방지.
- `redirect()` 는 동적 import — Next.js 가 server-only API 임을 정확히 알도록.
- 어떤 query string 도 인코딩하지 않음 (호출자가 path 에 미리 인코딩).

### Query key factory 패턴

기존 `entities/customer/model/index.ts:6`:

```ts
queryKey: ["customers", query],
```

변경 후 `entities/customer/api/index.ts` 에 추가:

```ts
export const customerKeys = {
  all: ["customers"] as const,
  list: (query: CustomerQuery = {}) => ["customers", query] as const,
};
```

`entities/customer/model/index.ts`:

```ts
import { useQuery } from "@tanstack/react-query";
import { customerKeys, fetchCustomers, type CustomerQuery } from "../api";

export function useCustomers(query: CustomerQuery = {}) {
  return useQuery({
    queryKey: customerKeys.list(query),
    queryFn: () => fetchCustomers(query),
  });
}
```

`entities/customer/index.ts` 의 re-export 에 `customerKeys` 추가.

같은 패턴을 `claim`, `contract`, `proposal`, `dashboard`, `health-analysis`, `session`(useMe), `admin/user`(useAdminUsers) 에 적용.

### Server Component 패턴

대표 예 `app/customers/page.tsx`:

```tsx
import { QueryClient, dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { serverFetch } from "@/shared/api/server-fetch";
import { customerKeys } from "@/entities/customer";
import type { PageResponse } from "@/shared/api";
import type { CustomerDto } from "@/entities/customer";
import CustomersPageClient from "./_client";

export default async function CustomersPage() {
  const queryClient = new QueryClient();

  await queryClient.prefetchQuery({
    queryKey: customerKeys.list({ page: 0, size: 20 }),
    queryFn: () =>
      serverFetch<PageResponse<CustomerDto>>("/api/customers?page=0&size=20"),
  });

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <CustomersPageClient />
    </HydrationBoundary>
  );
}
```

`app/customers/_client.tsx` — 기존 `page.tsx` 본문이 그대로 이동, default export 는 `CustomersPageClient`.

### 페이지별 prefetch 매트릭스

| 페이지 | prefetch query (key + path) |
|---|---|
| `dashboard` | `dashboardKeys.summary()` → `/api/dashboard/summary`<br>`sessionKeys.me()` → `/api/auth/me`<br>`healthAnalysisKeys.summary()` → `/api/health-analysis/summary`<br>`healthAnalysisKeys.recent(5)` → `/api/health-analysis/recent?limit=5` |
| `customers` | `customerKeys.list({page:0, size:20})` → `/api/customers?page=0&size=20` |
| `claims` | `claimKeys.list({page:0, size:20})` → `/api/claims?page=0&size=20` |
| `contracts` | `contractKeys.list({page:0, size:20})` → `/api/contracts?page=0&size=20` |
| `proposals` | `proposalKeys.list({page:0, size:20})` → `/api/proposals?page=0&size=20` |
| `underwriting` | 현재 페이지가 사용하는 RQ 훅의 query key 그대로 + `searchParams.analysisId` 있으면 detail 도 함께 (plan 단계에서 확정) |
| `admin/users` | 현재 페이지가 사용하는 admin user list 훅의 query key 그대로 (plan 단계에서 확정) |

페이지가 `searchParams` 를 받는 경우 (`underwriting`) Server Component signature:

```tsx
export default async function UnderwritingPage({
  searchParams,
}: {
  searchParams: Promise<{ analysisId?: string }>;
}) {
  const { analysisId } = await searchParams;
  // ...
}
```

---

## Data Flow

1. 사용자가 `/customers` 요청.
2. Next.js middleware (`proxy.ts`) 가 `AGENT_SESSION` 쿠키 확인 후 `/api/auth/me` 검증 — 200 이면 통과.
3. `app/customers/page.tsx` Server Component 실행:
   - `QueryClient` 생성 (request 스코프 — 다른 요청과 격리)
   - `serverFetch("/api/customers?page=0&size=20")` 호출 → `next/headers` 의 `cookies()` 가 현재 요청 쿠키 추출 → 백엔드 호출
   - 백엔드는 같은 도메인 (localhost dev) 이거나 `BACKEND_URL` 로 외부
   - `dehydrate(queryClient)` 가 RQ cache 를 직렬화 가능한 객체로 변환
4. `HydrationBoundary` 가 dehydrated state 와 client `CustomersPageClient` 를 함께 HTML 로 송출.
5. 브라우저:
   - HTML 에 `<script id="__NEXT_DATA__">` 와 RQ dehydrated state 포함
   - JS hydration 후 `QueryClientProvider`(이미 [providers.tsx](../../../frontend/src/app/providers.tsx) 에 존재) 가 hydrated state 를 흡수
   - `useCustomers({page:0, size:20})` 호출 시 RQ 가 같은 key 의 cache 발견 → 네트워크 0회, 즉시 데이터 반환

### 페이지네이션·필터링

- 사용자가 page 2 로 이동 → `useCustomers({page:1, size:20})` 호출 → 다른 query key → cache miss → 정상 client fetch → `/api/customers?page=1&size=20` 요청 (next.config.ts rewrites 통해 백엔드로).
- 첫 페이지로 돌아오면 hydrated cache 가 stale 일 수도 있지만 RQ default staleTime=0 이라 background refetch 발생 (UX 영향 없음).

### Mutation

- `CustomerFormModal` 등 client island 에서 RQ mutation 으로 POST/PATCH → `onSuccess` 에서 `queryClient.invalidateQueries({queryKey: customerKeys.all})` → 자동 refetch. 기존 동작 그대로.

---

## Error Handling

- **Server fetch 401 (세션 만료 race)**: `serverFetch` 가 `redirect("/login")` — 사용자는 그대로 로그인 화면.
- **Server fetch 5xx**: `throw new Error(...)` → Next.js `app/error.tsx` 가 잡음. 현재 프로젝트에 `error.tsx` 없으면 기본 에러 UI 표시 — 추가는 스코프 외.
- **Client fetch 에러**: 기존 [shared/api/csrf.ts](../../../frontend/src/shared/api/csrf.ts) 의 `apiFetch` 동작 그대로 (401 → window redirect, 그 외 ApiError throw → RQ `isError`).
- **Hydration mismatch**: query key 가 server / client 에서 다르면 RQ 가 hydrated entry 를 무시하고 다시 fetch (성능 손해뿐 깨지지 않음). Factory 패턴이 이를 방지.

---

## Testing Strategy

| 영역 | 방법 |
|---|---|
| 첫 HTML 에 데이터 포함 | `curl -b "AGENT_SESSION=<value>" http://localhost:3000/customers \| grep "감사대상고객"` — 데이터 직접 포함 확인 |
| Hydration 후 추가 fetch 0회 | DevTools Network → 페이지 진입 시 `/api/customers` GET 미발생 |
| 페이지네이션 정상 | page 2 이동 시 `/api/customers?page=1&size=20` GET 1회 |
| Mutation 후 invalidation | 고객 생성 → 목록에 즉시 반영 (수동 새로고침 없음) |
| 401 race (만료된 쿠키) | 쿠키 만료시킨 후 `/customers` 직접 진입 → 302 to `/login` (proxy.ts 가 1차 차단, server-fetch가 2차) |
| AGENT2 role 분기 | AGENT2 로그인 후 `/dashboard` 진입 시 서버 렌더된 HTML 에 "본인 고객" 카드가 포함되어야 함 (AGENT1 카드 아님) |
| Build 무결성 | `pnpm typecheck && pnpm lint && pnpm lint:fsd && pnpm build` 전부 green |

수동 검증 매트릭스는 plan 단계에서 자세히 풀 예정.

---

## Risks

1. **QueryClient request scope**: 모듈 스코프 인스턴스를 만들면 서로 다른 사용자의 데이터가 cache 에서 누출됨. 현재 [providers.tsx:7](../../../frontend/src/app/providers.tsx) 가 `useState(() => new QueryClient())` 로 request 스코프이며, **Server Component 안에서도 `new QueryClient()` 를 매 요청마다 함수 내부에서 생성**해야 함. spec 의 모든 예시는 이 패턴 준수.

2. **Date 직렬화**: dehydrate / hydrate 는 JSON 직렬화. 현재 모든 DTO 의 timestamp 가 string ISO 라 안전. 만약 backend 가 Date 객체나 BigInt 를 보내면 hydration 실패 — 현 상태에선 해당 없음.

3. **`useMe` SSR 처리**: `_auth-guard` 가 client 에서 `useMe()` 결과로 role-based redirect 수행. SSR 로 옮기면 dashboard 진입 시 me 가 prefetched → guard 의 useEffect 가 동기적으로 통과. 회귀 없음.

4. **next.config.ts rewrites vs serverFetch 차이**: client `apiFetch` 는 `/api/...` 상대 경로 → Next.js rewrites → 백엔드. server fetch 는 `BACKEND_URL` 절대 경로 → 백엔드 직접. 결과는 동일 (backend 가 same response).

5. **번들 사이즈**: Server Component 코드는 클라이언트로 전송 안 되지만, page.tsx 가 import 하는 `_client.tsx` 는 그대로 client bundle 에 포함. 따라서 변환 전후 client bundle 크기 변화 없음 (prefetch 코드만 server-only로 분리됨).

---

## Open Questions

없음 (브레인스토밍에서 모두 해소).

## References

- 관련 스펙: [docs/superpowers/specs/2026-05-20-security-hardening-design.md](2026-05-20-security-hardening-design.md) — proxy.ts SSR 인증 게이트
- 기존 인프라:
  - [frontend/src/app/providers.tsx](../../../frontend/src/app/providers.tsx) — QueryClientProvider (request scope OK)
  - [frontend/next.config.ts](../../../frontend/next.config.ts) — `BACKEND_URL` rewrites
  - [frontend/src/shared/api/csrf.ts](../../../frontend/src/shared/api/csrf.ts) — 기존 client `apiFetch`
