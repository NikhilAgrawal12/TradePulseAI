const AUTH_CHANGED_EVENT = "tradepulseai:auth-changed";

export function getStoredToken(): string | null {
  return localStorage.getItem("authToken") ?? sessionStorage.getItem("authToken");
}

export const SIGN_IN_REQUIRED_MESSAGE = "Please sign in before using cart or watchlist features.";

export function isUserAuthenticated(): boolean {
  return Boolean(getStoredToken());
}

export function showSignInRequiredMessage(message: string = SIGN_IN_REQUIRED_MESSAGE): void {
  if (typeof window === "undefined") {
    return;
  }

  window.dispatchEvent(
    new CustomEvent("tradepulseai:auth-required", {
      detail: { message },
    }),
  );
}

export function requireSignIn(): void {
  if (typeof window === "undefined") {
    return;
  }

  // Redirect-only behavior for protected actions.
  window.dispatchEvent(
    new CustomEvent("tradepulseai:auth-required", {
      detail: { redirectToLogin: true },
    }),
  );
}


function notifyAuthChanged(): void {
  if (typeof window === "undefined") {
    return;
  }

  window.dispatchEvent(
    new CustomEvent(AUTH_CHANGED_EVENT, {
      detail: { token: getStoredToken() },
    }),
  );
}

export function setStoredToken(token: string, rememberMe: boolean): void {
  if (rememberMe) {
    localStorage.setItem("authToken", token);
    sessionStorage.removeItem("authToken");
  } else {
    sessionStorage.setItem("authToken", token);
    localStorage.removeItem("authToken");
  }

  notifyAuthChanged();
}

export function clearStoredToken(): void {
  localStorage.removeItem("authToken");
  sessionStorage.removeItem("authToken");
  notifyAuthChanged();
}

export function subscribeToAuthChanges(listener: () => void): () => void {
  if (typeof window === "undefined") {
    return () => undefined;
  }

  const wrappedListener: EventListener = () => {
    listener();
  };

  window.addEventListener(AUTH_CHANGED_EVENT, wrappedListener);

  return () => {
    window.removeEventListener(AUTH_CHANGED_EVENT, wrappedListener);
  };
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

export function buildAuthHeaders(): { Authorization: string; "X-User-Id": string } {
  const token = getStoredToken();
  const userId = getUserIdFromToken(token);

  if (!token || !userId) {
    throw new Error("Missing valid authentication token.");
  }

  return {
    Authorization: `Bearer ${token}`,
    "X-User-Id": userId,
  };
}

export function getUserIdFromToken(token: string | null): string | null {
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
    const payload = JSON.parse(atob(padded)) as { userId?: number | string };
    if (typeof payload.userId === "number") {
      return String(payload.userId);
    }
    return typeof payload.userId === "string" ? payload.userId : null;
  } catch {
    return null;
  }
}

