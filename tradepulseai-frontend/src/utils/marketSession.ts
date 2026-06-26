/**
 * Returns the current US equity market session based on Eastern Time.
 *
 * Sessions (all times ET):
 *   Pre-Market  : 04:00 – 09:30
 *   Regular     : 09:30 – 16:00
 *   After-Hours : 16:00 – 20:00
 *   Closed      : 20:00 – 04:00
 */

export type MarketSession = "pre-market" | "regular" | "after-hours" | "closed" | "unknown";

export type SessionMeta = {
  session: MarketSession;
  label: string;
  cssClass: string;
};

type BackendMarketStatusResponse = {
  session?: string;
  label?: string;
  cssClass?: string;
  stale?: boolean;
};

const MARKET_STATUS_FETCH_TIMEOUT_MS = 1500;
const MARKET_STATUS_STREAM_RECONNECT_MS = 3000;

export function getMarketSession(now: Date = new Date()): SessionMeta {
  // Convert current time to US/Eastern fractional hour
  const etFormatter = new Intl.DateTimeFormat("en-US", {
    timeZone: "America/New_York",
    hour: "numeric",
    minute: "numeric",
    hour12: false,
  });

  const parts = etFormatter.formatToParts(now);
  const hourPart = parts.find((p) => p.type === "hour")?.value ?? "0";
  const minutePart = parts.find((p) => p.type === "minute")?.value ?? "0";

  const hour = parseInt(hourPart, 10);
  const minute = parseInt(minutePart, 10);
  const totalMinutes = hour * 60 + minute;

  // Boundaries in minutes-since-midnight
  const PRE_MARKET_START  = 4  * 60;       // 04:00
  const REGULAR_START     = 9  * 60 + 30;  // 09:30
  const AFTER_HOURS_START = 16 * 60;       // 16:00
  const AFTER_HOURS_END   = 20 * 60;       // 20:00

  if (totalMinutes >= PRE_MARKET_START && totalMinutes < REGULAR_START) {
    return { session: "pre-market",   label: "Pre-Market",   cssClass: "session-pre-market"   };
  }
  if (totalMinutes >= REGULAR_START && totalMinutes < AFTER_HOURS_START) {
    return { session: "regular",      label: "Market Open",  cssClass: "session-regular"       };
  }
  if (totalMinutes >= AFTER_HOURS_START && totalMinutes < AFTER_HOURS_END) {
    return { session: "after-hours",  label: "After-Hours",  cssClass: "session-after-hours"   };
  }
  return   { session: "closed",       label: "Market Closed", cssClass: "session-closed"       };
}

function asMarketSession(value: string | undefined): MarketSession | null {
  if (value === "pre-market" || value === "regular" || value === "after-hours" || value === "closed") {
    return value;
  }
  return null;
}

function toSessionMeta(payload: BackendMarketStatusResponse | null | undefined): SessionMeta {
  if (!payload) {
    return getMarketSession();
  }

  const session = asMarketSession(payload?.session);
  if (!session || payload?.stale === true) {
    return getMarketSession();
  }

  const label = payload.label;
  const cssClass = payload.cssClass;

  return {
    session,
    label: typeof label === "string" && label.trim().length > 0 ? label : "Market Status",
    cssClass: typeof cssClass === "string" && cssClass.trim().length > 0 ? cssClass : "session-closed",
  };
}

export async function getMarketSessionFromBackend(): Promise<SessionMeta> {
  const controller = new AbortController();
  const timeoutId = window.setTimeout(() => controller.abort(), MARKET_STATUS_FETCH_TIMEOUT_MS);
  try {
    const response = await fetch("/api/stocks/market-status", { signal: controller.signal });
    if (!response.ok) {
      return getMarketSession();
    }

    return toSessionMeta((await response.json()) as BackendMarketStatusResponse);
  } catch {
    return getMarketSession();
  } finally {
    window.clearTimeout(timeoutId);
  }
}

export function subscribeToMarketStatus(onUpdate: (next: SessionMeta) => void): () => void {
  let cancelled = false;
  let eventSource: EventSource | null = null;
  let reconnectTimer: number | null = null;

  const handleMessage = (raw: string) => {
    if (cancelled) {
      return;
    }
    try {
      const payload = JSON.parse(raw) as BackendMarketStatusResponse;
      onUpdate(toSessionMeta(payload));
    } catch {
      // Ignore malformed stream payloads and wait for next message.
    }
  };

  const connect = () => {
    if (cancelled) {
      return;
    }

    eventSource = new EventSource("/api/stocks/stream/market-status");
    eventSource.addEventListener("market-status", (event) => {
      handleMessage((event as MessageEvent).data);
    });
    eventSource.onmessage = (event) => {
      handleMessage(event.data);
    };
    eventSource.onerror = () => {
      if (eventSource) {
        eventSource.close();
        eventSource = null;
      }
      if (cancelled) {
        return;
      }
      reconnectTimer = window.setTimeout(connect, MARKET_STATUS_STREAM_RECONNECT_MS);
    };
  };

  connect();

  return () => {
    cancelled = true;
    if (reconnectTimer != null) {
      window.clearTimeout(reconnectTimer);
    }
    if (eventSource) {
      eventSource.close();
    }
  };
}

