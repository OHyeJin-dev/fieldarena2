// frontend/src/entities/health-analysis/ui/analysis-result/index.tsx
"use client";

import type { HealthAnalysisDto, UnderwritingRecommendation } from "../../api";
import { RiskBadge } from "../risk-badge";

const REC_LABEL: Record<UnderwritingRecommendation, string> = {
  APPROVE: "권고 (정상 인수)",
  CONDITIONAL: "조건부 (보험료 할증 권고)",
  DECLINE: "거절 권고",
};

interface Props {
  analysis: HealthAnalysisDto;
}

export function AnalysisResult({ analysis }: Props) {
  const analyzedAt = new Date(analysis.analyzedAt).toLocaleString("ko-KR");
  return (
    <div className="flex flex-col gap-4">
      <div className="text-sm text-on-surface-variant">분석 시각: {analyzedAt}</div>
      <div className="flex items-center gap-3">
        <span className="text-sm font-medium text-on-surface">위험 등급</span>
        <RiskBadge grade={analysis.riskGrade} />
        <span className="text-sm text-on-surface-variant">
          유병 여부: {analysis.hasDisease ? "있음" : "없음"}
        </span>
      </div>
      {analysis.diseases.length > 0 && (
        <div>
          <div className="text-sm font-semibold text-on-surface mb-2">보유 질환</div>
          <ul className="flex flex-col gap-2">
            {analysis.diseases.map((d) => (
              <li key={d.code} className="text-sm text-on-surface">
                • {d.name} ({d.code})
                <div className="ml-3 text-xs text-on-surface-variant">
                  추정 진단 {d.diagnosedAt} · 처방 {d.frequency}
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}
      <div>
        <div className="text-sm font-semibold text-on-surface">인수 권고</div>
        <div className="text-sm text-on-surface">{REC_LABEL[analysis.underwritingRecommendation]}</div>
      </div>
      <div>
        <div className="text-sm font-semibold text-on-surface">의견</div>
        <div className="text-sm text-on-surface-variant">{analysis.summary}</div>
      </div>
    </div>
  );
}
