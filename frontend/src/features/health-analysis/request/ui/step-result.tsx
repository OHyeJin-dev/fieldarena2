"use client";

import type { HealthAnalysisDto } from "@/entities/health-analysis";
import { AnalysisResult } from "@/entities/health-analysis";
import { Button } from "@/shared/ui/button";

interface Props {
  analysis: HealthAnalysisDto;
  onReAnalyze: () => void;
  onClose: () => void;
}

export function StepResult({ analysis, onReAnalyze, onClose }: Props) {
  return (
    <div className="flex flex-col gap-4">
      <AnalysisResult analysis={analysis} />
      <div className="flex gap-2 justify-end">
        <button
          type="button"
          onClick={onClose}
          className="px-4 py-2 text-sm rounded-lg border border-outline-variant text-on-surface hover:bg-surface-container-low"
        >
          닫기
        </button>
        <Button onClick={onReAnalyze} className="!w-auto px-6">재분석</Button>
      </div>
    </div>
  );
}
