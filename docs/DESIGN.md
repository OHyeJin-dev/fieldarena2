# DESIGN.md

> AgentSupport 디자인 시스템의 단일 출처(SSOT).
> Stitch 디자인(Material Design 3 기반)에서 추출 후 한국어 환경·GA(독립대리점) 도메인에 맞춰 보강.
>
> **이 문서를 수정할 때는 반드시 `docs/design-docs/changelog.md`에 변경 사유를 남길 것.**
> LLM이 디자인을 임의로 변경하지 않도록 모든 토큰은 여기서만 정의하고 코드에서는 토큰명만 사용.

---

## 0. 사용 원칙

1. **하드코딩 금지.** 컴포넌트 마크업에 `bg-blue-50`, `text-orange-600` 같은 raw 컬러가 보이면 PR 리젝트. 반드시 시맨틱 토큰 사용.
2. **토큰 = 의미 + 값.** 의미가 같으면 같은 토큰, 값만 같다고 같은 토큰이 아님. (예: `primary`와 `link-color`는 값이 같아도 다른 토큰이어야 한다.)
3. **한국어 우선 (ko-first).** 모든 타이포 토큰의 검증은 한글 텍스트로 한다. 영문은 보조.

---

## 1. 컬러 토큰

### 1.1 Brand (M3 Primary 계열)

| 토큰 | 값 | 용도 |
|---|---|---|
| `primary` | `#002045` | 최상위 강조. 로고, 핵심 헤드라인, 사이드바 텍스트 |
| `primary-container` | `#1A365D` | 주요 CTA 버튼 배경, 사이드바 배경, 성장지표 카드 배경 |
| `on-primary` | `#FFFFFF` | primary/primary-container 위 텍스트 |
| `on-primary-container` | `#86A0CD` | primary-container 위 보조 텍스트 (보조 라벨) |
| `primary-fixed` | `#D6E3FF` | 포커스 링, hover overlay |
| `primary-fixed-dim` | `#ADC7F7` | 사이드바 비활성 텍스트, 진행률 바 채움 |
| `inverse-primary` | `#ADC7F7` | dark 배경 위 강조 텍스트 |

### 1.2 Surface (배경 계층)

| 토큰 | 값 | 용도 |
|---|---|---|
| `surface` | `#F9F9F9` | 페이지 기본 배경 |
| `surface-container-lowest` | `#FFFFFF` | 카드, 모달 배경 |
| `surface-container-low` | `#F3F3F4` | hover 배경, 검색 input |
| `surface-container` | `#EEEEEE` | 미선택 탭 배경 |
| `surface-container-high` | `#E8E8E8` | hover 상태 강조 |
| `surface-container-highest` | `#E2E2E2` | 가장 강한 surface |
| `surface-variant` | `#E2E2E2` | 보조 영역 배경 |
| `on-surface` | `#1A1C1C` | 본문 텍스트 |
| `on-surface-variant` | `#43474E` | 라벨, 보조 텍스트 |
| `outline` | `#74777F` | 아이콘, 분리선 강조 |
| `outline-variant` | `#C4C6CF` | 일반 분리선, input border |

> **참고**: 대시보드 메인 배경은 `#EBF8FF` (style 블록에 하드코딩) — `secondary-container`와 다름. **결정 필요**: 이를 `surface-tinted`로 토큰화할지, primary와 정합되는 톤으로 통일할지.

### 1.3 Semantic (상태) — **🚨 현재 누락, 추가 필요**

CSS에서 `bg-blue-50`, `text-orange-600`, `bg-green-50`로 하드코딩된 상태 컬러를 토큰화. 보험 도메인의 진행 단계(설계→심사→승인/반려→지급)에 맞춰 정의:

