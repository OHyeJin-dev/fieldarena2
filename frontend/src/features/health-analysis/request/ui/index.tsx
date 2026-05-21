"use client";

import { useState } from "react";
import { X } from "lucide-react";
import type { HealthAnalysisDto, Scenario } from "@/entities/health-analysis";
import { useCreateAnalysis } from "../model";
import { StepInput } from "./step-input";
import { StepResult } from "./step-result";

interface Props {
  customer: { id: string; name: string };
  existingAnalysis?: HealthAnalysisDto;
  onClose: () => void;
}

type ModalState =
  | { step: "input" }
  | { step: "collecting" }
  | { step: "result"; analysis: HealthAnalysisDto }
  | { step: "error"; message: string };

export function AnalysisRequestModal({ customer, existingAnalysis, onClose }: Props) {
  const [state, setState] = useState<ModalState>(
    existingAnalysis ? { step: "result", analysis: existingAnalysis } : { step: "input" }
  );
  const mutation = useCreateAnalysis();

  function handleSubmit(scenario: Scenario) {
    setState({ step: "collecting" });
    mutation.mutate(
      { customerId: customer.id, scenario },
      {
        onSuccess: (data) => setState({ step: "result", analysis: data }),
        onError: (err) => setState({
          step: "error",
          message: err instanceof Error ? err.message : "분석 실패",
        }),
      },
    );
  }

  const isPending = state.step === "collecting";
  const currentStep = state.step === "result" ? 2 : 1;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm"
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <div className="bg-surface-container-lowest rounded-2xl shadow-lg w-full max-w-lg mx-4 max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between px-6 py-4 border-b border-outline-variant">
          <div className="text-base font-semibold text-on-surface">
            건강 데이터 분석 — {customer.name}
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded-lg hover:bg-surface-container-low"
            aria-label="닫기"
          >
            <X size={20} />
          </button>
        </div>

        <div className="flex items-center gap-3 px-6 py-3 text-xs text-on-surface-variant border-b border-outline-variant">
          <span className={currentStep === 1 ? "text-on-surface font-semibold" : ""}>
            ● Step 1 · 데이터 수집
          </span>
          <span>—</span>
          <span className={currentStep === 2 ? "text-on-surface font-semibold" : ""}>
            ● Step 2 · 분석 결과
          </span>
        </div>

        <div className="p-6">
          {state.step === "input" && (
            <StepInput isPending={isPending} onSubmit={handleSubmit} />
          )}
          {state.step === "collecting" && (
            <div className="flex flex-col items-center gap-3 py-8">
              <div className="w-8 h-8 border-2 border-primary-container border-t-transparent rounded-full animate-spin" />
              <div className="text-sm text-on-surface-variant">데이터 수집 및 분석 중…</div>
            </div>
          )}
          {state.step === "result" && (
            <StepResult
              analysis={state.analysis}
              onReAnalyze={() => setState({ step: "input" })}
              onClose={onClose}
            />
          )}
          {state.step === "error" && (
            <div className="flex flex-col gap-4">
              <div className="text-sm text-status-error">{state.message}</div>
              <button
                type="button"
                onClick={() => setState({ step: "input" })}
                className="self-start px-4 py-2 text-sm rounded-lg border border-outline-variant"
              >
                다시 시도
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
