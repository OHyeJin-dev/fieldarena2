"use client";

import { useState } from "react";
import { useUsers, useApproveMutation, useRejectMutation } from "@/entities/user";

const STATUS_LABELS: Record<string, string> = {
  PENDING: "대기",
  ACTIVE: "승인",
  REJECTED: "거절",
};

const ROLE_LABELS: Record<string, string> = {
  ADMIN: "관리자",
  AGENT1: "설계사1",
  AGENT2: "설계사2",
};

export default function AdminUsersPageClient() {
  const [statusFilter, setStatusFilter] = useState("PENDING");
  const { data: users = [], isLoading } = useUsers(statusFilter);
  const approveMutation = useApproveMutation();
  const rejectMutation = useRejectMutation();
  const [selectedRole, setSelectedRole] = useState<Record<string, string>>({});

  return (
    <div className="p-6 max-w-5xl mx-auto">
      <h1 className="text-xl font-bold text-on-surface mb-6">가입 신청 관리</h1>

      <div className="flex gap-2 mb-6">
        {["PENDING", "ACTIVE", "REJECTED"].map((s) => (
          <button
            key={s}
            onClick={() => setStatusFilter(s)}
            className={[
              "px-4 py-2 rounded-lg text-sm font-medium transition-colors",
              statusFilter === s
                ? "bg-primary-container text-on-primary"
                : "bg-surface-container text-on-surface-variant hover:bg-surface-container-high",
            ].join(" ")}
          >
            {STATUS_LABELS[s]}
          </button>
        ))}
      </div>

      {isLoading && <p className="text-on-surface-variant">로딩 중…</p>}

      {!isLoading && users.length === 0 && (
        <p className="text-on-surface-variant">해당 상태의 사용자가 없습니다.</p>
      )}

      <div className="flex flex-col gap-3">
        {users.map((user) => (
          <div
            key={user.id}
            className="bg-surface-container-lowest rounded-xl p-5 flex flex-col sm:flex-row sm:items-center gap-4 shadow-sm"
          >
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 mb-1">
                <span className="font-semibold text-on-surface">{user.name}</span>
                <span className="text-xs text-on-surface-variant">({user.id})</span>
              </div>
              <div className="text-sm text-on-surface-variant flex flex-wrap gap-x-4 gap-y-1">
                <span>{user.phone}</span>
                <span>{user.email}</span>
                <span>{user.gaName}</span>
                {user.role && (
                  <span className="font-medium text-primary">{ROLE_LABELS[user.role] ?? user.role}</span>
                )}
              </div>
            </div>

            {user.status === "PENDING" && (
              <div className="flex items-center gap-2 shrink-0">
                <select
                  value={selectedRole[user.id] ?? "AGENT1"}
                  onChange={(e) =>
                    setSelectedRole((prev) => ({ ...prev, [user.id]: e.target.value }))
                  }
                  className="h-9 px-3 rounded-lg border border-outline-variant bg-surface text-sm text-on-surface outline-none"
                >
                  <option value="AGENT1">설계사1</option>
                  <option value="AGENT2">설계사2</option>
                  <option value="ADMIN">관리자</option>
                </select>
                <button
                  onClick={() =>
                    approveMutation.mutate({ id: user.id, role: selectedRole[user.id] ?? "AGENT1" })
                  }
                  disabled={approveMutation.isPending}
                  className="h-9 px-4 rounded-lg bg-primary-container text-on-primary text-sm font-medium hover:opacity-90 disabled:opacity-50"
                >
                  승인
                </button>
                <button
                  onClick={() => rejectMutation.mutate(user.id)}
                  disabled={rejectMutation.isPending}
                  className="h-9 px-4 rounded-lg bg-status-error-container text-status-error text-sm font-medium hover:opacity-90 disabled:opacity-50"
                >
                  거절
                </button>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}