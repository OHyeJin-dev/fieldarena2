"use client";

import { useMe } from "@/features/auth/queries";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { AppShell } from "./app-shell";

export function AuthGuard({ children }: { children: React.ReactNode }) {
  const { data: user, isLoading } = useMe();
  const router = useRouter();

  useEffect(() => {
    if (!isLoading && !user) {
      router.replace("/login");
    }
  }, [user, isLoading, router]);

  if (isLoading) return null;
  if (!user) return null;

  return <AppShell username={user.username}>{children}</AppShell>;
}
