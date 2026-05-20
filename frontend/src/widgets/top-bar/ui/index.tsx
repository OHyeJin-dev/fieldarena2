"use client";

import { Menu } from "lucide-react";
import { LogoutButton } from "@/features/auth/logout";

interface TopBarProps {
  username: string;
  onMenuClick: () => void;
}

export function TopBar({ username, onMenuClick }: TopBarProps) {
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
        <LogoutButton />
      </div>
    </header>
  );
}
