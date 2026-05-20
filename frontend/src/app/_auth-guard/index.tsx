"use client";

import { useMe } from "@/entities/session";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { AppShell } from "@/widgets/app-shell";
import { Sidebar } from "@/widgets/sidebar";
import { TopBar } from "@/widgets/top-bar";

interface AuthGuardProps {
  children: React.ReactNode;
  allowedRoles?: string[];
}

export function AuthGuard({ children, allowedRoles }: AuthGuardProps) {
  const { data: user, isLoading } = useMe();
  const router = useRouter();

  useEffect(() => {
    if (isLoading) return;
    if (!user) {
      router.replace("/login");
      return;
    }
    if (allowedRoles && !allowedRoles.includes(user.role)) {
      router.replace("/dashboard");
    }
  }, [user, isLoading, router, allowedRoles]);

  if (isLoading) return null;
  if (!user) return null;
  if (allowedRoles && !allowedRoles.includes(user.role)) return null;

  return (
    <AppShell
      renderSidebar={({ desktopCollapsed, mobileDrawerOpen, onDesktopToggle, onMobileClose }) => (
        <Sidebar
          role={user.role}
          desktopCollapsed={desktopCollapsed}
          mobileDrawerOpen={mobileDrawerOpen}
          onDesktopToggle={onDesktopToggle}
          onMobileClose={onMobileClose}
        />
      )}
      renderTopBar={({ onMenuClick }) => (
        <TopBar username={user.id} onMenuClick={onMenuClick} />
      )}
    >
      {children}
    </AppShell>
  );
}
