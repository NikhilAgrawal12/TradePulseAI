export function getStoredToken(): string | null {
  return localStorage.getItem("authToken") ?? sessionStorage.getItem("authToken");
}

export function clearStoredToken(): void {
  localStorage.removeItem("authToken");
  sessionStorage.removeItem("authToken");
}

export function getEmailFromToken(token: string | null): string | null {
  if (!token) {
    return null;
  }

  const parts = token.split(".");
  if (parts.length < 2) {
    return null;
  }

  try {
    const normalized = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = normalized + "=".repeat((4 - (normalized.length % 4)) % 4);
    const payload = JSON.parse(atob(padded)) as { sub?: string };
    return typeof payload.sub === "string" ? payload.sub : null;
  } catch {
    return null;
  }
}

