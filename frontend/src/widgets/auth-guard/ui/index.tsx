"use client";

import { useMe } from "@/entities/session";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { AppShell } from "@/widgets/app-shell";

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

  return <AppShell userId={user.id} role={user.role}>{children}</AppShell>;
}
