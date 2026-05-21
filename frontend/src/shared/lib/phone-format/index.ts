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
