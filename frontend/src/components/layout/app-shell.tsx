"use client";

import { useState } from "react";
import { Sidebar } from "./sidebar";
import { TopBar } from "./top-bar";

interface AppShellProps {
  username: string;
  children: React.ReactNode;
}

export function AppShell({ username, children }: AppShellProps) {
  const [desktopCollapsed, setDesktopCollapsed] = useState(false);
  const [mobileDrawerOpen, setMobileDrawerOpen] = useState(false);

  function handleMenuClick() {
    if (typeof window !== "undefined" && window.innerWidth < 1024) {
      setMobileDrawerOpen((o) => !o);
    } else {
      setDesktopCollapsed((c) => !c);
    }
  }

  return (
    <div className="flex h-screen overflow-hidden">
      {/* 모바일 오버레이 */}
      {mobileDrawerOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/50 lg:hidden"
          onClick={() => setMobileDrawerOpen(false)}
        />
      )}

      <Sidebar
        desktopCollapsed={desktopCollapsed}
        mobileDrawerOpen={mobileDrawerOpen}
        onDesktopToggle={() => setDesktopCollapsed((c) => !c)}
        onMobileClose={() => setMobileDrawerOpen(false)}
      />

      <div className="flex flex-col flex-1 overflow-hidden min-w-0">
        <TopBar username={username} onMenuClick={handleMenuClick} />
        <main className="flex-1 overflow-auto bg-surface">{children}</main>
      </div>
    </div>
  );
}
