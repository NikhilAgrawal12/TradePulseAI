import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { Header } from "../../components/Header.tsx";
import { useCart } from "../../context/CartContext";
import { useMarketStatus } from "../../context/MarketStatusContext";
import { useWatchlist } from "../../context/WatchlistContext";
import type { StockInsights } from "../../types/stockInsights";
import { formatMoney, formatPercent, formatSignedMoney } from "../../utils/money";
import { fetchStockInsights } from "../../utils/stockInsightsApi";
import { useStreamedStocks } from "../../utils/useStreamedStocks";
import "./WatchlistPage.css";

export function WatchlistPage() {
  useEffect(() => { document.title = "Watchlist | TradePulseAI"; }, []);

  const { addToCart } = useCart();
  const { watchlist, removeFromWatchlist } = useWatchlist();
  const { stocks: streamedStocks } = useStreamedStocks();
  const { sessionMeta } = useMarketStatus();
  const [search, setSearch] = useState("");
  const [performanceByStockId, setPerformanceByStockId] = useState<Record<string, StockInsights["currentPerformance"]>>({});


   // Fetch initial insights currentPerformance for all watchlist stocks
   const watchlistStockIds = useMemo(() => watchlist.map((entry) => entry.stockId), [watchlist]);

   useEffect(() => {
     if (watchlistStockIds.length === 0) {
       setPerformanceByStockId({});
       return;
     }

     let cancelled = false;

     const loadPerformanceData = async () => {
       const responses = await Promise.allSettled(watchlistStockIds.map((stockId) => fetchStockInsights(stockId)));
       if (cancelled) {
         return;
       }

       setPerformanceByStockId(() => {
         const next: Record<string, StockInsights["currentPerformance"]> = {};
         responses.forEach((result, index) => {
           if (result.status === "fulfilled") {
             next[watchlistStockIds[index]] = result.value.currentPerformance;
           }
         });
         return next;
       });
     };

     void loadPerformanceData();

     return () => {
       cancelled = true;
     };
   }, [watchlistStockIds]);

   // Apply real-time updates from streamed stocks
   useEffect(() => {
     if (streamedStocks.length === 0) {
       return;
     }

     const streamedStockMap = new Map(streamedStocks.map((stock) => [stock.id, stock]));

     setPerformanceByStockId((prev) => {
       const next = { ...prev };

       Object.entries(prev).forEach(([stockId, performance]) => {
         const streamedStock = streamedStockMap.get(stockId);
         if (!streamedStock) {
           return;
         }

         const livePrice = typeof streamedStock.price === "number" ? streamedStock.price : null;
         const liveChangePercent = typeof streamedStock.changePercent === "number" ? streamedStock.changePercent : null;

         const nextCurrentPrice = livePrice == null ? performance.currentPrice : livePrice;
         const nextPreviousClose = performance.previousClose;
         const nextDailyChange =
           nextCurrentPrice != null && nextPreviousClose != null
             ? nextCurrentPrice - nextPreviousClose
             : performance.dailyChange;

         next[stockId] = {
           ...performance,
           currentPrice: nextCurrentPrice,
           previousClose: nextPreviousClose,
           dailyChange: nextDailyChange,
           dailyChangePercent: liveChangePercent == null ? performance.dailyChangePercent : liveChangePercent,
         };
       });

       return next;
     });
   }, [streamedStocks]);

   const streamedStockMap = useMemo(() => new Map(streamedStocks.map((stock) => [stock.id, stock])), [streamedStocks]);

   const watchlistStocks = useMemo(
     () =>
       watchlist.flatMap((entry) => {
         const stock = streamedStockMap.get(entry.stockId);
         if (!stock) {
           return [];
         }

         return [{ ...entry, stock }];
       }),
     [streamedStockMap, watchlist],
   );

   const filtered = useMemo(() => {
     const q = search.trim().toLowerCase();
     if (!q) return watchlistStocks;
     return watchlistStocks.filter(({ stock }) =>
       [stock.symbol, stock.name, stock.exchange, stock.market].join(" ").toLowerCase().includes(q),
     );
   }, [search, watchlistStocks]);

   const handleRemove = (stockId: string) => void removeFromWatchlist(stockId);

   const formatMaybeMoney = (value: number | null | undefined) => (typeof value === "number" ? `$${formatMoney(value)}` : "--");
   const formatMaybeSignedMoney = (value: number | null | undefined) => (typeof value === "number" ? formatSignedMoney(value) : "--");
   const formatMaybePercent = (value: number | null | undefined) => (typeof value === "number" ? `${formatPercent(value)}%` : "--");

   return (
     <>
       <Header />
       <main className="wl-page">
         {/* ── Page Header ── */}
         <div className="wl-header">
           <div>
             <h1 className="wl-title">Your Watchlist</h1>
             <p className="wl-subtitle">Tracking <span className="wl-count">{watchlistStocks.length}</span> stocks</p>
           </div>
         </div>

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
                    <th>Previous Day Close</th>
                    <th>Daily Change ($)</th>
                    <th>Daily Change (%)</th>
                    <th></th>
                    <th></th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(({ stock }) => {
                    const currentPerformance = performanceByStockId[stock.id];
                    const dailyChangePercent = currentPerformance?.dailyChangePercent;

                    return (
                      <tr key={stock.id}>
                        <td><strong>{stock.symbol}</strong></td>
                        <td><span className="wl-name">{stock.name ?? "N/A"}</span><span className="wl-sector">{stock.exchange ?? "N/A"}</span></td>
                        <td>{formatMaybeMoney(currentPerformance?.currentPrice)}</td>
                        <td>{formatMaybeMoney(currentPerformance?.previousClose)}</td>
                        <td className={typeof currentPerformance?.dailyChange === "number" ? (currentPerformance.dailyChange >= 0 ? "price-up" : "price-down") : ""}>
                          {formatMaybeSignedMoney(currentPerformance?.dailyChange)}
                        </td>
                        <td className={typeof dailyChangePercent === "number" ? (dailyChangePercent >= 0 ? "price-up" : "price-down") : ""}>
                          {formatMaybePercent(dailyChangePercent)}
                        </td>
                       <td>
                         <Link className="wl-insights-btn" to={`/stocks/${stock.id}/insights`}>
                           Insights
                         </Link>
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
