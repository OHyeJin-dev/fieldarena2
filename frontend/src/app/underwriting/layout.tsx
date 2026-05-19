import { redirect } from "next/navigation";
import { AppShell } from "@/components/layout/app-shell";
import { getMe } from "@/features/auth/server";

export default async function UnderwritingLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const user = await getMe();
  if (!user) redirect("/login");

  return <AppShell username={user.username}>{children}</AppShell>;
}
