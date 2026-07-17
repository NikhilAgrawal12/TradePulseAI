import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { Header } from "../../components/Header.tsx";
import { useCart } from "../../context/CartContext";
import { useMarketStatus } from "../../context/MarketStatusContext";
import { useWatchlist } from "../../context/WatchlistContext";
import { isUserAuthenticated, subscribeToAuthChanges } from "../../utils/auth";
import { formatMoney, formatPercent } from "../../utils/money";
import { useStreamedStocks } from "../../utils/useStreamedStocks";
import "./HomePage.css";

function formatRealtimeTime(lastUpdated: string | null | undefined): string {
  if (!lastUpdated) {
    return "--";
  }
  const parsed = new Date(lastUpdated);
  return Number.isNaN(parsed.getTime())
    ? "--"
    : parsed.toLocaleString(undefined, {
        year: "numeric",
        month: "short",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
      });
}

export function HomePage() {
  const { addToCart } = useCart();
  const { addToWatchlist } = useWatchlist();
  const { sessionMeta } = useMarketStatus();
  const { stocks: streamedStocks, error, searchTerm, setSearchTerm } = useStreamedStocks();
  const [isLoggedIn, setIsLoggedIn] = useState<boolean>(() => isUserAuthenticated());

  useEffect(() => {
    document.title = "Home | TradePulse";
  }, []);

  useEffect(() => {
    return subscribeToAuthChanges(() => {
      setIsLoggedIn(isUserAuthenticated());
    });
  }, []);

  // The streamed stocks are already filtered server-side:
  // - If no search: featured stocks (top 50)
  // - If search query: search results (top 50)
  const filteredStocks = useMemo(() => {
    return streamedStocks.filter((stock) => typeof stock.symbol === "string" && stock.symbol.trim().length > 0);
  }, [streamedStocks]);

  return (
    <>
      <Header />

      <main className="home-page">
        {!isLoggedIn && (
          <section className="home-hero">
            <p className="home-eyebrow">Market pulse in real time</p>
            <h1>Track stocks and trade with confidence.</h1>

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

            {error && searchTerm.trim() ? (
              <p className="error-message">{error}</p>
            ) : filteredStocks.length === 0 ? (
              searchTerm.trim() ? <p className="error-message">No stocks matched your search.</p> : null
            ) : (
              <div className="stocks-grid">
                {filteredStocks.map((stock: (typeof filteredStocks)[number]) => {
                    const symbol = stock.symbol.trim().toUpperCase();
                    const stickerPrice = stock.price ?? 0;
                    const change = stock.changePercent ?? 0;
                    const isPositive = change >= 0;

                    return (
                      <article className="stock-card" key={stock.id}>
                        <div className="stock-card-header">
                          <div>
                            <h3>{symbol}</h3>
                            <p>{stock.name ?? "N/A"}</p>
                          </div>
                          <span className={`recommendation-badge ${sessionMeta.cssClass}`}>{sessionMeta.label}</span>
                        </div>

                        <div className="quote-price-row">
                          <strong>${formatMoney(stickerPrice)}</strong>
                          <span className={isPositive ? "price-up" : "price-down"}>{formatPercent(change)}%</span>
                        </div>

                        <div className="aggregate-block">
                          <p className="block-title">Live Snapshot</p>
                          <div className="aggregate-grid">
                            <p className="metric-item"><span>Open</span><strong className="metric-value">{typeof stock.open === "number" ? `$${formatMoney(stock.open)}` : "--"}</strong></p>
                            <p className="metric-item"><span>High</span><strong className="metric-value">{typeof stock.high === "number" ? `$${formatMoney(stock.high)}` : "--"}</strong></p>
                            <p className="metric-item"><span>Low</span><strong className="metric-value">{typeof stock.low === "number" ? `$${formatMoney(stock.low)}` : "--"}</strong></p>
                            <p className="metric-item"><span>Volume</span><strong className="metric-value">{typeof stock.volume === "number" ? stock.volume.toLocaleString() : "--"}</strong></p>
                            <p className="metric-item"><span>VWAP</span><strong className="metric-value">{typeof stock.vwap === "number" ? `$${formatMoney(stock.vwap)}` : "--"}</strong></p>
                            <p className="metric-item metric-item-full"><span>Updated</span><strong className="metric-value">{formatRealtimeTime(stock.lastUpdated)}</strong></p>
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
                            onClick={() => void addToWatchlist(stock.id)}
                          >
                            ⭐ Add to watchlist
                          </button>
                        </div>
                        <Link className="stock-insights-btn" to={`/stocks/${stock.id}/insights`}>
                          ✨ Insights
                        </Link>
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