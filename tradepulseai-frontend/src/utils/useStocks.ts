import axios from "axios";
import { useEffect, useState } from "react";
import type { Stock } from "../types/stock";
const STOCKS_REFRESH_MS = 2000;

export function useStocks() {
  const [stocks, setStocks] = useState<Stock[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    let intervalId: number | null = null;

    const loadStocks = async () => {
      try {
        const response = await axios.get<Stock[]>("/api/stocks");
        if (!mounted) {
          return;
        }
        const data = Array.isArray(response.data) ? response.data : [];
        setStocks(data);
        setError(null);
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
    intervalId = window.setInterval(() => {
      void loadStocks();
    }, STOCKS_REFRESH_MS);

    return () => {
      mounted = false;
      if (intervalId !== null) {
        window.clearInterval(intervalId);
      }
    };
  }, []);

  return { stocks, loading, error };
}
