# Customer Phone Format & Required Fields Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 고객 등록 모달의 필수 필드를 (이름/휴대폰/생년월일/성별)로 명확히 하고, 휴대폰번호 입력은 숫자만 받되 화면에 자동 포맷팅(`010-XXXX-XXXX`)으로 표시한다. 동일한 포맷팅을 `/register` 페이지에도 적용한다.

**Architecture:** 작은 frontend-only 변경. `shared/lib/phone-format` 슬라이스에 `formatPhone(raw)` 유틸을 만들고, react-hook-form의 `register()` 옵션 `onChange`에서 입력값을 가공해 자동 포맷팅을 구현. zod schema에서 `birthDate`/`gender`를 required로 변경. Backend 변경 없음 (기존 regex 검증 그대로).

**Tech Stack:** Next.js 16 (App Router), React 19, react-hook-form + zod, Tailwind v4, pnpm.

**Reference Spec:** `docs/superpowers/specs/2026-05-21-customer-phone-format-design.md`

**Branch:** `chore/customer-phone-format` (master에서 분기. 다른 진행 중 작업과 독립).

---

## File Structure

### 신규

```
frontend/src/shared/lib/phone-format/
└── index.ts                          # formatPhone(raw: string): string
```

### 수정

```
frontend/src/features/customer/manage/ui/index.tsx    # schema + phone onChange + payload
frontend/src/app/register/page.tsx                    # schema phone regex + phone onChange
```

---

## Conventions

- **Working dir**: `d:\fieldarena2`. 명령은 `cd frontend` 기준.
- **Branch**: 시작 전 master에서 `git checkout -b chore/customer-phone-format`.
- **Verification**: `cd frontend; pnpm typecheck; pnpm lint; pnpm lint:fsd; pnpm build`
- **Pre-commit hook**: Husky가 `pnpm lint:fsd` 자동 실행.
- **Backend**: 변경 없음.

---

## Task 1: shared/lib/phone-format 유틸 생성

**Files:**
- Create: `frontend/src/shared/lib/phone-format/index.ts`

- [ ] **Step 1: 폴더 생성**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/shared/lib/phone-format -Force
```

- [ ] **Step 2: index.ts 작성**

```ts
// frontend/src/shared/lib/phone-format/index.ts

/**
 * 입력 문자열에서 숫자만 추출 후 휴대폰번호 형식(010-XXXX-XXXX)으로 변환.
 * 부분 입력(짧은 문자열)도 길이에 맞게 dash를 삽입한다.
 *
 * 예시:
 *  - "01012345678" → "010-1234-5678"
 *  - "0101234" → "010-1234"
 *  - "010" → "010"
 *  - "" → ""
 *  - "010-12abc34-5678" → "010-1234-5678" (숫자만 추출)
 *  - "010123456789999" → "010-1234-5678" (11자리 초과 절단)
 */
