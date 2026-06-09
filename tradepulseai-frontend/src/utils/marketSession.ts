/**
 * Returns the current US equity market session based on Eastern Time.
 *
 * Sessions (all times ET):
 *   Pre-Market  : 04:00 – 09:30
 *   Regular     : 09:30 – 16:00
 *   After-Hours : 16:00 – 20:00
 *   Closed      : 20:00 – 04:00
 */

export type MarketSession = "pre-market" | "regular" | "after-hours" | "closed";

export type SessionMeta = {
  session: MarketSession;
  label: string;
  cssClass: string;
};

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

