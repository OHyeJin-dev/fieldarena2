"use client";

import { useRouter } from "next/navigation";
import { Button } from "@/shared/ui/button";
import { useLogoutMutation } from "./queries";

export function LogoutButton() {
  const router = useRouter();
  const logoutMutation = useLogoutMutation();

  return (
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
  );
}
