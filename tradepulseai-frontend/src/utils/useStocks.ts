import { useEffect, useState } from "react";
import type { Stock } from "../types/stock";

export function useStocks() {
  const [stocks] = useState<Stock[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setError("Stock data feed is disabled while the new premium data flow is being redesigned.");
    setLoading(false);
    return undefined;
  }, []);

  return { stocks, loading, error };
}
