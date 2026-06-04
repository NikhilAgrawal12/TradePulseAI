import axios from "axios";
import { useEffect, useState } from "react";
import type { Stock } from "../types/stock";

const FEATURED_STOCKS_CACHE_KEY = "tradepulseai_featured_stocks_cache_v1";
const FEATURED_STOCKS_TIMESTAMP_KEY = "tradepulseai_featured_stocks_timestamp_v1";
const CACHE_TTL_MS = 24 * 60 * 60 * 1000; // 24 hours — aligns with daily 9 AM backend refresh

function isCacheStale(): boolean {
  try {
    const ts = localStorage.getItem(FEATURED_STOCKS_TIMESTAMP_KEY);
    if (!ts) return true;
    return Date.now() - Number(ts) > CACHE_TTL_MS;
  } catch {
    return true;
  }
}

function getCachedStocks(): Stock[] {
  try {
    const cached = localStorage.getItem(FEATURED_STOCKS_CACHE_KEY);
    if (cached) {
      const parsed = JSON.parse(cached) as Stock[];
      return Array.isArray(parsed) ? parsed : [];
    }
  } catch {
    // Ignore cache read errors
  }
  return [];
}

function saveCacheStocks(data: Stock[]): void {
  try {
    localStorage.setItem(FEATURED_STOCKS_CACHE_KEY, JSON.stringify(data));
    localStorage.setItem(FEATURED_STOCKS_TIMESTAMP_KEY, String(Date.now()));
  } catch {
    // Ignore cache write errors
  }
}

export function useFeaturedStocks() {
  const [featuredStocks, setFeaturedStocks] = useState<Stock[]>(() => {
    // Load from localStorage immediately on mount for instant display
    return getCachedStocks();
  });
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;

    const loadFeaturedStocks = async () => {
      // Always fetch fresh data from backend on page load if:
      // 1. Cache is stale (older than 24 hours) — picks up 9 AM backend refresh
      // 2. Cache is empty — first ever load
      // If cache is fresh, we still silently re-fetch to get latest but keep existing display
      const stale = isCacheStale();

      try {
        const response = await axios.get<Stock[]>("/api/stocks/featured");
        if (!mounted) return;
        const data = Array.isArray(response.data) ? response.data : [];
        if (data.length > 0) {
          setFeaturedStocks(data);
          saveCacheStocks(data);
        }
        setError(null);
      } catch {
        if (!mounted) return;
        // Keep cached data visible — don't show error if we have something to display
        if (stale) {
          // Cache is stale but backend unreachable — keep showing old data silently
        }
        setError(null);
      }
    };

    void loadFeaturedStocks();

    return () => {
      mounted = false;
    };
  }, []);

  return { featuredStocks, error };
}

