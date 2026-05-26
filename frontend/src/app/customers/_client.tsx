"use client";

import { useState } from "react";
import { Plus } from "lucide-react";
import { useCustomers } from "@/entities/customer";
import type { CustomerDto } from "@/entities/customer";
import { useDeleteCustomer } from "@/features/customer/delete";
import { CustomerFormModal } from "@/features/customer/manage";

export default function CustomersPageClient() {
  const [page, setPage] = useState(0);
  const [showModal, setShowModal] = useState(false);
  const [editing, setEditing] = useState<CustomerDto | null>(null);
  const { data, isLoading, isError } = useCustomers({ page, size: 20 });
  const del = useDeleteCustomer();

  function openCreate() {
    setEditing(null);
    setShowModal(true);
  }
  function openEdit(c: CustomerDto) {
    setEditing(c);
    setShowModal(true);
  }
  function handleDelete(c: CustomerDto) {
    if (!confirm(`'${c.name}' 고객을 삭제하시겠습니까?`)) return;
    del.mutate(c.id);
  }

  return (
    <div className="p-6 max-w-300 mx-auto">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-on-surface">고객 관리</h1>
        <button
          type="button"
          onClick={openCreate}
          className="flex items-center gap-2 px-4 py-2 rounded-xl bg-primary-container text-on-primary-container text-sm font-semibold hover:opacity-90 transition-opacity"
        >
          <Plus size={16} /> 신규 고객 등록
        </button>
      </div>

      <div className="bg-surface-container-lowest rounded-2xl shadow-card overflow-hidden">
        <table className="w-full">
          <thead>
            <tr className="bg-surface-container-low text-sm text-on-surface-variant">
              <th className="text-left px-6 py-3 font-medium">이름</th>
              <th className="text-left px-6 py-3 font-medium">전화</th>
              <th className="text-left px-6 py-3 font-medium">생년월일</th>
              <th className="text-left px-6 py-3 font-medium">성별</th>
              <th className="text-left px-6 py-3 font-medium">이메일</th>
              <th className="text-left px-6 py-3 font-medium">등록일</th>
              <th className="text-right px-6 py-3 font-medium">액션</th>
            </tr>
          </thead>
          <tbody>
            {isLoading &&
              Array.from({ length: 5 }).map((_, i) => (
                <tr key={i} className="border-b border-outline-variant">
                  {Array.from({ length: 7 }).map((_, j) => (
                    <td key={j} className="px-6 py-4">
                      <div className="h-4 bg-surface-container rounded animate-pulse" />
                    </td>
                  ))}
                </tr>
              ))}
            {isError && (
              <tr>
                <td colSpan={7} className="px-6 py-12 text-center text-sm text-status-error">
                  데이터를 불러오지 못했습니다.
                </td>
              </tr>
            )}
            {!isLoading && !isError && data?.content.length === 0 && (
              <tr>
                <td colSpan={7} className="px-6 py-12 text-center text-sm text-on-surface-variant">
                  등록된 고객이 없습니다.
                </td>
              </tr>
            )}
            {!isLoading && !isError && data?.content.map((c, i) => (
              <tr key={c.id} className={i < (data.content.length - 1) ? "border-b border-outline-variant" : ""}>
                <td className="px-6 py-4 text-sm text-on-surface">{c.name}</td>
                <td className="px-6 py-4 text-sm text-on-surface">{c.phone}</td>
                <td className="px-6 py-4 text-sm text-on-surface">{c.birthDate ?? "-"}</td>
                <td className="px-6 py-4 text-sm text-on-surface">
                  {c.gender === "M" ? "남" : c.gender === "F" ? "여" : "-"}
                </td>
                <td className="px-6 py-4 text-sm text-on-surface">{c.email ?? "-"}</td>
                <td className="px-6 py-4 text-sm text-on-surface-variant">{c.createdAt.slice(0, 10)}</td>
                <td className="px-6 py-4 text-right text-sm">
                  <button
                    type="button"
                    onClick={() => openEdit(c)}
                    className="text-primary mr-3 hover:underline"
                  >
                    수정
                  </button>
                  <button
                    type="button"
                    onClick={() => handleDelete(c)}
                    className="text-status-error hover:underline"
                  >
                    삭제
                  </button>
                </td>
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
            <span className="text-sm text-on-surface-variant">{page + 1} / {data.totalPages}</span>
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

      {showModal && (
        <CustomerFormModal
          onClose={() => setShowModal(false)}
          initial={editing ?? undefined}
        />
      )}
    </div>
  );
}