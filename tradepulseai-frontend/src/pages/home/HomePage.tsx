import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { Header } from "../../components/Header.tsx";
import { useCart } from "../../context/CartContext";
import { useWatchlist } from "../../context/WatchlistContext";
import { isUserAuthenticated } from "../../utils/auth";
import { getMarketSession, getMarketSessionFromBackend, type SessionMeta } from "../../utils/marketSession";
import { useStreamedStocks } from "../../utils/useStreamedStocks";
import "./HomePage.css";

function formatRealtimeTime(lastUpdated: string | null | undefined): string {
  if (!lastUpdated) {
    return "--";
  }
  const parsed = new Date(lastUpdated);
  return Number.isNaN(parsed.getTime()) ? "--" : parsed.toLocaleTimeString();
}

export function HomePage() {
  const { addToCart } = useCart();
  const { addToWatchlist } = useWatchlist();
  const { stocks: streamedStocks, error, searchTerm, setSearchTerm } = useStreamedStocks();

  useEffect(() => {
    document.title = "Home | TradePulseAI";
  }, []);

  // Update session badge every minute
  const [sessionMeta, setSessionMeta] = useState<SessionMeta>(() => getMarketSession());
  useEffect(() => {
    let cancelled = false;

    const refreshSession = async () => {
      const next = await getMarketSessionFromBackend();
      if (!cancelled) {
        setSessionMeta(next);
      }
    };

    void refreshSession();
    const id = window.setInterval(() => {
      void refreshSession();
    }, 60_000);

    return () => {
      cancelled = true;
      window.clearInterval(id);
    };
  }, []);

  const isLoggedIn = useMemo(() => isUserAuthenticated(), []);

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
                {filteredStocks.map((stock) => {
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
                          <strong>${stickerPrice.toFixed(2)}</strong>
                          <span className={isPositive ? "price-up" : "price-down"}>{isPositive ? "+" : ""}{change.toFixed(2)}%</span>
                        </div>

                        <div className="aggregate-block">
                          <p className="block-title">Live Snapshot</p>
                          <div className="aggregate-grid">
                            <p className="metric-item"><span>Open</span><strong className="metric-value">{typeof stock.open === "number" ? `$${stock.open.toFixed(2)}` : "--"}</strong></p>
                            <p className="metric-item"><span>High</span><strong className="metric-value">{typeof stock.high === "number" ? `$${stock.high.toFixed(2)}` : "--"}</strong></p>
                            <p className="metric-item"><span>Low</span><strong className="metric-value">{typeof stock.low === "number" ? `$${stock.low.toFixed(2)}` : "--"}</strong></p>
                            <p className="metric-item"><span>Volume</span><strong className="metric-value">{typeof stock.volume === "number" ? stock.volume.toLocaleString() : "--"}</strong></p>
                            <p className="metric-item"><span>VWAP</span><strong className="metric-value">{typeof stock.vwap === "number" ? `$${stock.vwap.toFixed(2)}` : "--"}</strong></p>
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