import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import {
  API_MARKET_STATUS_FALLBACK,
  getMarketSessionFromBackend,
  subscribeToMarketStatus,
  type SessionMeta,
} from "../utils/marketSession";

type MarketStatusContextValue = {
  sessionMeta: SessionMeta;
};

const MarketStatusContext = createContext<MarketStatusContextValue>({
  sessionMeta: API_MARKET_STATUS_FALLBACK,
});

export function MarketStatusProvider({ children }: { children: ReactNode }) {
  const [sessionMeta, setSessionMeta] = useState<SessionMeta>(API_MARKET_STATUS_FALLBACK);
  const isBootstrapInFlightRef = useRef(false);

  useEffect(() => {
    let cancelled = false;

    const updateFromBackend = async () => {
      if (isBootstrapInFlightRef.current) {
        return;
      }
      isBootstrapInFlightRef.current = true;

      try {
        const next = await getMarketSessionFromBackend();
        if (!cancelled) {
          setSessionMeta(next);
        }
      } finally {
        isBootstrapInFlightRef.current = false;
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


  const value = useMemo(() => ({ sessionMeta }), [sessionMeta]);
  return <MarketStatusContext.Provider value={value}>{children}</MarketStatusContext.Provider>;
}

export function useMarketStatus(): MarketStatusContextValue {
  return useContext(MarketStatusContext);
}

