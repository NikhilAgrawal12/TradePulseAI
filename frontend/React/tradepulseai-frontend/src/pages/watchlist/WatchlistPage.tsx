import { useEffect, useMemo, useState } from "react";
import { Header } from "../../components/Header.tsx";
import { stocks } from "../../data/stocks";
import { useCart } from "../../context/CartContext";
import { useWatchlist } from "../../context/WatchlistContext";
import "./WatchlistPage.css";

export function WatchlistPage() {
  useEffect(() => { document.title = "Watchlist | TradePulseAI"; }, []);

  const { addToCart } = useCart();
  const { watchlist, addToWatchlist, removeFromWatchlist } = useWatchlist();
  const [search, setSearch] = useState("");
  const [addSearch, setAddSearch] = useState("");
  const [addQty, setAddQty] = useState(1);
  const [addRef, setAddRef] = useState("");
  const [addStock, setAddStock] = useState("");
  const [showAdd, setShowAdd] = useState(false);

  const watchlistStocks = useMemo(() =>
    watchlist.map((entry) => {
      const stock        = stocks.find((s) => s.id === entry.stockId)!;
      const currentValue = stock.price * entry.quantity;
      const refValue     = entry.refPrice * entry.quantity;
      const pnl          = currentValue - refValue;
      const pnlPct       = ((stock.price - entry.refPrice) / entry.refPrice) * 100;
      return { ...entry, stock, currentValue, refValue, pnl, pnlPct };
    }),
  [watchlist]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return watchlistStocks;
    return watchlistStocks.filter(({ stock }) =>
      [stock.symbol, stock.name, stock.sector].join(" ").toLowerCase().includes(q)
    );
  }, [search, watchlistStocks]);

  const addCandidates = useMemo(() => {
    const inList = new Set(watchlist.map((e) => e.stockId));
    const q = addSearch.trim().toLowerCase();
    return stocks.filter((s) => {
      const notIn = !inList.has(s.id);
      if (!q) return notIn;
      return notIn && [s.symbol, s.name, s.sector].join(" ").toLowerCase().includes(q);
    });
  }, [addSearch, watchlist]);

  const totalRefValue = watchlistStocks.reduce((s, e) => s + e.refValue, 0);
  const totalCurrent  = watchlistStocks.reduce((s, e) => s + e.currentValue, 0);
  const totalPnL      = totalCurrent - totalRefValue;
  const totalPnLPct   = totalRefValue > 0 ? (totalPnL / totalRefValue) * 100 : 0;

  const handleAdd = () => {
    if (!addStock) return;
    const stock    = stocks.find((s) => s.id === addStock)!;
    const refPrice = parseFloat(addRef) || stock.price;
    addToWatchlist(addStock, stock.symbol, refPrice, addQty);
    setAddStock(""); setAddSearch(""); setAddRef(""); setAddQty(1); setShowAdd(false);
  };

  const handleRemove = (stockId: string) =>
    removeFromWatchlist(stockId);

  return (
    <>
      <Header />
      <main className="wl-page">

        {/* ── Summary stats ── */}
        <section className="wl-stats">
          <div className="wl-stat-card">
            <span>Monitored Value</span>
            <strong>${totalRefValue.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</strong>
          </div>
          <div className="wl-stat-card">
            <span>Current Value</span>
            <strong>${totalCurrent.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</strong>
          </div>
          <div className={`wl-stat-card ${totalPnL >= 0 ? "stat-green" : "stat-red"}`}>
            <span>Total Change ($)</span>
            <strong>{totalPnL >= 0 ? "+" : "−"}${Math.abs(totalPnL).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</strong>
          </div>
          <div className={`wl-stat-card ${totalPnLPct >= 0 ? "stat-green" : "stat-red"}`}>
            <span>Total Change (%)</span>
            <strong>{totalPnLPct >= 0 ? "+" : ""}{totalPnLPct.toFixed(2)}%</strong>
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
          <button className="wl-add-btn" onClick={() => setShowAdd((v) => !v)}>
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
                  {addCandidates.slice(0, 6).map((s) => (
                    <button
                      key={s.id}
                      type="button"
                      className={`wl-add-option ${addStock === s.id ? "selected" : ""}`}
                      onClick={() => { setAddStock(s.id); setAddSearch(s.symbol); }}
                    >
                      <strong>{s.symbol}</strong> — {s.name}
                      <span className={s.changePercent >= 0 ? "price-up" : "price-down"}>
                        ${s.price.toFixed(2)} ({s.changePercent >= 0 ? "+" : ""}{s.changePercent.toFixed(2)}%)
                      </span>
                    </button>
                  ))}
                </div>
              </div>

              <div className="wl-add-fields">
                <label>
                  Ref. price ($)
                  <input
                    type="number"
                    min="0.01"
                    step="0.01"
                    placeholder="e.g. 210.00"
                    value={addRef}
                    onChange={(e) => setAddRef(e.target.value)}
                  />
                </label>
                <label>
                  Quantity
                  <input
                    type="number"
                    min="1"
                    step="1"
                    value={addQty}
                    onChange={(e) => setAddQty(parseInt(e.target.value) || 1)}
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
                  <th>Signal</th>
                  <th>Current price</th>
                  <th>Day change</th>
                  <th>Ref. price</th>
                  <th>Qty</th>
                  <th>Ref. value</th>
                  <th>Current value</th>
                  <th>Change ($)</th>
                  <th>Change (%)</th>
                  <th></th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {filtered.map(({ stock, refPrice, quantity, refValue, currentValue, pnl, pnlPct }) => (
                  <tr key={stock.id} className={pnl >= 0 ? "row-green" : "row-red"}>
                    <td className={pnl >= 0 ? "symbol-green" : "symbol-red"}>
                      <strong>{stock.symbol}</strong>
                    </td>
                    <td><span className="wl-name">{stock.name}</span><span className="wl-sector">{stock.sector}</span></td>
                    <td><span className={`rec-badge ${stock.recommendation.toLowerCase()}`}>{stock.recommendation}</span></td>
                    <td>${stock.price.toFixed(2)}</td>
                    <td className={stock.changePercent >= 0 ? "price-up" : "price-down"}>
                      {stock.changePercent >= 0 ? "+" : ""}{stock.changePercent.toFixed(2)}%
                    </td>
                    <td>${refPrice.toFixed(2)}</td>
                    <td>{quantity}</td>
                    <td>${refValue.toFixed(2)}</td>
                    <td>${currentValue.toFixed(2)}</td>
                    <td className={pnl >= 0 ? "price-up" : "price-down"}>
                      {pnl >= 0 ? "+" : "−"}${Math.abs(pnl).toFixed(2)}
                    </td>
                    <td className={pnlPct >= 0 ? "price-up" : "price-down"}>
                      {pnlPct >= 0 ? "+" : ""}{pnlPct.toFixed(2)}%
                    </td>
                    <td>
                      <button
                        type="button"
                        className="wl-add-to-cart-btn"
                        onClick={() => addToCart(stock.id, stock.symbol, stock.price, quantity)}
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
                ))}
              </tbody>
            </table>
          </div>
        )}
      </main>
    </>
  );
}
