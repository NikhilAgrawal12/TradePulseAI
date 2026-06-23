import axios from "axios";
import { useEffect, useRef, useState } from "react";
import type { Stock } from "../types/stock";
import { toMoney } from "./money";

const LAST_STOCKS_CACHE_KEY = "tradepulseai:last-streamed-stocks";

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

function readCachedStocks(): Stock[] {
  try {
    const raw = window.localStorage.getItem(LAST_STOCKS_CACHE_KEY);
    if (!raw) {
      return [];
    }
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? normalizeStocks(parsed as Stock[]) : [];
  } catch {
    return [];
  }
}

function writeCachedStocks(stocks: Stock[]): void {
  try {
    window.localStorage.setItem(LAST_STOCKS_CACHE_KEY, JSON.stringify(normalizeStocks(stocks)));
  } catch {
    // Ignore localStorage failures silently.
  }
}

/**
 * Hook that combines:
 * 1. Server-Sent Events for featured stocks (no search)
 * 2. On-demand search endpoint when user searches
 *
 * This avoids constantly sending all 800 stocks - only featured stocks are streamed.
 * Search results are fetched on-demand from the 800-stock cache.
 */
export function useStreamedStocks() {
  const [stocks, setStocks] = useState<Stock[]>(() => readCachedStocks());
  const hadCachedStocksAtBoot = useRef(stocks.length > 0);
  const [error, setError] = useState<string | null>(null);
  const [searchTerm, setSearchTerm] = useState("");
  const stocksCount = stocks.length;

  useEffect(() => {
    let mounted = true;

    const loadInitialFeaturedStocks = async () => {
      if (searchTerm.trim()) {
        return;
      }

      // Keep boot-time cache visible until SSE pushes fresh ticks.
      if (hadCachedStocksAtBoot.current) {
        return;
      }

      try {
        const response = await axios.get<Stock[]>("/api/stocks/featured");
        if (!mounted) return;

        const data = Array.isArray(response.data) ? normalizeStocks(response.data) : [];
        if (data.length > 0) {
          setStocks(data);
          writeCachedStocks(data);
        }
      } catch {
        // Keep startup silent. SSE will connect shortly and replace data when available.
      }
    };

    void loadInitialFeaturedStocks();

    return () => {
      mounted = false;
    };
  }, [searchTerm]);

  // SSE connection for featured stocks
  useEffect(() => {
    let mounted = true;
    let eventSource: EventSource | null = null;

    const handleStocksPayload = (rawData: string) => {
      if (!mounted) return;
      try {
        const data = JSON.parse(rawData);
        const nextStocks = Array.isArray(data) ? normalizeStocks(data as Stock[]) : [];

        setStocks(nextStocks);
        if (!searchTerm.trim()) {
          writeCachedStocks(nextStocks);
        }
        setError(null);
      } catch (parseError) {
        console.error("Failed to parse stocks data:", parseError);
      }
    };

    const connectStream = () => {
      try {
        const query = searchTerm.trim();
        const streamUrl = query
          ? `/api/stocks/stream/featured?query=${encodeURIComponent(query)}`
          : "/api/stocks/stream/featured";

        eventSource = new EventSource(streamUrl);

        eventSource.addEventListener("stocks", (event) => {
          handleStocksPayload(event.data);
        });

        // Fallback for proxies/clients that forward SSE payloads as default message events.
        eventSource.onmessage = (event) => {
          handleStocksPayload(event.data);
        };


        eventSource.onerror = () => {
          if (mounted && stocksCount === 0) {
            setError("Unable to load stock data right now.");
          }
          if (eventSource) {
            eventSource.close();
            eventSource = null;
          }
          // Auto-reconnect after 3 seconds
          setTimeout(() => {
            if (mounted) {
              connectStream();
            }
          }, 3000);
        };
      } catch {
        if (mounted && stocksCount === 0) {
          setError("Unable to load stock data right now.");
        }
      }
    };

    connectStream();

    return () => {
      mounted = false;
      if (eventSource) {
        eventSource.close();
      }
    };
  }, [searchTerm, stocksCount]);

  return { stocks, error, searchTerm, setSearchTerm };
}