| 토큰 | 제안값 | 매핑되는 보험 단계 |
|---|---|---|
| `status-info` | `#1A365D` (primary-container 재사용) | 심사 중, 진행 중 |
| `status-info-container` | `#E0EAF7` | info 배경 |
| `status-warning` | `#C2410C` | 보완 요청, 서류 부족 |
| `status-warning-container` | `#FED7AA` | warning 배경 |
| `status-success` | `#15803D` | 승인 완료, 지급 완료 |
| `status-success-container` | `#DCFCE7` | success 배경 |
| `status-error` | `#BA1A1A` (기존 `error`) | 반려, 환수, 만기 경고 |
| `status-error-container` | `#FFDAD6` (기존 `error-container`) | error 배경 |
| `on-status-*` | `#FFFFFF` 또는 대응 dark | status 위 텍스트 |

### 1.4 외부 브랜드 (소셜 로그인) — 토큰화하지 말 것

`#FEE500` (카카오), `#00C73C` (네이버)는 브랜드 가이드 의무 색상. 토큰화 시 임의 변경 가능성 있어 **컴포넌트 내부에 상수로 고정**.

---

## 2. 타이포그래피

### 2.1 폰트 패밀리 — **🚨 1순위 결정사항**

현재 `Hanken Grotesk`만 정의되어 있음 → **한글 미지원**. 한국어 페이지에서 폰트가 시스템 기본으로 폴백되어 디자인이 무너짐.

**옵션 A (권장): Pretendard 추가**
```css
font-family: 'Pretendard', 'Hanken Grotesk', -apple-system, sans-serif;
```
- 한·영·숫자 모두 일관, 가독성 우수, 무료
- Hanken Grotesk를 Latin fallback으로 유지 (브랜드 영문 표기에 영향 최소)

**옵션 B: Pretendard만 사용**
- 가장 단순. AgentSupport 영문 표기에 Pretendard 적용됨 → 디자인 변경 영향 검토 필요.

**옵션 C: Noto Sans KR + Hanken Grotesk**
- 구글 폰트 통일성. 다만 Pretendard 대비 한글 가독성 다소 떨어짐.

→ **결정 후 본 문서 갱신, `references/fonts-llms.txt`에 폴백 규칙 명시.**

### 2.2 타입 스케일

| 토큰 | size / lineHeight / weight | 용도 |
|---|---|---|
| `headline-lg` | 32 / 1.2 / 700, letter-spacing -0.02em | 페이지 타이틀, 핵심 수치 |
| `headline-md` | 24 / 1.3 / 600, letter-spacing -0.01em | 섹션 헤더, 모달 타이틀 |
| `headline-sm` | 20 / 1.4 / 600 | 카드 제목, 서브섹션 |
| `body-lg` | 18 / 1.6 / 400 | 리드 텍스트 |
| `body-md` | 16 / 1.6 / 400 | 본문 기본 |
| `body-sm` | 14 / 1.5 / 400 | 보조 본문, 캡션 |
| `label-md` | 14 / 1 / 600, letter-spacing 0.02em | 버튼, 탭, 라벨 |
| `label-sm` | 12 / 1 / 500 | 메타데이터, 타임스탬프 |

> **한글 검증 필요**: letter-spacing 음수값은 한글에서 자간이 좁아 보일 수 있음. **headline-lg/md의 letter-spacing을 한글 콘텐츠로 시각 테스트 후 0으로 갈지 결정.**

---

## 3. 간격 (Spacing)

> **⚠️ 이슈**: 현재 spacing에 `container-max: 1200px`가 섞여 있음. 의미 분리 필요.

### 3.1 간격 스케일

| 토큰 | 값 | 용도 가이드 |
|---|---|---|
| `xs` | 4px | 아이콘-텍스트 사이 |
| `base` | 8px | 인접 요소 최소 간격 |
| `sm` | 12px | 컴포넌트 내부 padding |
| `md` | 24px | 카드 padding, 섹션 간격 |
| `lg` | 40px | 큰 섹션 간격 |
| `xl` | 64px | 페이지 상하 여백 |
| `gutter` | 24px | 그리드 컬럼 사이 |

