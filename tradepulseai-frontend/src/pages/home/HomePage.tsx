import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { Header } from "../../components/Header.tsx";
import { useCart } from "../../context/CartContext";
import { useWatchlist } from "../../context/WatchlistContext";
import type { Stock } from "../../types/stock";
import { isUserAuthenticated } from "../../utils/auth";
import { useStocks } from "../../utils/useStocks";
import "./HomePage.css";

const MAX_VISIBLE_STOCKS = 50;
const MASSIVE_DELAYED_WS_URL = "wss://delayed.massive.com/stocks";
const IMPORTANT_STOCKS_ORDER = ["GOOGL", "GOOG", "META", "AAPL", "NVDA", "MSFT", "AMZN", "TSLA"];
const IMPORTANT_STOCK_PRIORITY = new Map(IMPORTANT_STOCKS_ORDER.map((symbol, index) => [symbol, index]));
const MASSIVE_API_KEY = import.meta.env.VITE_MASSIVE_API_KEY as string | undefined;

type FeedStatus = "idle" | "connecting" | "live" | "error";

type LiveAggregate = {
  previousClose: number | null;
  open: number;
  close: number;
  high: number;
  low: number;
  volume: number;
  vwap: number;
  timestampMs: number;
};

function normalizeSymbol(value: string | null | undefined): string | null {
  if (!value) {
    return null;
  }
  const normalized = value.trim().toUpperCase();
  return normalized.length > 0 ? normalized : null;
}

function formatRealtimeTime(timestampMs: number | null | undefined): string {
  if (!timestampMs) {
    return "--";
  }
  return new Date(timestampMs).toLocaleTimeString();
}

function calculateChangePercent(previousClose: number | null, close: number): number {
  if (previousClose === null || previousClose <= 0) {
    return 0;
  }
  return ((close - previousClose) / previousClose) * 100;
}

function toRankedStocks(stocks: Stock[]): Stock[] {
  return [...stocks].sort((a, b) => {
    const symbolA = a.symbol.trim().toUpperCase();
    const symbolB = b.symbol.trim().toUpperCase();
    const priorityA = IMPORTANT_STOCK_PRIORITY.get(symbolA) ?? Number.MAX_SAFE_INTEGER;
    const priorityB = IMPORTANT_STOCK_PRIORITY.get(symbolB) ?? Number.MAX_SAFE_INTEGER;

    if (priorityA !== priorityB) {
      return priorityA - priorityB;
    }

    return symbolA.localeCompare(symbolB);
  });
}

