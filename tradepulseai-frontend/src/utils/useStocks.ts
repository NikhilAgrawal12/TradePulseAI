import axios from "axios";
import { useEffect, useState } from "react";
import type { Stock } from "../types/stock";

export function useStocks() {
  const [stocks, setStocks] = useState<Stock[]>([]);
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
        setStocks(Array.isArray(response.data) ? response.data : []);
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

    return () => {
      mounted = false;
    };
  }, []);

  return { stocks, loading, error };
}
