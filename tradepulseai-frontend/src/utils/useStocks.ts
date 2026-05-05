import { useEffect, useState } from "react";
import axios from "axios";
import type { Stock } from "../types/stock";
import type { StockLiveEvent } from "../types/stockLive";
import { fetchStocks } from "./stocksApi";
import { connectStockLiveFeed } from "./stockLiveSocket";

const STOCKS_CACHE_KEY = "tradepulseai:stocks-cache";
const STOCKS_CACHE_TTL_MS = 60_000;

type StocksCache = {
  data: Stock[];
  cachedAt: number;
};

let memoryCache: StocksCache | null = null;
let inFlightRequest: Promise<Stock[]> | null = null;

function isCacheFresh(cache: StocksCache) {
  return Date.now() - cache.cachedAt < STOCKS_CACHE_TTL_MS;
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

function mergeLiveEvent(stocks: Stock[], event: StockLiveEvent) {
  let touched = false;
  const merged = stocks.map((stock) => {
    if (stock.symbol !== event.symbol) {
      return stock;
    }

    touched = true;
    return {
      ...stock,
      price: event.price,
      changePercent: event.changePercent,
      volume: event.volume,
      lastUpdated: event.marketTimestamp,
      source: event.source,
    };
  });

  return { merged, touched };
}

export function useStocks() {
  const [stocks, setStocks] = useState<Stock[]>(() => getCachedStocks()?.data ?? []);
  const [loading, setLoading] = useState(() => getCachedStocks() == null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    const loadStocks = async () => {
      const cache = getCachedStocks();
      if (cache && cache.data.length > 0) {
        setStocks(cache.data);
        setLoading(false);

        if (isCacheFresh(cache)) {
          return;
        }
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

    void loadStocks();

    return () => {
      isMounted = false;
    };
  }, []);

  useEffect(() => {
    const disconnect = connectStockLiveFeed((event) => {
      setStocks((previous) => {
        if (previous.length === 0) {
          return previous;
        }

        const { merged, touched } = mergeLiveEvent(previous, event);
        if (!touched) {
          return previous;
        }

        persistCache({ data: merged, cachedAt: Date.now() });
        return merged;
      });
    });

    return () => {
      disconnect();
    };
  }, []);

  return { stocks, loading, error };
}
