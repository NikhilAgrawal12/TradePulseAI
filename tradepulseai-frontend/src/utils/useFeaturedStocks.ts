import axios from "axios";
import { useEffect, useState } from "react";
import type { Stock } from "../types/stock";

export function useFeaturedStocks() {
  const [featuredStocks, setFeaturedStocks] = useState<Stock[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;

    const loadFeaturedStocks = async () => {
      try {
        const response = await axios.get<Stock[]>("/api/stocks/featured");
        if (!mounted) return;
        const data = Array.isArray(response.data) ? response.data : [];
        setFeaturedStocks(data);
        setError(null);
      } catch {
        if (!mounted) return;
        setError("Unable to load featured stocks right now.");
      }
    };

    void loadFeaturedStocks();

    return () => {
      mounted = false;
    };
  }, []);

  return { featuredStocks, error };
}

