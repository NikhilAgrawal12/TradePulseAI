import { useEffect, useState } from "react";
import type { Stock } from "../types/stock";
import { toMoney } from "./money";

const STOCKS_STREAM_RECONNECT_MS = 3000;
const ALL_STOCKS_STREAM_QUERY = "__all__";

function normalizeStocks(rawStocks: Stock[]): Stock[] {
  return rawStocks.map((stock) => ({
    ...stock,
    price: stock.price == null ? null : toMoney(stock.price),
    changePercent: stock.changePercent == null ? null : toMoney(stock.changePercent),
    open: stock.open == null ? null : toMoney(stock.open),
    high: stock.high == null ? null : toMoney(stock.high),
    low: stock.low == null ? null : toMoney(stock.low),
    vwap: stock.vwap == null ? null : toMoney(stock.vwap),
  }));
}

export function useStocks() {
  const [stocks, setStocks] = useState<Stock[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    let eventSource: EventSource | null = null;
    let reconnectTimer: number | null = null;

    const handlePayload = (rawData: string) => {
      if (!mounted) {
        return;
      }

      try {
        const data = JSON.parse(rawData);
        const nextStocks = Array.isArray(data) ? normalizeStocks(data as Stock[]) : [];
        setStocks(nextStocks);
        setError(null);
      } catch {
        // Ignore malformed payloads and keep the previous snapshot.
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    };

    const connect = () => {
      if (!mounted) {
        return;
      }

      const streamUrl = `/api/stocks/stream/featured?query=${encodeURIComponent(ALL_STOCKS_STREAM_QUERY)}`;
      eventSource = new EventSource(streamUrl);

      eventSource.addEventListener("stocks", (event) => {
        handlePayload((event as MessageEvent).data);
      });

      eventSource.onmessage = (event) => {
        handlePayload(event.data);
      };

      eventSource.onerror = () => {
        if (eventSource) {
          eventSource.close();
          eventSource = null;
        }

        if (!mounted) {
          return;
        }

        setStocks((current) => {
          if (current.length === 0) {
            setError("Unable to load stocks right now. Please try again shortly.");
            setLoading(false);
          }
          return current;
        });

        reconnectTimer = window.setTimeout(connect, STOCKS_STREAM_RECONNECT_MS);
      };
    };

    connect();

    return () => {
      mounted = false;
      if (reconnectTimer != null) {
        window.clearTimeout(reconnectTimer);
      }
      if (eventSource) {
        eventSource.close();
      }
    };
  }, []);

  return { stocks, loading, error };
}
