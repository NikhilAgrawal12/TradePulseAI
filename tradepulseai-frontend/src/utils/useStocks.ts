import { useEffect, useState } from "react";
import axios from "axios";
import type { Stock } from "../types/stock";
import { fetchStocks } from "./stocksApi";

const STOCKS_CACHE_KEY = "tradepulseai:stocks-cache";
const DEFAULT_POLL_INTERVAL_MS = 5_000;

type StocksCache = {
  data: Stock[];
  cachedAt: number;
};

let memoryCache: StocksCache | null = null;
let inFlightRequest: Promise<Stock[]> | null = null;

function getPollIntervalMs() {
  const configured = Number(import.meta.env.VITE_STOCKS_POLL_INTERVAL_MS);
  if (Number.isFinite(configured) && configured > 0) {
    return configured;
  }

  return DEFAULT_POLL_INTERVAL_MS;
}

function persistCache(cache: StocksCache) {
  memoryCache = cache;

  if (typeof window === "undefined") {
    return;
  }

  try {
    window.sessionStorage.setItem(STOCKS_CACHE_KEY, JSON.stringify(cache));
  } catch {
    // Ignore storage errors and continue with in-memory cache.
  }
}

function readSessionCache(): StocksCache | null {
  if (typeof window === "undefined") {
    return null;
  }

  try {
    const raw = window.sessionStorage.getItem(STOCKS_CACHE_KEY);
    if (!raw) {
      return null;
    }

    const parsed = JSON.parse(raw) as Partial<StocksCache>;
    if (!Array.isArray(parsed.data) || typeof parsed.cachedAt !== "number") {
      return null;
    }

    return {
      data: parsed.data as Stock[],
      cachedAt: parsed.cachedAt,
    };
  } catch {
    return null;
  }
}

function getCachedStocks() {
  if (memoryCache) {
    return memoryCache;
  }

  const sessionCache = readSessionCache();
  if (sessionCache) {
    memoryCache = sessionCache;
  }

  return memoryCache;
}

async function fetchAndCacheStocks() {
  if (inFlightRequest) {
    return inFlightRequest;
  }

  inFlightRequest = fetchStocks()
    .then((data) => {
      persistCache({ data, cachedAt: Date.now() });
      return data;
    })
    .finally(() => {
      inFlightRequest = null;
    });

  return inFlightRequest;
}

export function useStocks() {
  const [stocks, setStocks] = useState<Stock[]>(() => getCachedStocks()?.data ?? []);
  const [loading, setLoading] = useState(() => getCachedStocks() == null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;
    const pollIntervalMs = getPollIntervalMs();
    let pollTimer: ReturnType<typeof setInterval> | null = null;

    const loadStocks = async () => {
      const cache = getCachedStocks();
      if (cache && cache.data.length > 0) {
        setStocks(cache.data);
        setLoading(false);
      } else {
        setLoading(true);
      }

      try {
        const data = await fetchAndCacheStocks();
        if (!isMounted) {
          return;
        }
        setStocks(data);
        setError(null);
      } catch (err) {
        if (!isMounted) {
          return;
        }

        console.error("[useStocks] Error fetching stocks:", err);
        if (axios.isAxiosError(err) && typeof err.response?.data?.message === "string") {
          setError(err.response.data.message);
        } else {
          setError("Unable to load stocks right now.");
        }

        const fallback = getCachedStocks();
        setStocks(fallback?.data ?? []);
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    };

    const refreshStocks = async () => {
      try {
        const data = await fetchAndCacheStocks();
        if (!isMounted) {
          return;
        }

        setStocks(data);
        setError(null);
      } catch (err) {
        if (!isMounted) {
          return;
        }

        if (axios.isAxiosError(err) && typeof err.response?.data?.message === "string") {
          setError(err.response.data.message);
        }
      }
    };

    const handleForegroundRefresh = () => {
      void refreshStocks();
    };

    const handleVisibilityRefresh = () => {
      if (document.visibilityState === "visible") {
        void refreshStocks();
      }
    };

    void loadStocks();

    pollTimer = setInterval(() => {
      void refreshStocks();
    }, pollIntervalMs);

    window.addEventListener("focus", handleForegroundRefresh);
    window.addEventListener("online", handleForegroundRefresh);
    document.addEventListener("visibilitychange", handleVisibilityRefresh);

    return () => {
      isMounted = false;
      if (pollTimer) {
        clearInterval(pollTimer);
      }
      window.removeEventListener("focus", handleForegroundRefresh);
      window.removeEventListener("online", handleForegroundRefresh);
      document.removeEventListener("visibilitychange", handleVisibilityRefresh);
    };
  }, []);

  return { stocks, loading, error };
}
