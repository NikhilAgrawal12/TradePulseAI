import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import {
  API_MARKET_STATUS_FALLBACK,
  getMarketSessionFromBackend,
  MARKET_STATUS_MAX_AGE_MS,
  subscribeToMarketStatus,
  type SessionMeta,
} from "../utils/marketSession";

type MarketStatusContextValue = {
  sessionMeta: SessionMeta;
};

const LAST_MARKET_STATUS_CACHE_KEY = "tradepulseai:last-market-status";

const MarketStatusContext = createContext<MarketStatusContextValue>({
  sessionMeta: API_MARKET_STATUS_FALLBACK,
});

function isFresh(meta: SessionMeta): boolean {
  return meta.lastUpdatedMs !== null && Date.now() - meta.lastUpdatedMs <= MARKET_STATUS_MAX_AGE_MS;
}

function readCachedSession(): SessionMeta | null {
  try {
    const raw = window.localStorage.getItem(LAST_MARKET_STATUS_CACHE_KEY);
    if (!raw) {
      return null;
    }

    const parsed = JSON.parse(raw) as SessionMeta;
    if (!parsed || typeof parsed !== "object") {
      return null;
    }

    const candidate: SessionMeta = {
      session: parsed.session,
      label: parsed.label,
      cssClass: parsed.cssClass,
      lastUpdatedMs: typeof parsed.lastUpdatedMs === "number" ? parsed.lastUpdatedMs : null,
    };

    return isFresh(candidate) ? candidate : null;
  } catch {
    return null;
  }
}

function writeCachedSession(meta: SessionMeta): void {
  try {
    if (isFresh(meta)) {
      window.localStorage.setItem(LAST_MARKET_STATUS_CACHE_KEY, JSON.stringify(meta));
      return;
    }
    window.localStorage.removeItem(LAST_MARKET_STATUS_CACHE_KEY);
  } catch {
    // Ignore storage failures and continue with in-memory state.
  }
}

export function MarketStatusProvider({ children }: { children: ReactNode }) {
  const [sessionMeta, setSessionMeta] = useState<SessionMeta>(() => readCachedSession() ?? API_MARKET_STATUS_FALLBACK);

  useEffect(() => {
    let cancelled = false;

    const updateFromBackend = async () => {
      const next = await getMarketSessionFromBackend();
      if (!cancelled) {
        setSessionMeta(next);
      }
    };

    void updateFromBackend();

    const unsubscribe = subscribeToMarketStatus((next) => {
      if (!cancelled) {
        setSessionMeta(next);
      }
    });

    const handleFocus = () => {
      void updateFromBackend();
    };

    const handleVisibilityChange = () => {
      if (document.visibilityState === "visible") {
        void updateFromBackend();
      }
    };

    window.addEventListener("focus", handleFocus);
    document.addEventListener("visibilitychange", handleVisibilityChange);

    return () => {
      cancelled = true;
      unsubscribe();
      window.removeEventListener("focus", handleFocus);
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, []);

  useEffect(() => {
    writeCachedSession(sessionMeta);
  }, [sessionMeta]);

  const value = useMemo(() => ({ sessionMeta }), [sessionMeta]);
  return <MarketStatusContext.Provider value={value}>{children}</MarketStatusContext.Provider>;
}

export function useMarketStatus(): MarketStatusContextValue {
  return useContext(MarketStatusContext);
}

