import { useEffect, useRef, useState } from "react";

const MASSIVE_DELAYED_WS_URL = "wss://delayed.massive.com/stocks";
const MASSIVE_API_KEY = import.meta.env.VITE_MASSIVE_API_KEY as string | undefined;
const AGGREGATE_CACHE_KEY = "tradepulseai_home_aggregate_cache_v1";
const MAX_CACHE_AGE_MS = 7 * 24 * 60 * 60 * 1000;

export type LiveAggregate = {
  previousClose: number | null;
  open: number;
  close: number;
  high: number;
  low: number;
  volume: number;
  vwap: number;
  timestampMs: number;
};

type AggregateCachePayload = {
  savedAt: number;
  data: Record<string, LiveAggregate>;
};

function normalizeSymbol(value: string | null | undefined): string | null {
  if (!value) {
    return null;
  }
  const normalized = value.trim().toUpperCase();
  return normalized.length > 0 ? normalized : null;
}

function loadAggregateCache(): Record<string, LiveAggregate> {
  try {
    const raw = localStorage.getItem(AGGREGATE_CACHE_KEY);
    if (!raw) {
      return {};
    }

    const parsed = JSON.parse(raw) as AggregateCachePayload;
    if (!parsed || typeof parsed !== "object" || typeof parsed.savedAt !== "number" || !parsed.data || typeof parsed.data !== "object") {
      return {};
    }

    if (Date.now() - parsed.savedAt > MAX_CACHE_AGE_MS) {
      return {};
    }

    const entries = Object.entries(parsed.data).filter(([symbol, value]) => {
      return (
        symbol.length > 0 &&
        value &&
        typeof value.open === "number" &&
        typeof value.close === "number" &&
        typeof value.high === "number" &&
        typeof value.low === "number" &&
        typeof value.volume === "number" &&
        typeof value.vwap === "number" &&
        typeof value.timestampMs === "number"
      );
    });

    return Object.fromEntries(entries);
  } catch {
    return {};
  }
}

export function useWebSocketPrices(symbols: string[]) {
  const [aggregateBySymbol, setAggregateBySymbol] = useState<Record<string, LiveAggregate>>(() => loadAggregateCache());
  const socketGenerationRef = useRef(0);
  const [socketRetryNonce, setSocketRetryNonce] = useState(0);

  const normalizedSymbols = symbols
    .map((symbol) => normalizeSymbol(symbol))
    .filter((symbol): symbol is string => Boolean(symbol));

  const subscriptionParams = normalizedSymbols.map((symbol) => `A.${symbol}`).join(",");

  // Save to cache whenever aggregates update
  useEffect(() => {
    try {
      const payload: AggregateCachePayload = {
        savedAt: Date.now(),
        data: aggregateBySymbol,
      };
      localStorage.setItem(AGGREGATE_CACHE_KEY, JSON.stringify(payload));
    } catch {
      // Ignore cache persistence failures.
    }
  }, [aggregateBySymbol]);

  useEffect(() => {
    if (!subscriptionParams) {
      return undefined;
    }

    if (!MASSIVE_API_KEY) {
      return undefined;
    }

    const connectionGeneration = ++socketGenerationRef.current;

    let closedByEffect = false;
    let reconnectScheduled = false;
    let reconnectTimeoutId: number | null = null;
    const socket = new WebSocket(MASSIVE_DELAYED_WS_URL);

    const scheduleReconnect = () => {
      if (closedByEffect || reconnectScheduled || socketGenerationRef.current !== connectionGeneration) {
        return;
      }
      reconnectScheduled = true;
      reconnectTimeoutId = window.setTimeout(() => {
        if (closedByEffect || socketGenerationRef.current !== connectionGeneration) {
          return;
        }
        setSocketRetryNonce((current) => current + 1);
      }, 1500);
    };

    const subscribeToVisibleSymbols = () => {
      if (closedByEffect || socketGenerationRef.current !== connectionGeneration) {
        return;
      }
      socket.send(JSON.stringify({ action: "subscribe", params: subscriptionParams }));
    };

    const handleMessage = (payload: unknown) => {
      if (!Array.isArray(payload)) {
        return;
      }

      for (const rawEvent of payload) {
        if (!rawEvent || typeof rawEvent !== "object") {
          continue;
        }

        const event = rawEvent as Record<string, unknown>;
        const eventType = typeof event.ev === "string" ? event.ev : "";

        if (eventType === "status") {
          const status = typeof event.status === "string" ? event.status : "";
          if (status === "connected") {
            socket.send(JSON.stringify({ action: "auth", params: MASSIVE_API_KEY }));
          } else if (status === "auth_success") {
            subscribeToVisibleSymbols();
          } else if (status === "auth_failed") {
            if (closedByEffect || socketGenerationRef.current !== connectionGeneration) {
              continue;
            }
            scheduleReconnect();
          }
          continue;
        }

        if (eventType === "A") {
          const symbol = normalizeSymbol(typeof event.sym === "string" ? event.sym : null);
          if (!symbol) {
            continue;
          }
          const open = typeof event.o === "number" ? event.o : null;
          const close = typeof event.c === "number" ? event.c : null;
          const high = typeof event.h === "number" ? event.h : close;
          const low = typeof event.l === "number" ? event.l : close;
          const volume = typeof event.v === "number" ? event.v : 0;
          const vwap = typeof event.vw === "number" ? event.vw : (close ?? 0);
          const timestamp = typeof event.e === "number" ? event.e : Date.now();

          if (open === null || close === null || high === null || low === null) {
            continue;
          }

          setAggregateBySymbol((prev) => {
            const previous = prev[symbol];
            return {
              ...prev,
              [symbol]: {
                previousClose: previous?.close ?? null,
                open,
                close,
                high,
                low,
                volume,
                vwap,
                timestampMs: timestamp,
              },
            };
          });
        }
      }
    };

    socket.onmessage = (event) => {
      if (closedByEffect || socketGenerationRef.current !== connectionGeneration) {
        return;
      }
      try {
        handleMessage(JSON.parse(event.data as string));
      } catch {
        scheduleReconnect();
      }
    };

    socket.onerror = () => {
      if (closedByEffect || socketGenerationRef.current !== connectionGeneration) {
        return;
      }
      scheduleReconnect();
    };

    socket.onclose = () => {
      if (!closedByEffect && socketGenerationRef.current === connectionGeneration) {
        scheduleReconnect();
      }
    };

    return () => {
      closedByEffect = true;
      if (reconnectTimeoutId !== null) {
        window.clearTimeout(reconnectTimeoutId);
      }
      if (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING) {
        socket.close();
      }
    };
  }, [socketRetryNonce, subscriptionParams]);

  return aggregateBySymbol;
}


