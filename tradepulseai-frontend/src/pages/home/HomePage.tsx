import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { Header } from "../../components/Header.tsx";
import { useCart } from "../../context/CartContext";
import { useWatchlist } from "../../context/WatchlistContext";
import type { Stock } from "../../types/stock";
import { isUserAuthenticated } from "../../utils/auth";
import { useFeaturedStocks } from "../../utils/useFeaturedStocks";
import { useWebSocketPrices } from "../../utils/useWebSocketPrices";
import { useStocks } from "../../utils/useStocks";
import "./HomePage.css";

const MAX_VISIBLE_STOCKS = 50;
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

export function HomePage() {
  const { addToCart } = useCart();
  const { addToWatchlist } = useWatchlist();
  const { stocks: fetchedStocks, error } = useStocks();
  const { featuredStocks } = useFeaturedStocks();
  const [searchTerm, setSearchTerm] = useState("");

  // Indexed map of all 800 backend stocks for fast search lookups
  const fetchedBySymbol = useMemo(() => {
    return new Map(
      fetchedStocks
        .map((stock) => {
          const normalized = normalizeSymbol(stock.symbol);
          return normalized ? [normalized, stock] as const : null;
        })
        .filter((entry): entry is readonly [string, Stock] => Boolean(entry)),
    );
  }, [fetchedStocks]);

  // Default view: use featured stocks from backend (cached locally for instant load).
  // Overlay latest market data from the all-stocks fetch when available.
  const defaultStocks = useMemo(() => {
    return featuredStocks.map((featured) => {
      const sym = normalizeSymbol(featured.symbol);
      const fresh = sym ? fetchedBySymbol.get(sym) : undefined;
      if (!fresh) return featured;
      return {
        ...featured,
        name: fresh.name ?? featured.name,
        price: fresh.price ?? featured.price,
        changePercent: fresh.changePercent ?? featured.changePercent,
        active: fresh.active ?? featured.active,
        lastUpdated: fresh.lastUpdated ?? featured.lastUpdated,
      } satisfies Stock;
    });
  }, [featuredStocks, fetchedBySymbol]);

  useEffect(() => {
    document.title = "Home | TradePulseAI";
  }, []);

  const isLoggedIn = useMemo(() => isUserAuthenticated(), []);

  const filteredStocks = useMemo(() => {
    const query = searchTerm.trim().toLowerCase();
    if (!query) {
      // No search — show featured top 50 from backend
      return defaultStocks.filter((stock) => typeof stock.symbol === "string" && stock.symbol.trim().length > 0);
    }

    // Search active — search all 800 fetched stocks from backend
    return fetchedStocks
      .filter((stock) => {
        if (typeof stock.symbol !== "string" || stock.symbol.trim().length === 0) return false;
        const symbol = stock.symbol.toLowerCase();
        const name = (stock.name ?? "").toLowerCase();
        return symbol.includes(query) || name.includes(query);
      })
      .slice(0, MAX_VISIBLE_STOCKS);
  }, [defaultStocks, fetchedStocks, searchTerm]);

  const visibleSymbols = useMemo(
    () =>
      filteredStocks
        .map((stock) => normalizeSymbol(stock.symbol))
        .filter((symbol): symbol is string => Boolean(symbol)),
    [filteredStocks],
  );

  const aggregateBySymbol = useWebSocketPrices(visibleSymbols);

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
            <h2 id="stocks-heading" style={{ display: "none" }}>Top stocks</h2>

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
            ) : filteredStocks.length === 0 ? (
              <p className="error-message">
                {searchTerm.trim()
                  ? "No stocks matched your search."
                  : "Featured stocks are refreshing. Please check back in a moment."}
              </p>
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


                        <div className="stock-card-actions">
                          <button
                            className="stock-add-to-cart-btn"
                            onClick={() => void addToCart(stock.id, 1)}
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