import { useEffect, useMemo } from "react";
import { Link } from "react-router";
import { Header } from "../../components/Header.tsx";
import { useCart } from "../../context/CartContext";
import { useWatchlist } from "../../context/WatchlistContext";
import { isUserAuthenticated } from "../../utils/auth";
import { useStocks } from "../../utils/useStocks";
import "./HomePage.css";


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

  const topGainers = useMemo(
    () => [...pricedStocks].sort((a, b) => (b.changePercent ?? 0) - (a.changePercent ?? 0)).slice(0, 4),
    [pricedStocks],
  );

  const topLosers = useMemo(
    () => [...pricedStocks].sort((a, b) => (a.changePercent ?? 0) - (b.changePercent ?? 0)).slice(0, 4),
    [pricedStocks],
  );

  const mostActive = useMemo(
    () => [...pricedStocks].sort((a, b) => (b.volume ?? 0) - (a.volume ?? 0)).slice(0, 5),
    [pricedStocks],
  );

  const exchangeOverview = useMemo(() => {
    const totals = new Map<string, { total: number; count: number }>();

    pricedStocks.forEach((stock) => {
      const exchange = stock.exchange ?? "Unknown";
      const current = totals.get(exchange) ?? { total: 0, count: 0 };
      totals.set(exchange, {
        total: current.total + (stock.changePercent ?? 0),
        count: current.count + 1,
      });
    });

    return Array.from(totals.entries())
      .map(([exchange, data]) => ({ exchange, avgChange: data.total / data.count }))
      .sort((a, b) => b.avgChange - a.avgChange);
  }, [pricedStocks]);

  const strongestExchange = exchangeOverview[0];
  const weakestExchange = exchangeOverview[exchangeOverview.length - 1];

  const marketAverage = useMemo(() => {
    if (pricedStocks.length === 0) {
      return 0;
    }
    const total = pricedStocks.reduce((sum, stock) => sum + (stock.changePercent ?? 0), 0);
    return total / pricedStocks.length;
  }, [pricedStocks]);

  const stockHeadlines = topGainers.slice(0, 3).map((stock) => ({
    id: `${stock.id}-headline`,
    title: `${stock.symbol} moves ${(stock.changePercent ?? 0).toFixed(2)}% on latest feed`,
    source: stock.source ?? "Live Feed",
  }));

  const newsTickerItems = useMemo(() => {
    if (pricedStocks.length === 0) {
      return ["Waiting for live Polygon market updates..."];
    }

    const items: string[] = [];
    if (topGainers[0]) {
      items.push(`${topGainers[0].symbol} leads gainers at +${(topGainers[0].changePercent ?? 0).toFixed(2)}%`);
    }
    if (topLosers[0]) {
      items.push(`${topLosers[0].symbol} is the largest pullback at ${(topLosers[0].changePercent ?? 0).toFixed(2)}%`);
    }
    if (mostActive[0]) {
      items.push(`${mostActive[0].symbol} tops activity with ${(mostActive[0].volume ?? 0).toLocaleString()} volume`);
    }
    if (strongestExchange) {
      items.push(`${strongestExchange.exchange} currently strongest exchange (${strongestExchange.avgChange.toFixed(2)}%)`);
    }

    return items;
  }, [pricedStocks, topGainers, topLosers, mostActive, strongestExchange]);

  const upcomingAlerts = useMemo(() => {
    return mostActive.slice(0, 4).map((stock) => ({
      label: stock.source ?? "Live Source",
      company: `${stock.name ?? stock.symbol} (${stock.symbol})`,
      when: `Last update: ${stock.lastUpdated ? new Date(stock.lastUpdated).toLocaleTimeString() : "N/A"}`,
    }));
  }, [mostActive]);

  const indexSnapshot = useMemo(() => {
    return [
      { name: "Tracked Universe", value: stocks.length.toLocaleString(), change: marketAverage },
      { name: "Priced Symbols", value: pricedStocks.length.toLocaleString(), change: marketAverage },
      { name: "Top Gainer", value: topGainers[0]?.symbol ?? "N/A", change: topGainers[0]?.changePercent ?? 0 },
      { name: "Top Loser", value: topLosers[0]?.symbol ?? "N/A", change: topLosers[0]?.changePercent ?? 0 },
    ];
  }, [stocks.length, pricedStocks.length, marketAverage, topGainers, topLosers]);

  return (
    <>
      <Header />

      <main className="home-page">
        {!isLoggedIn && (
          <section className="home-hero">
            <p className="home-eyebrow">Market pulse in real time</p>
            <h1>Track stocks and trade with confidence.</h1>
            <p className="home-subtitle">All market data below is loaded from live backend feeds.</p>
            <div className="home-hero-actions">
              <Link to="/login" className="home-btn-primary">Sign in</Link>
              <Link to="/registration" className="home-btn-secondary">Create account</Link>
            </div>
          </section>
        )}

        <div className="home-section-shell">
          <section className="home-stocks-section" aria-labelledby="stocks-heading">
            <div className="stocks-top-row">
              <h2 id="stocks-heading">Explore stocks</h2>
              <p>{loading ? "Loading..." : `${pricedStocks.length} priced results`}</p>
            </div>

            {error ? (
              <p>{error}</p>
            ) : loading ? (
              <p>Loading stocks from backend...</p>
            ) : (
              <div className="stocks-grid">
                {pricedStocks.map((stock) => {
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
                        <span className="recommendation-badge hold">{stock.source ?? "live"}</span>
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

                      <p className="stock-rating">Last update: {stock.lastUpdated ? new Date(stock.lastUpdated).toLocaleTimeString() : "N/A"}</p>

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

          <section className="market-news-ticker" aria-label="Market news ticker">
            <div className="ticker-track">
              {[...newsTickerItems, ...newsTickerItems].map((item, index) => (
                <span key={`${item}-${index}`}>• {item}</span>
              ))}
            </div>
          </section>

          <section className="market-insights-grid">
            <article className="insight-card">
              <h3>Top Gainers</h3>
              {topGainers.map((stock) => (
                <p key={`${stock.id}-g`}><strong>{stock.symbol}</strong><span className="price-up">+{(stock.changePercent ?? 0).toFixed(2)}%</span></p>
              ))}
            </article>

            <article className="insight-card">
              <h3>Top Losers</h3>
              {topLosers.map((stock) => (
                <p key={`${stock.id}-l`}><strong>{stock.symbol}</strong><span className="price-down">{(stock.changePercent ?? 0).toFixed(2)}%</span></p>
              ))}
            </article>

            <article className="insight-card">
              <h3>Most Active</h3>
              {mostActive.map((stock) => (
                <p key={`${stock.id}-a`}><strong>{stock.symbol}</strong><span>{(stock.volume ?? 0).toLocaleString()}</span></p>
              ))}
            </article>

            <article className="insight-card">
              <h3>Exchange Overview</h3>
              {exchangeOverview.map((exchange) => (
                <p key={exchange.exchange}><strong>{exchange.exchange}</strong><span className={exchange.avgChange >= 0 ? "price-up" : "price-down"}>{exchange.avgChange >= 0 ? "+" : ""}{exchange.avgChange.toFixed(2)}%</span></p>
              ))}
            </article>
          </section>

          <section className="indices-section">
            <h3>Market Snapshot</h3>
            <div className="indices-grid">
              {indexSnapshot.map((index) => (
                <article className="index-card" key={index.name}>
                  <p>{index.name}</p>
                  <strong>{index.value}</strong>
                  <span className={index.change >= 0 ? "price-up" : "price-down"}>{index.change >= 0 ? "+" : ""}{index.change.toFixed(2)}%</span>
                </article>
              ))}
            </div>
          </section>

          <section className="daily-summary-card">
            <h3>Daily Summary</h3>
            <p>
              Today, priced symbols are <strong>{marketAverage >= 0 ? "up" : "down"} {Math.abs(marketAverage).toFixed(2)}%</strong>. Strongest exchange: <strong>{strongestExchange?.exchange ?? "N/A"}</strong> ({strongestExchange?.avgChange.toFixed(2)}%). Weakest exchange: <strong>{weakestExchange?.exchange ?? "N/A"}</strong> ({weakestExchange?.avgChange.toFixed(2)}%).
            </p>
          </section>

          <section className="news-alerts-grid">
            <article className="news-card">
              <h3>Stock-specific headlines</h3>
              {stockHeadlines.map((headline) => (
                <p key={headline.id}><strong>{headline.title}</strong><span>{headline.source}</span></p>
              ))}
            </article>

            <article className="news-card">
              <h3>Live Update Alerts</h3>
              {upcomingAlerts.map((alert) => (
                <p key={`${alert.company}-${alert.when}`}><strong>{alert.label}: {alert.company}</strong><span>{alert.when}</span></p>
              ))}
            </article>
          </section>
        </div>
      </main>
    </>
  );
}