### 3.2 레이아웃 상수 (spacing에서 분리)

| 토큰 | 값 | 용도 |
|---|---|---|
| `container-max` | 1200px | 대시보드 콘텐츠 최대 너비 |
| `sidebar-width` | 256px | 사이드바 펼침 (`w-64`) |
| `sidebar-width-collapsed` | 80px | 사이드바 접힘 |
| `topbar-height` | 64px | 상단 앱바 (현재 명시 안 됨, 박아둘 것) |

---

## 4. 모서리 (Border Radius)

| 토큰 | 값 | 용도 |
|---|---|---|
| `DEFAULT` | 4px | 일반 |
| `lg` | 8px | input, 작은 버튼 |
| `xl` | 12px | 카드, 큰 버튼 |
| `2xl` | 16px | (현재 없음, 성장지표 카드에 사용 — 추가 필요) |
| `full` | 9999px | pill 배지, 아바타 |

---

## 5. 그림자 (Elevation)

현재 단일 그림자만 정의됨:
```css
.soft-depth { box-shadow: 0 4px 20px rgba(26, 54, 93, 0.08); }
.policy-card-hover:hover { box-shadow: 0 10px 30px rgba(26, 54, 93, 0.12); }
```

**제안**: 의도 분리.

| 토큰 | 값 | 용도 |
|---|---|---|
| `shadow-card` | `0 4px 20px rgba(26, 54, 93, 0.08)` | 카드 기본 |
| `shadow-card-hover` | `0 10px 30px rgba(26, 54, 93, 0.12)` | 카드 hover |
| `shadow-modal` | TBD | 모달 |
| `shadow-dropdown` | TBD | 드롭다운/팝오버 |

---

## 6. 다크 모드 — **🚨 결정 필요**

`tailwind.config.darkMode = "class"`가 설정되어 있으나 dark 토큰이 없음.

**옵션 A**: 다크 모드 안 함 → 설정 제거.
**옵션 B**: 다크 모드 함 → 각 토큰의 dark pair 정의 (M3 dark palette 활용).

→ **결정 전까지 어느 시점에든 컴포넌트에 `dark:` prefix 사용 금지.**

---

## 7. 반응형 브레이크포인트

CSS에서 Tailwind 기본값을 그대로 쓰고 있음:

| 브레이크포인트 | min-width | 디바이스 |
|---|---|---|
| `sm` | 640px | 큰 모바일 (검증 시 안 씀) |
| `md` | 768px | 태블릿 |
| `lg` | 1024px | 노트북/PC |
| `xl` | 1280px | 큰 데스크탑 |

**보험설계사 사용 환경**: 외근 시 모바일 + 상담 시 태블릿 + 사무실 PC. **태블릿 케이스가 다른 SaaS 대비 비중이 높음** → 검증 시 768~1024px 구간을 별도 시나리오로 분리할 것.

---

## 8. 검증 (Verification)

이 문서의 변경이 코드에 반영되었는지 확인하는 방법:

1. **`tokens/tailwind.config.js`가 본 문서의 표를 그대로 옮긴 것인지** 코드 리뷰 체크리스트에 포함.
2. **컴포넌트 PR에 `bg-#…`, `text-#…`, `[16px]` 같은 raw 값이 있는지** ESLint/Stylelint로 차단.
3. **`docs/design-docs/screens/`에 저장된 스크린샷과 픽셀 비교** Playwright 시각 회귀 테스트.

---

## 9. 미해결 결정사항 (Tech Debt 후보)

`docs/exec-plans/tech-debt-tracker.md`에 자동 등록할 것:

- [ ] 한글 폰트 선택 (1순위)
- [ ] 다크 모드 여부
- [ ] 시맨틱 상태 토큰 값 확정
- [ ] 대시보드 배경 `#EBF8FF` 토큰화 여부
- [ ] letter-spacing 음수값의 한글 영향 검증
- [ ] 그림자 토큰 추가 정의 (modal, dropdown)
