import { useMemo } from "react";
import { Link } from "react-router";
import { Header } from "../../components/Header.tsx";
import { stocks } from "../../data/stocks";
import { useCart } from "../../context/CartContext";
import { useWatchlist } from "../../context/WatchlistContext";
import "./HomePage.css";


export function HomePage() {
  const { addToCart } = useCart();
  const { addToWatchlist } = useWatchlist();

  const isLoggedIn = useMemo(() => {
    return Boolean(localStorage.getItem("authToken") || sessionStorage.getItem("authToken"));
  }, []);

  const topGainers = useMemo(() => [...stocks].sort((a, b) => b.changePercent - a.changePercent).slice(0, 4), []);
  const topLosers = useMemo(() => [...stocks].sort((a, b) => a.changePercent - b.changePercent).slice(0, 4), []);
  const mostActive = useMemo(() => [...stocks].sort((a, b) => b.volume - a.volume).slice(0, 5), []);

  const sectorOverview = useMemo(() => {
    const totals = new Map<string, { total: number; count: number }>();

    stocks.forEach((stock) => {
      const current = totals.get(stock.sector) ?? { total: 0, count: 0 };
      totals.set(stock.sector, {
        total: current.total + stock.changePercent,
        count: current.count + 1,
      });
    });

    return Array.from(totals.entries())
      .map(([sector, data]) => ({
        sector,
        avgChange: data.total / data.count,
      }))
      .sort((a, b) => b.avgChange - a.avgChange);
  }, []);

  const strongestSector = sectorOverview[0];
  const weakestSector = sectorOverview[sectorOverview.length - 1];

  const marketAverage = useMemo(() => {
    const total = stocks.reduce((sum, stock) => sum + stock.changePercent, 0);
    return total / stocks.length;
  }, []);

  const indexSnapshot = [
    { name: "S&P 500", value: "5,318.24", change: 0.78 },
    { name: "NASDAQ", value: "16,904.70", change: 1.12 },
    { name: "Dow Jones", value: "39,891.56", change: 0.32 },
    { name: "Russell 2000", value: "2,102.63", change: -0.21 },
  ];

  const stockHeadlines = topGainers.slice(0, 3).map((stock) => ({
    id: `${stock.id}-headline`,
    title: `${stock.symbol} jumps ${stock.changePercent.toFixed(2)}% as momentum accelerates`,
    source: "TradePulseAI News Desk",
  }));

  const newsTickerItems = [
    "Fed signals a data-driven stance ahead of next policy meeting",
    "Tech leads gains as AI infrastructure demand remains strong",
    "Energy stocks rise with crude oil rebounding above weekly lows",
    "Retail earnings surprise to the upside in latest quarter",
  ];

  const upcomingAlerts = [
    { label: "Earnings", company: "NVIDIA (NVDA)", when: "Tomorrow, 8:30 PM" },
    { label: "Earnings", company: "Tesla (TSLA)", when: "Mar 21, 9:00 PM" },
    { label: "Dividend", company: "Johnson & Johnson (JNJ)", when: "Ex-date: Mar 25" },
    { label: "Dividend", company: "Procter & Gamble (PG)", when: "Ex-date: Mar 28" },
  ];

  return (
    <>
      <Header />

      <main className="home-page">
        {!isLoggedIn && (
          <section className="home-hero">
            <p className="home-eyebrow">Market pulse in real time</p>
            <h1>Track stocks, read AI signals, and trade with confidence.</h1>
            <p className="home-subtitle">View recommendations and unlock deeper analytics after sign in.</p>
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
            <p>{stocks.length} results</p>
          </div>

          <div className="stocks-grid">
            {stocks.map((stock) => {
              const isPositive = stock.changePercent >= 0;

              return (
                <article className="stock-card" key={stock.id}>
                  <div className="stock-card-header">
                    <div>
                      <h3>{stock.symbol}</h3>
                      <p>{stock.name}</p>
                    </div>
                    <span className={`recommendation-badge ${stock.recommendation.toLowerCase()}`}>{stock.recommendation}</span>
                  </div>

                  <div className="stock-price-row">
                    <strong>${stock.price.toFixed(2)}</strong>
                    <span className={isPositive ? "price-up" : "price-down"}>{isPositive ? "+" : ""}{stock.changePercent.toFixed(2)}%</span>
                  </div>

                  <div className="stock-meta-grid">
                    <p className="metric-item metric-item-full"><span>Sector</span><strong className="metric-value">{stock.sector}</strong></p>
                    <p className="metric-item"><span>Exchange</span><strong className="metric-value">{stock.exchange}</strong></p>
                    <p className="metric-item"><span>Market Cap</span><strong className="metric-value">${stock.marketCapBillion}B</strong></p>
                    <p className="metric-item"><span>Volume</span><strong className="metric-value">{stock.volume.toLocaleString()}</strong></p>
                  </div>

                  <p className="stock-rating">AI confidence {stock.rating.score}/5 ({stock.rating.analysts} analysts)</p>

                  <div className="stock-card-actions">
                    <button
                      className="stock-add-to-cart-btn"
                      onClick={() => addToCart(stock.id, stock.symbol, stock.price, 1)}
                    >
                      🛒 Add to cart
                    </button>
                    <button
                      className="stock-add-to-watchlist-btn"
                      onClick={() => addToWatchlist(stock.id, stock.symbol, stock.price, 1)}
                    >
                      ⭐ Add to watchlist
                    </button>
                  </div>
                </article>
              );
            })}
          </div>
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
              <p key={`${stock.id}-g`}><strong>{stock.symbol}</strong><span className="price-up">+{stock.changePercent.toFixed(2)}%</span></p>
            ))}
          </article>

          <article className="insight-card">
            <h3>Top Losers</h3>
            {topLosers.map((stock) => (
              <p key={`${stock.id}-l`}><strong>{stock.symbol}</strong><span className="price-down">{stock.changePercent.toFixed(2)}%</span></p>
            ))}
          </article>

          <article className="insight-card">
            <h3>Most Active</h3>
            {mostActive.map((stock) => (
              <p key={`${stock.id}-a`}><strong>{stock.symbol}</strong><span>{stock.volume.toLocaleString()}</span></p>
            ))}
          </article>

          <article className="insight-card">
            <h3>Sector Overview</h3>
            {sectorOverview.map((sector) => (
              <p key={sector.sector}><strong>{sector.sector}</strong><span className={sector.avgChange >= 0 ? "price-up" : "price-down"}>{sector.avgChange >= 0 ? "+" : ""}{sector.avgChange.toFixed(2)}%</span></p>
            ))}
          </article>
        </section>

        <section className="indices-section">
          <h3>Market Indices Snapshot</h3>
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
            Today, the market is <strong>{marketAverage >= 0 ? "up" : "down"} {Math.abs(marketAverage).toFixed(2)}%</strong>. Top performing sector: <strong>{strongestSector?.sector}</strong> ({strongestSector?.avgChange.toFixed(2)}%). Weakest sector: <strong>{weakestSector?.sector}</strong> ({weakestSector?.avgChange.toFixed(2)}%).
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
            <h3>Earnings / Dividend Alerts</h3>
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