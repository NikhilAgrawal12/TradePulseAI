import { useEffect, useMemo } from "react";
import { Link } from "react-router";
import { Header } from "../../components/Header.tsx";
import { useCart } from "../../context/CartContext";
import { useWatchlist } from "../../context/WatchlistContext";
import { isUserAuthenticated } from "../../utils/auth";
import { useStocks } from "../../utils/useStocks";
import "./HomePage.css";

const IMPORTANT_STOCKS_ORDER = ["GOOGL", "GOOG", "META", "AAPL", "NVDA", "MSFT", "AMZN", "TSLA"];
const IMPORTANT_STOCK_PRIORITY = new Map(IMPORTANT_STOCKS_ORDER.map((symbol, index) => [symbol, index]));

export function HomePage() {
  const { addToCart } = useCart();
  const { addToWatchlist } = useWatchlist();
  const { stocks, loading, error } = useStocks();

  useEffect(() => {
    document.title = "Home | TradePulseAI";
  }, []);

  const isLoggedIn = useMemo(() => isUserAuthenticated(), []);

  const pricedStocks = useMemo(
    () => stocks.filter((stock) => typeof stock.price === "number" && typeof stock.changePercent === "number"),
    [stocks],
  );

  const prioritizedStocks = useMemo(
    () =>
      [...pricedStocks].sort((a, b) => {
        const symbolA = a.symbol.trim().toUpperCase();
        const symbolB = b.symbol.trim().toUpperCase();
        const priorityA = IMPORTANT_STOCK_PRIORITY.get(symbolA) ?? Number.MAX_SAFE_INTEGER;
        const priorityB = IMPORTANT_STOCK_PRIORITY.get(symbolB) ?? Number.MAX_SAFE_INTEGER;

        if (priorityA !== priorityB) {
          return priorityA - priorityB;
        }

        return symbolA.localeCompare(symbolB);
      }),
    [pricedStocks],
  );

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
              <p>{loading ? "Loading..." : `${prioritizedStocks.length} stocks available`}</p>
            </div>

            {error ? (
              <p className="error-message">{error}</p>
            ) : loading ? (
              <p className="loading-message">Loading stocks from backend...</p>
            ) : prioritizedStocks.length === 0 ? (
              <p className="no-data-message">No stocks available at the moment. Check back later.</p>
            ) : (
              <div className="stocks-grid">
                {prioritizedStocks.map((stock) => {
                  const isPositive = (stock.changePercent ?? 0) >= 0;
                  const price = stock.price ?? 0;
                  const change = stock.changePercent ?? 0;

                  return (
                    <article className="stock-card" key={stock.id}>
                      <div className="stock-card-header">
                        <div>
                          <h3>{stock.symbol}</h3>
                          <p>{stock.name ?? "N/A"}</p>
                        </div>
                        <span className={`recommendation-badge ${stock.active ? "active" : "inactive"}`}>{stock.active ? "Active" : "Inactive"}</span>
                      </div>

                      <div className="stock-price-row">
                        <strong>${price.toFixed(2)}</strong>
                        <span className={isPositive ? "price-up" : "price-down"}>{isPositive ? "+" : ""}{change.toFixed(2)}%</span>
                      </div>

                      <div className="stock-meta-grid">
                        <p className="metric-item metric-item-full"><span>Exchange</span><strong className="metric-value">{stock.exchange ?? "N/A"}</strong></p>
                        <p className="metric-item"><span>Market</span><strong className="metric-value">{stock.market ?? "N/A"}</strong></p>
                        <p className="metric-item"><span>Locale</span><strong className="metric-value">{stock.locale ?? "N/A"}</strong></p>
                        <p className="metric-item"><span>Volume</span><strong className="metric-value">{(stock.volume ?? 0).toLocaleString()}</strong></p>
                      </div>

                      <p className="stock-rating">Last update: {stock.lastUpdated ? new Date(stock.lastUpdated).toLocaleString() : "N/A"}</p>

                      <div className="stock-card-actions">
                        <button
                          className="stock-add-to-cart-btn"
                          onClick={() => void addToCart(stock.id, stock.symbol, price, 1)}
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