function readCookie(name: string): string | null {
  if (typeof document === "undefined") return null;
  const match = document.cookie.match(new RegExp(`(^|; )${name}=([^;]+)`));
  return match?.[2] ? decodeURIComponent(match[2]) : null;
}

export class ApiError extends Error {
  constructor(
    public status: number,
    public body: unknown,
  ) {
    super(`API ${status}`);
  }
}

export async function apiFetch<T>(
  input: string,
  init: RequestInit = {},
): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);

  if (!["GET", "HEAD"].includes(method)) {
    const token = readCookie("XSRF-TOKEN");
    if (token) headers.set("X-XSRF-TOKEN", token);
  }
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const res = await fetch(input, { ...init, headers, credentials: "include" });

  if (!res.ok) {
    if (
      res.status === 401 &&
      typeof window !== "undefined" &&
      !input.includes("/api/auth/login") &&
      window.location.pathname !== "/login"
    ) {
      window.location.replace("/login");
    }
    const body = await res.json().catch(() => null);
    throw new ApiError(res.status, body);
  }
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}
