"use client";

import { useState } from "react";
import type { Scenario } from "@/entities/health-analysis";
import { Button } from "@/shared/ui/button";

const SCENARIO_OPTIONS: { value: Scenario; label: string }[] = [
  { value: "NORMAL", label: "정상" },
  { value: "HYPERTENSION", label: "고혈압" },
  { value: "DIABETES", label: "당뇨" },
  { value: "COMPLEX", label: "복합" },
];

interface Props {
  isPending: boolean;
  onSubmit: (scenario: Scenario) => void;
}

export function StepInput({ isPending, onSubmit }: Props) {
  const [mode, setMode] = useState<"auto" | "scenario">("auto");
  const [scenario, setScenario] = useState<Scenario>("NORMAL");

  function handleSubmit() {
    onSubmit(mode === "auto" ? "RANDOM" : scenario);
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="text-sm text-on-surface-variant">데이터 수집 시나리오</div>
      <div className="flex flex-col gap-2">
        <label className="flex items-center gap-2 text-sm">
          <input
            type="radio"
            checked={mode === "auto"}
            onChange={() => setMode("auto")}
          />
          자동 수집 (기본)
        </label>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="radio"
            checked={mode === "scenario"}
            onChange={() => setMode("scenario")}
          />
          시나리오 선택 (개발/시연용)
        </label>
        {mode === "scenario" && (
          <div className="ml-6 flex gap-2 flex-wrap">
            {SCENARIO_OPTIONS.map((opt) => (
              <button
                key={opt.value}
                type="button"
                onClick={() => setScenario(opt.value)}
                className={`px-3 py-1 rounded-lg text-xs ${
                  scenario === opt.value
                    ? "bg-primary-container text-on-primary"
                    : "bg-surface-container text-on-surface"
                }`}
              >
                {opt.label}
              </button>
            ))}
          </div>
        )}
      </div>
      <Button onClick={handleSubmit} loading={isPending} className="!w-auto self-start px-6">
        수집 시작
      </Button>
    </div>
  );
}
