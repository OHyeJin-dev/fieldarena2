# Customer Phone Format & Required Fields Design

- **Date**: 2026-05-21
- **Scope**: 고객 등록/수정 모달의 필수 필드 정책과 휴대폰번호 입력 UX 개선. 회원가입 페이지에도 동일 포맷팅 적용.
- **Goal**: 필수 입력 항목을 (이름/휴대폰번호/생년월일)로 명확히 하고, 휴대폰번호는 숫자만 입력 받되 화면에 자동 포맷(`010-XXXX-XXXX`)으로 표시.

---

## 1. 변경 범위

| 영역 | 변경 |
|---|---|
| `CustomerFormModal` zod schema | `birthDate` optional → **required** ("생년월일을 입력하세요"), `gender` optional → **required** ("성별을 선택하세요") |
| `CustomerFormModal` phone 입력 | 숫자만 입력, 자동 포맷팅 (`010-XXXX-XXXX`) |
| `/register` page phone 입력 | 동일한 자동 포맷팅 |
| 신규 `shared/lib/phone-format` | `formatPhone(value)` 공용 유틸 |
| `CustomerFormModal` 비필수 필드 | email / address / memo — 기존 optional 유지 |

## 2. 동작 정의

### 2.1 휴대폰번호 입력 동작

- 사용자가 input에 입력하는 모든 값이 `formatPhone()`을 거침
- 숫자가 아닌 문자는 자동 제거됨
- 자동 dash 삽입: 길이별 변환
  - 0~3자리: `XXX`
  - 4~7자리: `XXX-XXXX` (실제로는 `XXX-X`, `XXX-XX`, `XXX-XXX`, `XXX-XXXX` 단계별)
  - 8~11자리: `XXX-XXXX-XXXX`
- 11자리 초과 입력은 무시 (자동 절단)
- 붙여넣기: `010-1234-5678` 또는 `01012345678` 둘 다 정상 처리
- backspace로 dash 위치 삭제 시 dash가 자동으로 사라지면서 자연스러운 삭제 (formatPhone이 dash 위치를 다시 계산하므로 사용자가 별도 처리 불필요)
- 빈 input은 빈 문자열로 유지

### 2.2 필수 필드 정책 (CustomerFormModal)

- **필수**: `name`, `phone`, `birthDate`, `gender`
- **선택**: `email`, `address`, `memo`
- 필수 미입력 시 input 아래에 에러 메시지 표시 (`text-status-error`)
- gender select의 "선택" placeholder 옵션은 default 상태로 유지. 사용자가 남/여를 명시적으로 골라야 통과

### 2.3 검증 동작

- phone: zod regex `^010-\d{3,4}-\d{4}$` (기존 그대로)
  - formatPhone이 항상 이 형식을 만들거나 부분 문자열만 만들므로, 미완료 입력 시 검증 실패 메시지 표시
- birthDate: zod `z.string().min(1, "생년월일을 입력하세요")` (`type="date"` input이므로 brouser native 검증 + zod check)

## 3. 코드 구조

### 3.1 신규 파일

```
frontend/src/shared/lib/phone-format/
└── index.ts          # formatPhone(raw: string): string
```

(`shared/lib/`은 도메인 무관 공용 유틸. FSD 패턴.)

### 3.2 `formatPhone` 구현

