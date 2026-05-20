"use client";

import { useState } from "react";
import { Sidebar } from "@/widgets/sidebar";
import { TopBar } from "@/widgets/top-bar";

interface AppShellProps {
  userId: string;
  role: string;
  children: React.ReactNode;
}

export function AppShell({ userId, role, children }: AppShellProps) {
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
      {mobileDrawerOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/50 lg:hidden"
          onClick={() => setMobileDrawerOpen(false)}
        />
      )}

      <Sidebar
        role={role}
        desktopCollapsed={desktopCollapsed}
        mobileDrawerOpen={mobileDrawerOpen}
        onDesktopToggle={() => setDesktopCollapsed((c) => !c)}
        onMobileClose={() => setMobileDrawerOpen(false)}
      />

      <div className="flex flex-col flex-1 overflow-hidden min-w-0">
        <TopBar username={userId} onMenuClick={handleMenuClick} />
        <main className="flex-1 overflow-auto bg-surface">{children}</main>
      </div>
    </div>
  );
}
