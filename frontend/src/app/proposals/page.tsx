"use client";

import { useState } from "react";
import { Plus } from "lucide-react";
import { useProposals } from "@/features/proposals/queries";
import { ProposalFormModal } from "@/features/proposals/ProposalFormModal";

const STATUS_OPTIONS = ["전체", "작성 중", "설계 완료", "취소"] as const;

const STATUS_CLASS: Record<string, string> = {
  "작성 중": "bg-status-info-container text-status-info",
  "설계 완료": "bg-status-success-container text-status-success",
  "취소": "bg-surface-container-high text-on-surface-variant",
};

function formatPremium(value: number | null): string {
  if (value == null) return "-";
  return value.toLocaleString("ko-KR") + "원";
}

export default function ProposalsPage() {
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [showModal, setShowModal] = useState(false);

  const { data, isLoading, isError } = useProposals({
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
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-on-surface">설계 관리</h1>
        <button
          type="button"
          onClick={() => setShowModal(true)}
          className="flex items-center gap-2 px-4 py-2 rounded-xl bg-primary-container text-on-primary text-sm font-semibold hover:opacity-90 transition-opacity"
        >
          <Plus size={16} />
          새 설계 등록
        </button>
      </div>

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
              <th className="text-left px-6 py-3 font-medium">고객명</th>
              <th className="text-left px-6 py-3 font-medium">나이</th>
              <th className="text-left px-6 py-3 font-medium">연락처</th>
              <th className="text-left px-6 py-3 font-medium">상품</th>
              <th className="text-left px-6 py-3 font-medium">보험사</th>
              <th className="text-left px-6 py-3 font-medium">월 보험료</th>
              <th className="text-left px-6 py-3 font-medium">상태</th>
              <th className="text-left px-6 py-3 font-medium">설계일</th>
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <>
                {Array.from({ length: 5 }).map((_, i) => (
                  <tr key={i} className="border-b border-outline-variant">
                    {Array.from({ length: 8 }).map((_, j) => (
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
                <td colSpan={8} className="px-6 py-12 text-center text-sm text-status-error">
                  데이터를 불러오지 못했습니다.
                </td>
              </tr>
            )}
            {!isLoading && !isError && data?.content.length === 0 && (
              <tr>
                <td colSpan={8} className="px-6 py-12 text-center text-sm text-on-surface-variant">
                  설계 내역이 없습니다.
                </td>
              </tr>
            )}
            {!isLoading &&
              !isError &&
              data?.content.map((p, i) => (
                <tr
                  key={p.id}
                  className={i < (data.content.length - 1) ? "border-b border-outline-variant" : ""}
                >
                  <td className="px-6 py-4 text-sm text-on-surface">{p.customerName}</td>
                  <td className="px-6 py-4 text-sm text-on-surface-variant">{p.age}</td>
                  <td className="px-6 py-4 text-sm font-mono text-on-surface-variant">{p.phoneNumber}</td>
                  <td className="px-6 py-4 text-sm text-on-surface">{p.productName}</td>
                  <td className="px-6 py-4 text-sm text-on-surface">{p.insurerName}</td>
                  <td className="px-6 py-4 text-sm font-mono text-on-surface">
                    {formatPremium(p.monthlyPremium)}
                  </td>
                  <td className="px-6 py-4">
                    <span
                      className={`text-xs font-medium px-2 py-1 rounded-full ${
                        STATUS_CLASS[p.status] ?? "bg-surface-container text-on-surface-variant"
                      }`}
                    >
                      {p.status}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-sm text-on-surface-variant">{p.proposedDate}</td>
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

      {showModal && <ProposalFormModal onClose={() => setShowModal(false)} />}
    </div>
  );
}
