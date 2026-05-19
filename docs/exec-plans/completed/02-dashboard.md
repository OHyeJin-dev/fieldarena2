# exec-plan: 02-dashboard

> 대시보드 본격 구현. 사이드바 + 앱바 앱 셸 레이아웃을 확립하고,
> 요약 카드 3종(이번 달 계약 건수·처리 대기 건수·최근 계약 목록)을 placeholder 데이터로 표시한다.
> 실제 API 데이터 연동은 03-contracts.md 이후로 미룬다.

**Status**: Completed
**Estimate**: 1~1.5d
**Depends on**: `01-login-mvp` (완료), `docs/DESIGN.md`

---

## 3. 검증 (DoD)

- [x] `/dashboard` 진입 시 사이드바 + 앱바 + 메인 영역 레이아웃 확인
- [x] 사이드바 토글 버튼 클릭 → 256px ↔ 80px 전환
- [x] 앱바에 사용자명 표시
- [x] 로그아웃 버튼 클릭 → `/login`으로 이동
- [x] 요약 카드 2종 (이번 달 계약 건수·처리 대기 건수) placeholder 값 표시
- [x] 최근 계약 목록 테이블 5행 placeholder 표시
- [x] `pnpm typecheck` 통과
- [x] DESIGN.md 토큰만 사용

---

## 8. 회고

### 구현 결정사항
- Route Group `(dashboard)` 대신 `dashboard/layout.tsx` 사용 — 기존 `dashboard/page.tsx` 파일이 존재해 Route Group 사용 시 URL 충돌 발생. 기능적으로 동일하며 파일 수 적음.
- `AppShell` Client Component에서 사이드바 토글 상태 일원 관리 → Sidebar·TopBar에 prop drilling. Context 불필요 (이 plan 범위).
- `shadow-card` Tailwind v4 유틸리티: `--shadow-card` 토큰 정상 생성 확인.
- `rounded-2xl`: `--radius-2xl: 16px` 정상 적용 확인.

### 다음 plan에서 결정할 항목
- 계약 현황 API 설계 (`docs/API.md` 기준, 페이지네이션·필터 포함)
- 사이드바 반응형 처리 (모바일 drawer 오버레이 방식)
