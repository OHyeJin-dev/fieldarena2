// frontend/src/entities/health-analysis/ui/risk-badge/index.tsx
"use client";

import type { RiskGrade } from "../../api";

const GRADE_LABEL: Record<RiskGrade, string> = {
  NORMAL: "정상",
  CAUTION: "주의",
  RISK: "위험",
};

const GRADE_CLASS: Record<RiskGrade, string> = {
  NORMAL: "bg-status-success-container text-status-success",
  CAUTION: "bg-status-warning-container text-status-warning",
  RISK: "bg-status-error-container text-status-error",
};

interface Props {
  grade: RiskGrade;
  onClick?: () => void;
}

export function RiskBadge({ grade, onClick }: Props) {
  const className = `inline-flex items-center px-2 py-1 rounded-full text-xs font-medium ${GRADE_CLASS[grade]}`;
  if (onClick) {
    return (
      <button type="button" onClick={onClick} className={`${className} cursor-pointer hover:opacity-80`}>
        {GRADE_LABEL[grade]}
      </button>
    );
  }
  return <span className={className}>{GRADE_LABEL[grade]}</span>;
}