export function formatPhone(raw: string): string {
  const digits = raw.replace(/\D/g, "").slice(0, 11);
  if (digits.length <= 3) return digits;
  if (digits.length <= 7) return `${digits.slice(0, 3)}-${digits.slice(3)}`;
  return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`;
}
```

- [ ] **Step 3: 타입체크 확인**

Run: `cd frontend; pnpm typecheck`
Expected: 통과.

- [ ] **Step 4: 사용 가능 여부 grep**

Run: 새 모듈이 import 가능한지 확인:
```powershell
echo "import { formatPhone } from '@/shared/lib/phone-format';" > /tmp/test-import.ts 2>/dev/null
```
(실제 import는 다음 task에서 테스트.)

- [ ] **Step 5: 커밋 (Task 2와 함께 묶을 수 있지만 단독 commit으로 일반 유틸 분리)**

```bash
git add frontend/src/shared/lib/phone-format/index.ts
git commit -m "feat(shared): add formatPhone utility for 010-XXXX-XXXX format"
```

---

## Task 2: CustomerFormModal — schema + phone onChange + payload

**Files:**
- Modify: `frontend/src/features/customer/manage/ui/index.tsx`

- [ ] **Step 1: import 추가 + schema 변경**

기존 `frontend/src/features/customer/manage/ui/index.tsx` 의 import 블록과 schema를 다음과 같이 수정:

Imports (기존 imports 위에 추가):
```ts
import { formatPhone } from "@/shared/lib/phone-format";
```

Schema 교체:
```ts
const schema = z.object({
  name: z.string().min(1, "이름을 입력하세요"),
  phone: z.string().regex(/^010-\d{3,4}-\d{4}$/, "올바른 형식: 010-0000-0000"),
  birthDate: z.string().min(1, "생년월일을 입력하세요"),
  gender: z.enum(["M", "F"], { message: "성별을 선택하세요" }),
  email: z.string().email("올바른 이메일").or(z.literal("")).optional(),
  address: z.string().optional(),
  memo: z.string().optional(),
});
```

- [ ] **Step 2: defaultValues 수정 — gender 빈 문자열 제거**

기존 `defaultValues`에서 `gender: (initial?.gender as "M" | "F" | "") ?? "",` 를 다음으로 교체:

```ts
defaultValues: {
  name: initial?.name ?? "",
  phone: initial?.phone ?? "",
  birthDate: initial?.birthDate ?? "",
  gender: (initial?.gender as "M" | "F" | undefined) ?? undefined,
  email: initial?.email ?? "",
  address: initial?.address ?? "",
  memo: initial?.memo ?? "",
},
```

`gender`의 type을 `"M" | "F" | undefined`로 바꿔 빈 문자열 대신 undefined를 default로 둠. select element의 default option `value=""`가 폼 상태에서는 빈 문자열로 다뤄지지만, zod enum이 `""`을 reject하므로 사용자가 선택을 안 하면 검증 실패.

- [ ] **Step 3: onSubmit payload — gender / birthDate || null 제거**

기존 `onSubmit` 함수의 payload를 다음으로 교체:

```ts
function onSubmit(values: FormValues) {
  const payload = {
    name: values.name,
    phone: values.phone,
    birthDate: values.birthDate,
    gender: values.gender,
    email: values.email || null,
    address: values.address || null,
    memo: values.memo || null,
  };
  if (isEdit && initial) {
    update.mutate({ id: initial.id, req: payload }, { onSuccess: onClose });
  } else {
    create.mutate(payload, { onSuccess: onClose });
  }
}
```

- [ ] **Step 4: phone input에 onChange 적용**

기존 phone TextField 부분:
```tsx
<TextField
  label="휴대폰번호"
  placeholder="010-0000-0000"
  error={errors.phone?.message}
  {...register("phone")}
/>
```

다음으로 교체:
```tsx
<TextField
  label="휴대폰번호"
  placeholder="010-0000-0000"
  error={errors.phone?.message}
  {...register("phone", {
    onChange: (e) => {
      e.target.value = formatPhone(e.target.value);
    },
  })}
/>
```

- [ ] **Step 5: 타입체크 + lint + build**

Run:
```powershell
cd frontend
pnpm typecheck
pnpm lint
pnpm lint:fsd
pnpm build
```
Expected: 모두 통과.

- [ ] **Step 6: 커밋**

```bash
git add frontend/src/features/customer/manage/ui/index.tsx
git commit -m "feat(customer): require birthDate/gender and auto-format phone input"
```

---

## Task 3: /register page — schema + phone onChange

**Files:**
- Modify: `frontend/src/app/register/page.tsx`

- [ ] **Step 1: import 추가**

기존 imports 위에 추가:
```ts
import { formatPhone } from "@/shared/lib/phone-format";
```

- [ ] **Step 2: schema phone regex 강화**

기존:
```ts
phone: z.string().min(1, "연락처를 입력해주세요"),
```

다음으로 교체:
```ts
phone: z
  .string()
  .min(1, "연락처를 입력해주세요")
  .regex(/^010-\d{3,4}-\d{4}$/, "올바른 형식: 010-0000-0000"),
```

- [ ] **Step 3: phone 필드만 onChange 적용 — fields.map 안에서 분기**

기존 fields.map 블록:
```tsx
{fields.map((f) => (
  <div key={f.name} className="flex flex-col gap-1.5">
    <label className="text-sm font-semibold text-on-surface-variant px-1">
      {f.label}
    </label>
    <input
      type={f.type}
      placeholder={f.placeholder}
      autoComplete={f.autoComplete}
      className={[INPUT_CLASS, errors[f.name] ? "border-status-error" : ""].join(" ")}
      {...register(f.name)}
    />
    {errors[f.name] && (
      <p className="text-xs text-status-error px-1">{errors[f.name]?.message}</p>
    )}
  </div>
))}
```

다음으로 교체 (phone 필드만 onChange 추가하는 분기):

```tsx
{fields.map((f) => {
  const registerOptions =
    f.name === "phone"
      ? {
          onChange: (e: React.ChangeEvent<HTMLInputElement>) => {
            e.target.value = formatPhone(e.target.value);
          },
        }
      : undefined;
  return (
    <div key={f.name} className="flex flex-col gap-1.5">
      <label className="text-sm font-semibold text-on-surface-variant px-1">
        {f.label}
      </label>
      <input
        type={f.type}
        placeholder={f.placeholder}
        autoComplete={f.autoComplete}
        className={[INPUT_CLASS, errors[f.name] ? "border-status-error" : ""].join(" ")}
        {...register(f.name, registerOptions)}
      />
      {errors[f.name] && (
        <p className="text-xs text-status-error px-1">{errors[f.name]?.message}</p>
      )}
    </div>
  );
})}
```

추가 import 필요: `React` 타입 사용을 위해 파일 상단에 이미 있을 수 있음. 없으면 `import type * as React from "react";` 추가.

- [ ] **Step 4: 타입체크 + lint + build**

Run:
```powershell
cd frontend
pnpm typecheck
pnpm lint
pnpm lint:fsd
pnpm build
```
Expected: 모두 통과.

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/app/register/page.tsx
git commit -m "feat(register): auto-format phone input and enforce 010-XXXX-XXXX regex"
```

---

## Task 4: 수동 스모크 테스트

**Files:** N/A

- [ ] **Step 1: 백엔드 + 프론트엔드 실행**

별도 터미널:
- `cd backend; ./gradlew.bat bootRun`
- `cd frontend; pnpm dev`

- [ ] **Step 2: CustomerFormModal 시나리오**

1. AGENT2 또는 ADMIN으로 로그인 → `/customers` 메뉴
2. "+ 새 고객 등록" 클릭 → 모달 오픈
3. 휴대폰번호 칸에 `01012345678` 입력 → 화면에 **`010-1234-5678`** 표시
4. 같은 칸 전체 선택 → `010-1234-5678` 그대로 paste → **`010-1234-5678`** 유지
5. 칸 끝에 `99999` 추가 입력 → **11자리만 유지** (변화 없음)
6. 칸을 비우고 `abc010def1234ghi5678` 입력 → 숫자만 추출되어 **`010-1234-5678`** 표시
7. backspace로 마지막 4자리 삭제 → 자연스럽게 dash가 사라짐 (`010-1234`)
8. 이름만 채우고 제출 → 휴대폰/생년월일/성별 에러 메시지
9. 휴대폰을 `010-1234-` 까지만 입력 후 제출 → "올바른 형식: 010-0000-0000" 에러
10. 생년월일 비우고 제출 → "생년월일을 입력하세요" 에러
11. 성별 "선택" 상태로 두고 제출 → "성별을 선택하세요" 에러
12. 이름 + 휴대폰 + 생년월일 + 성별 + (선택 필드 비움) 채우고 제출 → **등록 성공**
13. 모든 필드 채우고 제출 → 등록 성공
14. 기존 고객 "수정" 클릭 → 모달에 기존 값 채워짐, 휴대폰 포맷 그대로 유지

- [ ] **Step 3: /register 시나리오**

1. 로그아웃 또는 시크릿 창으로 `/register` 진입
2. 연락처 칸에 `01012345678` → **`010-1234-5678`** 표시
3. 다른 필드 채우고 연락처 `010-1234-` 까지만 입력 후 제출 → "올바른 형식…" 에러
4. 연락처 `010-1234-5678` 완성 후 제출 → 회원가입 진행

각 시나리오 통과 시 다음 task. 실패 시 해당 코드로 돌아가 수정.

---

## Task 5: Push + PR + 머지

**Files:** N/A

- [ ] **Step 1: 커밋 검토**

Run: `git log master..HEAD --oneline`
Expected:
- Task 1: feat(shared): add formatPhone utility for 010-XXXX-XXXX format
- Task 2: feat(customer): require birthDate/gender and auto-format phone input
- Task 3: feat(register): auto-format phone input and enforce 010-XXXX-XXXX regex

총 3 commits.

- [ ] **Step 2: 사용자에게 push 승인 요청**

(memory: feedback_git_push_confirm)

- [ ] **Step 3: push**

```bash
git push -u origin chore/customer-phone-format
```

- [ ] **Step 4: PR 생성**

```bash
gh pr create --base master --head chore/customer-phone-format --title "feat(customer): require birthDate/gender and auto-format phone in customer + register forms" --body "$(cat <<'EOF'
## Summary

고객 등록 모달의 필수 필드 명확화 + 휴대폰 자동 포맷팅.

## Spec / Plan

- Spec: \`docs/superpowers/specs/2026-05-21-customer-phone-format-design.md\`
- Plan: \`docs/superpowers/plans/2026-05-21-customer-phone-format.md\`

## Changes

- \`shared/lib/phone-format\`: \`formatPhone(raw)\` 유틸 신설 (숫자 추출 + 010-XXXX-XXXX 포맷)
- \`features/customer/manage\` (CustomerFormModal):
  - 필수 필드: name / phone / birthDate / gender
  - 선택 필드: email / address / memo (기존 유지)
  - 휴대폰번호 입력 자동 포맷팅
  - gender select에 zod enum 검증
- \`app/register/page.tsx\`: 휴대폰번호 자동 포맷팅 + zod regex 강화

## Backend

변경 없음. 기존 정규식 검증(\`^010-\\d{3,4}-\\d{4}\$\`) 그대로 사용.

## Test plan

- [x] Frontend: typecheck / lint / lint:fsd / build 통과
- [x] 수동 스모크: CustomerFormModal 모든 필수/선택 케이스, 자동 포맷팅 동작, /register 동일 동작

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 5: CI 통과 + 머지**

Run: `gh pr view --json statusCheckRollup --jq '.statusCheckRollup[] | {name, conclusion}'`

CI `ci` 체크가 SUCCESS 되면 사용자에게 머지 방식 확인 (기본 merge commit).

```bash
gh pr merge <PR번호> --merge
```

- [ ] **Step 6: 브랜치 정리**

```bash
git checkout master
git pull --quiet
git branch -d chore/customer-phone-format
git push origin --delete chore/customer-phone-format
```

---

# 완료 후 점검

- [ ] master에 변경 반영
- [ ] CI (Steiger 포함) 통과
- [ ] Vercel 자동 배포 성공
- [ ] 운영 환경 (또는 로컬)에서 CustomerFormModal + /register phone 입력 한 번 시연

## Out of Scope (별도 작업)

- backend `customers.birth_date` NOT NULL 변경 (프론트만 강제)
- 다른 페이지 phone 필드 (claim 등록 등 — 존재하면 별도 작업)
- 국가번호 처리 (+82)
- 휴대폰 이외 전화번호 (02-, 031- 등)
- 자동 포맷팅 단위 테스트 (수동 검증으로 충분)
- backend gender enum 검증 강화
