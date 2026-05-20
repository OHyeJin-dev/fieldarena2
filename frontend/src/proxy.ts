import { NextRequest, NextResponse } from "next/server";

const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";
const SESSION_COOKIE = process.env.SESSION_COOKIE_NAME ?? "AGENT_SESSION";

type Role = "ADMIN" | "AGENT1" | "AGENT2";

const ROLE_RULES: Array<{ prefix: string; allow: Role[] }> = [
  { prefix: "/admin", allow: ["ADMIN"] },
  { prefix: "/proposals", allow: ["ADMIN", "AGENT1"] },
  { prefix: "/underwriting", allow: ["ADMIN", "AGENT1"] },
  { prefix: "/customers", allow: ["ADMIN", "AGENT1", "AGENT2"] },
  { prefix: "/claims", allow: ["ADMIN", "AGENT1", "AGENT2"] },
  { prefix: "/contracts", allow: ["ADMIN", "AGENT1", "AGENT2"] },
  { prefix: "/dashboard", allow: ["ADMIN", "AGENT1", "AGENT2"] },
];

function redirectToLogin(req: NextRequest): NextResponse {
  const url = req.nextUrl.clone();
  url.pathname = "/login";
  url.search = "";
  return NextResponse.redirect(url);
}

function redirectToDashboard(req: NextRequest): NextResponse {
  const url = req.nextUrl.clone();
  url.pathname = "/dashboard";
  url.search = "";
  return NextResponse.redirect(url);
}

export async function proxy(req: NextRequest): Promise<NextResponse> {
  const sessionCookie = req.cookies.get(SESSION_COOKIE);
  if (!sessionCookie) return redirectToLogin(req);

  const cookieHeader = req.headers.get("cookie") ?? "";

  let meResponse: Response;
  try {
    meResponse = await fetch(`${BACKEND_URL}/api/auth/me`, {
      headers: { cookie: cookieHeader },
      cache: "no-store",
    });
  } catch {
    return redirectToLogin(req);
  }

  if (meResponse.status !== 200) return redirectToLogin(req);

  let parsed: { role: unknown };
  try {
    parsed = (await meResponse.json()) as { role: unknown };
  } catch {
    return redirectToLogin(req);
  }

  const validRoles: Role[] = ["ADMIN", "AGENT1", "AGENT2"];
  const role = parsed.role as Role;
  if (!validRoles.includes(role)) return redirectToLogin(req);

  const matched = ROLE_RULES.find((rule) =>
    req.nextUrl.pathname.startsWith(rule.prefix),
  );
  if (matched && !matched.allow.includes(role)) {
    return redirectToDashboard(req);
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    "/dashboard/:path*",
    "/customers/:path*",
    "/contracts/:path*",
    "/claims/:path*",
    "/proposals/:path*",
    "/underwriting/:path*",
    "/admin/:path*",
  ],
};