```ts
// frontend/src/shared/lib/phone-format/index.ts
export function formatPhone(raw: string): string {
  const digits = raw.replace(/\D/g, "").slice(0, 11);
  if (digits.length <= 3) return digits;
  if (digits.length <= 7) return `${digits.slice(0, 3)}-${digits.slice(3)}`;
  return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`;
}
```

### 3.3 shared Public API

`frontend/src/shared/lib/phone-format/index.ts` 자체가 슬라이스 root이므로 별도 barrel 불필요. 사용처는 `import { formatPhone } from "@/shared/lib/phone-format";`

### 3.4 CustomerFormModal 통합

기존 `register("phone")` 호출에 `onChange` 옵션 추가:

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

(react-hook-form의 `register`는 `onChange` 옵션을 받고, 그 안에서 이벤트의 `target.value`를 변경하면 후속 처리에 반영됨.)

zod schema에서 birthDate, gender 변경:

```ts
const schema = z.object({
  name: z.string().min(1, "이름을 입력하세요"),
  phone: z.string().regex(/^010-\d{3,4}-\d{4}$/, "올바른 형식: 010-0000-0000"),
  birthDate: z.string().min(1, "생년월일을 입력하세요"),         // optional 제거
  gender: z.enum(["M", "F"], { message: "성별을 선택하세요" }),  // optional 제거, 빈 값 거부
  email: z.string().email("올바른 이메일").or(z.literal("")).optional(),
  address: z.string().optional(),
  memo: z.string().optional(),
});
```

submit 시 payload — birthDate / gender 모두 채워져 있으므로 `|| null` 불필요:

```ts
const payload = {
  name: values.name,
  phone: values.phone,
  birthDate: values.birthDate,                  // 이전: values.birthDate || null
  gender: values.gender,                        // 이전: values.gender || null
  email: values.email || null,
  address: values.address || null,
  memo: values.memo || null,
};
```

기존 select element의 default value (`""`)가 submit 시점에 zod 검증에 걸리도록 schema에서 `""`을 enum 값에서 제거. 사용자는 명시적으로 "남" 또는 "여" 선택해야 함.

### 3.5 `/register` page 통합

`/register` page의 phone 필드도 동일하게 처리:

```tsx
{ name: "phone", label: "연락처", type: "tel", placeholder: "010-0000-0000" }
```

이 필드를 `register("phone", { onChange: (e) => { e.target.value = formatPhone(e.target.value); } })` 로 변경.

(현재 `/register/page.tsx`가 fields 배열을 map해서 렌더하는 구조이므로, phone 필드에만 onChange 적용하는 방식으로 구조 약간 수정 필요.)

## 4. Backend 영향

**없음.** 백엔드 검증 규칙(`^010-\d{3,4}-\d{4}$`, birth_date nullable 컬럼)은 그대로. 프론트가 항상 올바른 형식으로 보냄.

birthDate가 NOT NULL이어야 한다면 백엔드 스키마 변경이 추가로 필요하지만, 본 변경은 **프론트 UX만**. 백엔드는 여전히 NULL 허용 — 추후 다른 흐름(API 직접 호출 등)에서 NULL 들어올 가능성 보존.

## 5. 테스트 (수동 스모크)

### 5.1 CustomerFormModal

1. AGENT1/AGENT2로 `/customers` → "+ 새 고객 등록"
2. 휴대폰번호 칸에 `01012345678` 입력 → 화면 `010-1234-5678` 표시
3. 같은 칸에 `010-1234-5678` paste → 그대로 표시
4. 영문 `abc` 입력 → 무시
5. `010-1234-56789` 11자리 초과 → 11자리만 유지
6. `010-1234-` 까지만 입력 후 제출 → "올바른 형식…" 에러
7. 생년월일 비우고 제출 → "생년월일을 입력하세요" 에러
8. 성별 "선택" 상태로 두고 제출 → "성별을 선택하세요" 에러
9. 이름 + 휴대폰 + 생년월일 + 성별만 채우고 제출 → 등록 성공
10. 모든 필드 채우고 제출 → 등록 성공

### 5.2 `/register` 페이지

1. 회원가입 폼 phone 입력 동일 동작
2. 다른 필드는 기존 동작 유지

## 6. 진행 순서

- 별도 PR (`chore/customer-phone-format`)
- 시점: `feat/health-analysis` PR 머지 후 또는 병행 가능 (다른 영역 코드 손대지 않음)
- `feat/proposal-customer-picker` 와는 독립

## 7. Out of Scope

- 백엔드 regex 완화
- 다른 페이지 phone 필드 (claim 등록 등 — 존재하면 별도 작업)
- 국가번호 처리 (+82)
- 휴대폰 이외 전화번호 (02-, 031- 등)
- 자동 포맷팅 단위 테스트 (수동 검증으로 충분)
- backend birth_date 컬럼 NOT NULL 변경 (프론트만 강제)
