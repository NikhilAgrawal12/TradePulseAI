import axios from "axios";
import { useEffect, useState } from "react";
import type { Stock } from "../types/stock";

const STOCKS_CACHE_KEY = "tradepulseai_stocks_cache";

export function useStocks() {
  const [stocks, setStocks] = useState<Stock[]>(() => {
    // Load from cache immediately on mount
    try {
      const cached = localStorage.getItem(STOCKS_CACHE_KEY);
      if (cached) {
        const parsed = JSON.parse(cached) as Stock[];
        return Array.isArray(parsed) ? parsed : [];
      }
    } catch {
      // Ignore cache errors
    }
    return [];
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;

    const loadStocks = async () => {
      try {
        const response = await axios.get<Stock[]>("/api/stocks");
        if (!mounted) {
          return;
        }
        const data = Array.isArray(response.data) ? response.data : [];
        setStocks(data);
        setError(null);
        // Cache the stocks
        try {
          localStorage.setItem(STOCKS_CACHE_KEY, JSON.stringify(data));
        } catch {
          // Ignore cache write errors
        }
      } catch {
        if (!mounted) {
          return;
        }
        setError("Unable to load stocks right now. Please try again shortly.");
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    };

    void loadStocks();

    return () => {
      mounted = false;
    };
  }, []);

  return { stocks, loading, error };
}
