# exec-plan: 02-login-responsive

> 로그인 페이지·대시보드의 태블릿/모바일 반응형 처리.
> lg(1024px) 기준으로 분기: 미만은 단일 컬럼 + 사이드바 drawer.

**Status**: Done
**Estimate**: 0.5~1d
**Depends on**: `01-login-mvp`, `02-dashboard` (완료)

---

## 1. 목표

- 로그인 페이지: `< lg`에서 비주얼 패널 숨김 + 폼만 전체 너비
- 대시보드: `< lg`에서 사이드바가 오버레이 drawer로 전환
- 대시보드 요약 카드: `< md`에서 1열 스택

## 2. 스코프

### 포함
- `login/page.tsx` — 모바일 패딩 조정 (`p-4 lg:p-8`)
- `AppShell` — `mobileDrawerOpen` / `desktopCollapsed` 상태 분리
- `Sidebar` — `fixed`(모바일) ↔ `static`(데스크탑) 반응형 CSS
- `TopBar` — 메뉴 버튼: 모바일 drawer 토글 / 데스크탑 collapse 토글
- `dashboard/page.tsx` — stat card 그리드 `grid-cols-1 md:grid-cols-2`

### 비포함
- ❌ 터치 스와이프 제스처
- ❌ 태블릿 전용 레이아웃 (768~1023px 구간은 모바일과 동일하게 처리)

## 3. 검증 (DoD)

- [x] 뷰포트 `< 1024px`: 로그인 폼만 전체 너비, 비주얼 패널 없음
- [x] 뷰포트 `< 1024px`: 대시보드 진입 시 사이드바 숨김
- [x] TopBar 메뉴 버튼 클릭 → 사이드바 drawer 슬라이드 인
- [x] Drawer 외부(오버레이) 클릭 → drawer 닫힘
- [x] 뷰포트 `>= 1024px`: 기존 데스크탑 동작 유지 (256px ↔ 80px 토글)
- [x] 뷰포트 `< 768px`: stat card 1열 스택
- [x] `pnpm typecheck` 통과

## 4. 작업 분해

### RS-1. AppShell 상태 분리
- `sidebarOpen` → `desktopCollapsed: boolean`, `mobileDrawerOpen: boolean`
- `handleMenuClick`: `window.innerWidth < 1024` 기준으로 분기

### RS-2. Sidebar 반응형
- 모바일: `fixed inset-y-0 left-0 z-50 w-[256px]`
- 데스크탑: `lg:static lg:z-auto`
- 열림/닫힘 transform: `translate-x-0` / `-translate-x-full` → `lg:translate-x-0` 로 오버라이드
- 모바일 오버레이: `<div className="fixed inset-0 z-40 bg-black/50 lg:hidden">`

### RS-3. 로그인 패딩 + Dashboard 그리드
- `login/page.tsx`: 우측 패널 `p-4 lg:p-8`
- `dashboard/page.tsx`: `grid-cols-1 md:grid-cols-2`
