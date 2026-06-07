import { useEffect, useMemo, useState } from "react";
import { Header } from "../../components/Header.tsx";
import { useCart } from "../../context/CartContext";
import { useWatchlist } from "../../context/WatchlistContext";
import { isUserAuthenticated, requireSignIn } from "../../utils/auth";
import { useWebSocketPrices } from "../../utils/useWebSocketPrices";
import { useStocks } from "../../utils/useStocks";
import "./WatchlistPage.css";

function calculateChangePercent(previousClose: number | null, close: number): number {
  if (previousClose === null || previousClose <= 0) {
    return 0;
  }
  return ((close - previousClose) / previousClose) * 100;
}

export function WatchlistPage() {
  useEffect(() => { document.title = "Watchlist | TradePulseAI"; }, []);

  const { addToCart } = useCart();
  const { watchlist, addToWatchlist, removeFromWatchlist } = useWatchlist();
  const { stocks, loading, error } = useStocks();
  const [search, setSearch] = useState("");
  const [addSearch, setAddSearch] = useState("");
  const [addQty, setAddQty] = useState(1);
  const [addStock, setAddStock] = useState("");
  const [showAdd, setShowAdd] = useState(false);

  const stockMap = useMemo(() => new Map(stocks.map((stock) => [stock.id, stock])), [stocks]);

  const watchlistStocks = useMemo(
    () =>
      watchlist.flatMap((entry) => {
        const stock = stockMap.get(entry.stockId);
        if (!stock || typeof stock.price !== "number") {
          return [];
        }

        const quantity = Number(entry.quantity);
        return [{ ...entry, quantity, stock }];
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

  const addCandidates = useMemo(() => {
    const inList = new Set(watchlist.map((e) => e.stockId));
    const q = addSearch.trim().toLowerCase();
    return stocks.filter((s) => {
      const notIn = !inList.has(s.id);
      if (!q) return notIn;
      return notIn && [s.symbol, s.name, s.exchange, s.market].join(" ").toLowerCase().includes(q);
    });
  }, [addSearch, watchlist, stocks]);

  const symbols = useMemo(() => watchlistStocks.map(({ stock }) => stock.symbol), [watchlistStocks]);
  const aggregateBySymbol = useWebSocketPrices(symbols);
  const totalCurrent = watchlistStocks.reduce((sum, entry) => {
    const symbol = entry.stock.symbol.trim().toUpperCase();
    const livePrice = aggregateBySymbol[symbol]?.close ?? entry.stock.price ?? 0;
    return sum + livePrice * entry.quantity;
  }, 0);
  const totalQuantity = watchlistStocks.reduce((s, e) => s + e.quantity, 0);

  const handleAdd = () => {
    if (!addStock) return;
    void addToWatchlist(addStock, addQty);
    setAddStock("");
    setAddSearch("");
    setAddQty(1);
    setShowAdd(false);
  };

  const toggleAddPanel = () => {
    if (!isUserAuthenticated()) {
      requireSignIn();
      return;
    }

    setShowAdd((v) => !v);
  };

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
          <div className="wl-stat-card">
            <span>Total Quantity</span>
            <strong>{totalQuantity.toFixed(0)}</strong>
          </div>
          <div className="wl-stat-card">
            <span>Current Value</span>
            <strong>${totalCurrent.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</strong>
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
          <button className="wl-add-btn" onClick={toggleAddPanel}>
            {showAdd ? "✕ Cancel" : "+ Add stock"}
          </button>
        </div>

        {/* ── Add stock panel ── */}
        {showAdd && (
          <div className="wl-add-panel">
            <h3>Add a stock to watchlist</h3>
            <div className="wl-add-form">
              <div className="wl-add-search">
                <input
                  type="search"
                  placeholder="Search symbol or company…"
                  value={addSearch}
                  onChange={(e) => setAddSearch(e.target.value)}
                />
                <div className="wl-add-dropdown">
                  {loading ? (
                    <p>Loading stocks...</p>
                  ) : addCandidates.length === 0 ? (
                    <p>No stocks available to add.</p>
                  ) : (
                    addCandidates.slice(0, 6).map((s) => (
                      <button
                        key={s.id}
                        type="button"
                        className={`wl-add-option ${addStock === s.id ? "selected" : ""}`}
                        onClick={() => { setAddStock(s.id); setAddSearch(s.symbol); }}
                      >
                        <strong>{s.symbol}</strong> - {s.name ?? "N/A"}
                        <span className={(s.changePercent ?? 0) >= 0 ? "price-up" : "price-down"}>
                          ${(s.price ?? 0).toFixed(2)} ({(s.changePercent ?? 0) >= 0 ? "+" : ""}{(s.changePercent ?? 0).toFixed(2)}%)
                        </span>
                      </button>
                    ))
                  )}
                </div>
              </div>

              <div className="wl-add-fields">
                <label>
                  Quantity
                  <input
                    type="number"
                    min="1"
                    step="1"
                    value={addQty}
                    onChange={(e) => setAddQty(parseInt(e.target.value, 10) || 1)}
                  />
                </label>
                <button
                  type="button"
                  className="wl-confirm-add"
                  disabled={!addStock}
                  onClick={handleAdd}
                >
                  Add to watchlist
                </button>
              </div>
            </div>
          </div>
        )}

        {/* ── Watchlist table ── */}
        {filtered.length === 0 ? (
          <div className="wl-empty">
            {watchlist.length === 0
              ? <p>Your watchlist is empty. Add stocks using the button above.</p>
              : <p>No stocks matched your search.</p>}
          </div>
        ) : (
          <div className="wl-table-wrap">
            <table className="wl-table">
              <thead>
                <tr>
                  <th>Symbol</th>
                  <th>Company</th>
                  <th>Source</th>
                  <th>Current price</th>
                  <th>Day change</th>
                  <th>Qty</th>
                  <th>Current value</th>
                  <th></th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {filtered.map(({ stock, quantity }) => {
                  const symbol = stock.symbol.trim().toUpperCase();
                  const aggregate = aggregateBySymbol[symbol];
                  const livePrice = aggregate?.close ?? stock.price ?? 0;
                  const liveChange = aggregate
                    ? calculateChangePercent(aggregate.previousClose, aggregate.close)
                    : (stock.changePercent ?? 0);
                  const currentValue = livePrice * quantity;

                  return (
                    <tr key={stock.id}>
                      <td><strong>{stock.symbol}</strong></td>
                      <td><span className="wl-name">{stock.name ?? "N/A"}</span><span className="wl-sector">{stock.exchange ?? "N/A"}</span></td>
                      <td><span className="rec-badge hold">{stock.source ?? "live"}</span></td>
                      <td>${livePrice.toFixed(2)}</td>
                      <td className={liveChange >= 0 ? "price-up" : "price-down"}>
                        {liveChange >= 0 ? "+" : ""}{liveChange.toFixed(2)}%
                      </td>
                      <td>{quantity}</td>
                      <td>${currentValue.toFixed(2)}</td>
                      <td>
                        <button
                          type="button"
                          className="wl-add-to-cart-btn"
                          onClick={() => void addToCart(stock.id, quantity)}
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
