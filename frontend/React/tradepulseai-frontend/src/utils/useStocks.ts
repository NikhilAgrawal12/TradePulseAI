import { useEffect, useState } from "react";
import axios from "axios";
import type { Stock } from "../types/stock";
import { fetchStocks } from "./stocksApi";

export function useStocks() {
  const [stocks, setStocks] = useState<Stock[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    const loadStocks = async () => {
      try {
        setLoading(true);
        const data = await fetchStocks();
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
        setStocks([]);
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

  return { stocks, loading, error };
}

