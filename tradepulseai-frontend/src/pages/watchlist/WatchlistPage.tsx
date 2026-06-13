import { useEffect, useMemo, useState } from "react";
import { Header } from "../../components/Header.tsx";
import { useCart } from "../../context/CartContext";
import { useWatchlist } from "../../context/WatchlistContext";
import { getMarketSession, getMarketSessionFromBackend, type SessionMeta } from "../../utils/marketSession";
import { useStocks } from "../../utils/useStocks";
import "./WatchlistPage.css";

export function WatchlistPage() {
  useEffect(() => { document.title = "Watchlist | TradePulseAI"; }, []);

  const { addToCart } = useCart();
  const { watchlist, removeFromWatchlist } = useWatchlist();
  const { stocks, error } = useStocks();
  const [sessionMeta, setSessionMeta] = useState<SessionMeta>(() => getMarketSession());
  const [search, setSearch] = useState("");

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

  const stockMap = useMemo(() => new Map(stocks.map((stock) => [stock.id, stock])), [stocks]);

  const watchlistStocks = useMemo(
    () =>
      watchlist.flatMap((entry) => {
        const stock = stockMap.get(entry.stockId);
        if (!stock || typeof stock.price !== "number") {
          return [];
        }

        return [{ ...entry, stock }];
      }),
    [stockMap, watchlist],
  );

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return watchlistStocks;
    return watchlistStocks.filter(({ stock }) =>
      [stock.symbol, stock.name, stock.exchange, stock.market].join(" ").toLowerCase().includes(q),
    );
  }, [search, watchlistStocks]);

  const handleRemove = (stockId: string) => void removeFromWatchlist(stockId);

  return (
    <>
      <Header />
      <main className="wl-page">
        {error && <p>{error}</p>}

        {/* ── Summary stats ── */}
        <section className="wl-stats">
          <div className="wl-stat-card">
            <span>Stocks Tracked</span>
            <strong>{watchlistStocks.length}</strong>
          </div>
        </section>

        {/* ── Toolbar ── */}
        <div className="wl-toolbar">
          <div className="wl-search-wrap">
            <input
              type="search"
              className="wl-search"
              placeholder="Search watchlist…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
        </div>

        <div className="wl-market-status" role="status" aria-live="polite">
          <span>Market Status</span>
          <span className={`wl-session-badge ${sessionMeta.cssClass}`}>{sessionMeta.label}</span>
        </div>

        {/* ── Watchlist table ── */}
        {filtered.length === 0 ? (
          <div className="wl-empty">
            {watchlist.length === 0
              ? <p>Your watchlist is empty.</p>
              : <p>No stocks matched your search.</p>}
          </div>
        ) : (
          <div className="wl-table-wrap">
            <table className="wl-table">
              <thead>
                <tr>
                  <th>Symbol</th>
                  <th>Company</th>
                  <th>Current price</th>
                  <th>Day change</th>
                  <th></th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {filtered.map(({ stock }) => {
                  const livePrice = stock.price ?? 0;
                  const liveChange = stock.changePercent ?? 0;

                  return (
                    <tr key={stock.id}>
                      <td><strong>{stock.symbol}</strong></td>
                      <td><span className="wl-name">{stock.name ?? "N/A"}</span><span className="wl-sector">{stock.exchange ?? "N/A"}</span></td>
                      <td>${livePrice.toFixed(2)}</td>
                      <td className={liveChange >= 0 ? "price-up" : "price-down"}>
                        {liveChange >= 0 ? "+" : ""}{liveChange.toFixed(2)}%
                      </td>
                      <td>
                        <button
                          type="button"
                          className="wl-add-to-cart-btn"
                          onClick={() => void addToCart(stock.id, 1)}
                          title="Add to cart"
                        >🛒</button>
                      </td>
                      <td>
                        <button
                          type="button"
                          className="wl-remove-btn"
                          onClick={() => handleRemove(stock.id)}
                          title="Remove from watchlist"
                        >✕</button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </main>
    </>
  );
}
