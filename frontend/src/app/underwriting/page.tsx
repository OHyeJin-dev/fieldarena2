"use client";

import { useState } from "react";
import { usePolicies } from "@/features/contracts/queries";

const STATUS_OPTIONS = ["전체", "심사 중", "서류 보완", "승인 완료", "반려"] as const;

const STATUS_CLASS: Record<string, string> = {
  "심사 중": "bg-status-info-container text-status-info",
  "승인 완료": "bg-status-success-container text-status-success",
  "서류 보완": "bg-status-warning-container text-status-warning",
  "반려": "bg-status-error-container text-status-error",
};

export default function UnderwritingPage() {
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);

  const { data, isLoading, isError } = usePolicies({
    page,
    size: 20,
    status: status || undefined,
  });

  function handleStatusChange(value: string) {
    setStatus(value === "전체" ? "" : value);
    setPage(0);
  }

  return (
    <div className="p-6 max-w-300 mx-auto">
      <h1 className="text-2xl font-bold text-on-surface mb-6">심사 현황</h1>

      <div className="mb-4">
        <select
          aria-label="상태 필터"
          value={status || "전체"}
          onChange={(e) => handleStatusChange(e.target.value)}
          className="px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest text-sm text-on-surface outline-none focus:border-primary-container"
        >
          {STATUS_OPTIONS.map((opt) => (
            <option key={opt} value={opt}>
              {opt}
            </option>
          ))}
        </select>
      </div>

      <div className="bg-surface-container-lowest rounded-2xl shadow-card overflow-hidden">
        <table className="w-full">
          <thead>
            <tr className="bg-surface-container-low text-sm text-on-surface-variant">
              <th className="text-left px-6 py-3 font-medium">계약번호</th>
              <th className="text-left px-6 py-3 font-medium">고객명</th>
              <th className="text-left px-6 py-3 font-medium">상품</th>
              <th className="text-left px-6 py-3 font-medium">보험사</th>
              <th className="text-left px-6 py-3 font-medium">상태</th>
              <th className="text-left px-6 py-3 font-medium">계약일</th>
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <>
                {Array.from({ length: 5 }).map((_, i) => (
                  <tr key={i} className="border-b border-outline-variant">
                    {Array.from({ length: 6 }).map((_, j) => (
                      <td key={j} className="px-6 py-4">
                        <div className="h-4 bg-surface-container rounded animate-pulse" />
                      </td>
                    ))}
                  </tr>
                ))}
              </>
            )}
            {isError && (
              <tr>
                <td colSpan={6} className="px-6 py-12 text-center text-sm text-status-error">
                  데이터를 불러오지 못했습니다.
                </td>
              </tr>
            )}
            {!isLoading && !isError && data?.content.length === 0 && (
              <tr>
                <td colSpan={6} className="px-6 py-12 text-center text-sm text-on-surface-variant">
                  심사 내역이 없습니다.
                </td>
              </tr>
            )}
            {!isLoading &&
              !isError &&
              data?.content.map((c, i) => (
                <tr
                  key={c.id}
                  className={i < (data.content.length - 1) ? "border-b border-outline-variant" : ""}
                >
                  <td className="px-6 py-4 text-sm font-mono text-on-surface">{c.policyNumber}</td>
                  <td className="px-6 py-4 text-sm text-on-surface">{c.customerName}</td>
                  <td className="px-6 py-4 text-sm text-on-surface">{c.productName}</td>
                  <td className="px-6 py-4 text-sm text-on-surface">{c.insurerName}</td>
                  <td className="px-6 py-4">
                    <span
                      className={`text-xs font-medium px-2 py-1 rounded-full ${
                        STATUS_CLASS[c.status] ?? "bg-surface-container text-on-surface-variant"
                      }`}
                    >
                      {c.status}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-sm text-on-surface-variant">{c.contractDate}</td>
                </tr>
              ))}
          </tbody>
        </table>

        {data && data.totalPages > 1 && (
          <div className="flex items-center justify-between px-6 py-4 border-t border-outline-variant">
            <button
              type="button"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="px-4 py-2 text-sm rounded-lg border border-outline-variant text-on-surface disabled:opacity-40 hover:bg-surface-container-low transition-colors"
            >
              이전
            </button>
            <span className="text-sm text-on-surface-variant">
              {page + 1} / {data.totalPages}
            </span>
            <button
              type="button"
              onClick={() => setPage((p) => Math.min(data.totalPages - 1, p + 1))}
              disabled={page >= data.totalPages - 1}
              className="px-4 py-2 text-sm rounded-lg border border-outline-variant text-on-surface disabled:opacity-40 hover:bg-surface-container-low transition-colors"
            >
              다음
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
