import { cookies } from "next/headers";
import type { MeResponse } from "../api";

export async function getMe(): Promise<MeResponse | null> {
  const cookieStore = await cookies();
  const session = cookieStore.get("AGENT_SESSION");
  if (!session) return null;

  try {
    const res = await fetch(
      `${process.env.NEXT_PUBLIC_API_BASE_URL}/api/auth/me`,
      {
        headers: { Cookie: `${session.name}=${session.value}` },
        cache: "no-store",
      },
    );
    if (!res.ok) return null;
    return res.json() as Promise<MeResponse>;
  } catch {
    return null;
  }
}