export function HomePage() {
  const { addToCart } = useCart();
  const { addToWatchlist } = useWatchlist();
  const { stocks, loading, error } = useStocks();
  const [searchTerm, setSearchTerm] = useState("");
  const [feedStatus, setFeedStatus] = useState<FeedStatus>("idle");
  const [feedError, setFeedError] = useState<string | null>(null);
  const [aggregateBySymbol, setAggregateBySymbol] = useState<Record<string, LiveAggregate>>({});

  useEffect(() => {
    document.title = "Home | TradePulseAI";
  }, []);

  const isLoggedIn = useMemo(() => isUserAuthenticated(), []);

  const prioritizedStocks = useMemo(
    () => toRankedStocks(stocks.filter((stock) => typeof stock.symbol === "string" && stock.symbol.trim().length > 0)),
    [stocks],
  );

  const filteredStocks = useMemo(() => {
    const query = searchTerm.trim().toLowerCase();
    if (!query) {
      return prioritizedStocks.slice(0, MAX_VISIBLE_STOCKS);
    }

    return prioritizedStocks
      .filter((stock) => {
        const symbol = stock.symbol.toLowerCase();
        const name = (stock.name ?? "").toLowerCase();
        return symbol.includes(query) || name.includes(query);
      })
      .slice(0, MAX_VISIBLE_STOCKS);
  }, [prioritizedStocks, searchTerm]);

  const visibleSymbols = useMemo(
    () =>
      filteredStocks
        .map((stock) => normalizeSymbol(stock.symbol))
        .filter((symbol): symbol is string => Boolean(symbol)),
    [filteredStocks],
  );

  const visibleSymbolsKey = useMemo(() => visibleSymbols.join(","), [visibleSymbols]);

  useEffect(() => {
    if (visibleSymbols.length === 0) {
      setFeedStatus("idle");
      setFeedError(null);
      return undefined;
    }

    if (!MASSIVE_API_KEY) {
      setFeedStatus("error");
      setFeedError("Set VITE_MASSIVE_API_KEY in frontend env to stream stock aggregate data.");
      return undefined;
    }

    setFeedStatus("connecting");
    setFeedError(null);

    let closedByEffect = false;
    const socket = new WebSocket(MASSIVE_DELAYED_WS_URL);

    const subscribeToVisibleSymbols = () => {
      const params = visibleSymbols.map((symbol) => `A.${symbol}`).join(",");

      socket.send(JSON.stringify({ action: "subscribe", params }));
      setFeedStatus("live");
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
            setFeedStatus("error");
            setFeedError("Massive authentication failed. Check VITE_MASSIVE_API_KEY.");
          }
          continue;
        }

        if (eventType === "A") {
          const symbol = normalizeSymbol(typeof event.sym === "string" ? event.sym : null);
          const open = typeof event.o === "number" ? event.o : null;
          const close = typeof event.c === "number" ? event.c : null;
          const high = typeof event.h === "number" ? event.h : close;
          const low = typeof event.l === "number" ? event.l : close;
          const volume = typeof event.v === "number" ? event.v : 0;
          const vwap = typeof event.vw === "number" ? event.vw : (close ?? 0);
          const timestamp = typeof event.e === "number" ? event.e : Date.now();

          if (!symbol || open === null || close === null || high === null || low === null) {
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
      try {
        handleMessage(JSON.parse(event.data as string));
      } catch {
        setFeedStatus("error");
        setFeedError("Received invalid message format from market feed.");
      }
    };

    socket.onerror = () => {
      setFeedStatus("error");
      setFeedError("Market feed connection failed.");
    };

    socket.onclose = () => {
      if (!closedByEffect) {
        setFeedStatus("error");
        setFeedError("Market feed disconnected. Refresh to reconnect.");
      }
    };

    return () => {
      closedByEffect = true;
      socket.close();
    };
  }, [visibleSymbols, visibleSymbolsKey]);

  const feedStatusLabel = useMemo(() => {
    if (feedStatus === "live") {
      return "Market feed connected";
    }
    if (feedStatus === "connecting") {
      return "Connecting market feed...";
    }
    if (feedStatus === "error") {
      return "Market feed error";
    }
    return "Market feed idle";
  }, [feedStatus]);

  return (
    <>
      <Header />

      <main className="home-page">
        {!isLoggedIn && (
          <section className="home-hero">
            <p className="home-eyebrow">Market pulse in real time</p>
            <h1>Track stocks and trade with confidence.</h1>
            <p className="home-subtitle">Browse available stocks and manage your portfolio.</p>
            <div className="home-hero-actions">
              <Link to="/login" className="home-btn-primary">Sign in</Link>
              <Link to="/registration" className="home-btn-secondary">Create account</Link>
            </div>
          </section>
        )}

        <div className="home-section-shell">
          <section className="home-stocks-section" aria-labelledby="stocks-heading">
            <div className="stocks-top-row">
              <h2 id="stocks-heading">Available Stocks</h2>
              <p>{loading ? "Loading..." : `Showing ${filteredStocks.length} of ${prioritizedStocks.length}`}</p>
            </div>

            <div className="stocks-feed-row">
              <span className={`feed-pill ${feedStatus}`}>{feedStatusLabel}</span>
              {feedError ? <span className="feed-error-inline">{feedError}</span> : null}
            </div>

            <div className="search-wrapper">
              <label className="search-label" htmlFor="stock-search-input">Search symbol or company</label>
              <input
                id="stock-search-input"
                className="stock-search-input"
                placeholder="Try AAPL, MSFT, NVIDIA..."
                value={searchTerm}
                onChange={(event) => setSearchTerm(event.target.value)}
                autoComplete="off"
              />
            </div>

            {error ? (
              <p className="error-message">{error}</p>
            ) : loading ? (
              <p className="loading-message">Loading stocks from backend...</p>
            ) : filteredStocks.length === 0 ? (
              <p className="no-data-message">No stocks available at the moment. Check back later.</p>
            ) : (
              <div className="stocks-grid">
                {filteredStocks.map((stock) => {
                  const symbol = stock.symbol.trim().toUpperCase();
                  const aggregate = aggregateBySymbol[symbol];
                  const stickerPrice = aggregate?.close ?? stock.price ?? 0;
                  const change = aggregate
                    ? calculateChangePercent(aggregate.previousClose, aggregate.close)
                    : (stock.changePercent ?? 0);
                  const isPositive = change >= 0;

                  return (
                    <article className="stock-card" key={stock.id}>
                      <div className="stock-card-header">
                        <div>
                          <h3>{symbol}</h3>
                          <p>{stock.name ?? "N/A"}</p>
                        </div>
                        <span className={`recommendation-badge ${stock.active ? "active" : "inactive"}`}>{stock.active ? "Active" : "Inactive"}</span>
                      </div>

                      <div className="quote-price-row">
                        <strong>${stickerPrice.toFixed(2)}</strong>
                        <span className={isPositive ? "price-up" : "price-down"}>{isPositive ? "+" : ""}{change.toFixed(2)}%</span>
                      </div>

                      <div className="aggregate-block">
                        <p className="block-title">Live Snapshot</p>
                        <div className="aggregate-grid">
                          <p className="metric-item"><span>Open</span><strong className="metric-value">{aggregate ? `$${aggregate.open.toFixed(2)}` : "--"}</strong></p>
                          <p className="metric-item"><span>High</span><strong className="metric-value">{aggregate ? `$${aggregate.high.toFixed(2)}` : "--"}</strong></p>
                          <p className="metric-item"><span>Low</span><strong className="metric-value">{aggregate ? `$${aggregate.low.toFixed(2)}` : "--"}</strong></p>
                          <p className="metric-item"><span>Volume</span><strong className="metric-value">{aggregate ? aggregate.volume.toLocaleString() : "--"}</strong></p>
                          <p className="metric-item"><span>VWAP</span><strong className="metric-value">{aggregate ? `$${aggregate.vwap.toFixed(2)}` : "--"}</strong></p>
                          <p className="metric-item metric-item-full"><span>Updated</span><strong className="metric-value">{formatRealtimeTime(aggregate?.timestampMs)}</strong></p>
                        </div>
                      </div>

                      <p className="stock-rating">Baseline update: {stock.lastUpdated ? new Date(stock.lastUpdated).toLocaleString() : "N/A"}</p>

                      <div className="stock-card-actions">
                        <button
                          className="stock-add-to-cart-btn"
                          onClick={() => void addToCart(stock.id, stock.symbol, stickerPrice, 1)}
                        >
                          🛒 Add to cart
                        </button>
                        <button
                          className="stock-add-to-watchlist-btn"
                          onClick={() => void addToWatchlist(stock.id, 1)}
                        >
                          ⭐ Add to watchlist
                        </button>
                      </div>
                    </article>
                  );
                })}
              </div>
            )}
          </section>
        </div>
      </main>
    </>
  );
}