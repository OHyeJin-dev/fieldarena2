"use client";

import { Menu } from "lucide-react";
import { useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Button } from "@/shared/ui/button";
import { apiFetch } from "@/shared/api";

function logout(): Promise<void> {
  return apiFetch<void>("/api/auth/logout", { method: "POST" });
}

function useLogoutMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: logout,
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: ["me"] });
    },
  });
}

interface TopBarProps {
  username: string;
  onMenuClick: () => void;
}

export function TopBar({ username, onMenuClick }: TopBarProps) {
  const router = useRouter();
  const logoutMutation = useLogoutMutation();

  return (
    <header className="h-16 flex items-center justify-between px-6 bg-surface-container-lowest border-b border-outline-variant shrink-0">
      <button
        type="button"
        onClick={onMenuClick}
        className="p-2 rounded-lg text-on-surface-variant hover:bg-surface-container-low transition-colors"
        aria-label="메뉴 토글"
      >
        <Menu size={20} />
      </button>
      <div className="flex items-center gap-4">
        <span className="text-sm text-on-surface-variant">{username}</span>
        <Button
          onClick={() =>
            logoutMutation.mutate(undefined, {
              onSuccess: () => router.push("/login"),
            })
          }
          loading={logoutMutation.isPending}
          className="!w-auto px-8"
        >
          로그아웃
        </Button>
      </div>
    </header>
  );
}
