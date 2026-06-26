import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { Header } from "../../components/Header";
import type { PortfolioHolding, PortfolioResponse } from "../../types/portfolio";
import { isUserAuthenticated } from "../../utils/auth";
import { formatEasternDateTime } from "../../utils/dateTime";
import { getMarketSession, getMarketSessionFromBackend, subscribeToMarketStatus, type SessionMeta } from "../../utils/marketSession";
import { formatMoney, formatPercent, formatSignedCurrency, toMoney } from "../../utils/money";
import { sellPortfolioItem, fetchPortfolio } from "../../utils/portfolioApi";
import { useStocks } from "../../utils/useStocks";
import "./PortfolioPage.css";

const EMPTY_PORTFOLIO: PortfolioResponse = {
    summary: {
        totalPositions: 0,
        totalQuantity: 0,
        totalInvestedValue: 0,
        totalMarketValue: 0,
        totalUnrealizedPnl: 0,
        totalUnrealizedPnlPercent: 0,
        totalRealizedPnl: 0,
    },
    holdings: [],
    transactions: [],
};

function formatCurrency(value: number) {
    return `$${formatMoney(value)}`;
}

export function PortfolioPage() {
    const navigate = useNavigate();
    const { stocks } = useStocks();
    const [portfolio, setPortfolio] = useState<PortfolioResponse>(EMPTY_PORTFOLIO);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [sellingStockId, setSellingStockId] = useState<string | null>(null);
    const [sellQuantities, setSellQuantities] = useState<Record<string, number>>({});
    const [sessionMeta, setSessionMeta] = useState<SessionMeta>(() => getMarketSession());
    const [portfolioNotice, setPortfolioNotice] = useState<string | null>(null);

    const closedMarketMessage =
        "Markets are currently closed. Trading is available Monday through Friday, excluding market holidays, during the following hours (ET): Pre-Market: 4:00 AM – 9:30 AM, Regular Market: 9:30 AM – 4:00 PM, and After-Hours: 4:00 PM – 8:00 PM. Please try again when trading resumes at 4:00 AM ET on the next trading day.";

    useEffect(() => {
        document.title = "Portfolio | TradePulseAI";
    }, []);

    useEffect(() => {
        let cancelled = false;

        const bootstrapSession = async () => {
            const next = await getMarketSessionFromBackend();
            if (!cancelled) {
                setSessionMeta(next);
            }
        };

        void bootstrapSession();
        const unsubscribe = subscribeToMarketStatus((next) => {
            if (!cancelled) {
                setSessionMeta(next);
            }
        });

        return () => {
            cancelled = true;
            unsubscribe();
        };
    }, []);

    const isMarketClosed = sessionMeta.session === "closed";
    const visibleNotice = portfolioNotice ?? (isMarketClosed ? closedMarketMessage : null);

    useEffect(() => {
        if (!isUserAuthenticated()) {
            navigate("/login");
            return;
        }

        const loadPortfolio = async () => {
            try {
                setLoading(true);
                const data = await fetchPortfolio();
                setPortfolio(data);
            } catch (loadError) {
                const message = loadError instanceof Error ? loadError.message : "Failed to load portfolio.";
                setError(message);
            } finally {
                setLoading(false);
            }
        };

        void loadPortfolio();
    }, [navigate]);

    const stockPriceMap = useMemo(
        () => new Map(stocks.map((stock) => [stock.id, stock.price])),
        [stocks],
    );

    const stockSymbolMap = useMemo(
        () => new Map(stocks.map((stock) => [stock.id, stock.symbol])),
        [stocks],
    );

    const holdingsWithLivePrice = useMemo(() => {
        return portfolio.holdings.map((holding) => {
            const livePrice = stockPriceMap.get(holding.stockId) ?? holding.currentPrice;
            const symbol = stockSymbolMap.get(holding.stockId) ?? holding.symbol ?? holding.stockId;
            const marketValue = toMoney(livePrice * holding.quantity);
            const unrealizedPnl = toMoney(marketValue - holding.investedValue);
            const unrealizedPnlPercent =
                holding.investedValue > 0 ? toMoney((unrealizedPnl / holding.investedValue) * 100) : 0;

            return {
                ...holding,
                symbol,
                currentPrice: toMoney(livePrice),
                marketValue,
                unrealizedPnl,
                unrealizedPnlPercent,
            };
        });
    }, [portfolio.holdings, stockPriceMap, stockSymbolMap]);

    const livePortfolioTotals = useMemo(() => {
        const totalMarketValue = holdingsWithLivePrice.reduce((sum, item) => sum + item.marketValue, 0);
        const totalInvestedValue = holdingsWithLivePrice.reduce((sum, item) => sum + item.investedValue, 0);
        const totalUnrealizedPnl = toMoney(totalMarketValue - totalInvestedValue);

        return {
            totalMarketValue: toMoney(totalMarketValue),
            totalUnrealizedPnl,
        };
    }, [holdingsWithLivePrice]);

    const transactionsWithSymbols = useMemo(
        () => portfolio.transactions.map((transaction) => ({
            ...transaction,
            symbol: stockSymbolMap.get(transaction.stockId) ?? transaction.symbol ?? transaction.stockId,
        })),
        [portfolio.transactions, stockSymbolMap],
    );

    const handleSell = async (holding: PortfolioHolding) => {
        if (isMarketClosed) {
            setPortfolioNotice(closedMarketMessage);
            return;
        }

        const quantity = sellQuantities[holding.stockId] ?? 1;
        if (quantity <= 0 || quantity > holding.quantity) {
            setError("Please provide a valid sell quantity.");
            return;
        }

        const currentPrice = stockPriceMap.get(holding.stockId) ?? holding.currentPrice;

        try {
            setSellingStockId(holding.stockId);
            setError(null);
            setPortfolioNotice(null);
            const updated = await sellPortfolioItem(holding.stockId, {
                quantity,
                price: toMoney(currentPrice),
            });
            setPortfolio(updated);
        } catch (sellError) {
            const message = sellError instanceof Error ? sellError.message : "Failed to sell stock.";
            setError(message);
        } finally {
            setSellingStockId(null);
        }
    };

    return (
        <>
            <Header />
            <main className="portfolio-page">
                <section className="portfolio-shell">
                    <h1>My Portfolio</h1>

                    <div className="portfolio-market-status" role="status" aria-live="polite">
                        <span>Market Status</span>
                        <span className={`portfolio-session-badge ${sessionMeta.cssClass}`}>{sessionMeta.label}</span>
                    </div>

                    {visibleNotice && <p className="portfolio-notice">{visibleNotice}</p>}

                    {loading && <p>Loading portfolio...</p>}
                    {error && <p className="portfolio-error">{error}</p>}

                    {!loading && (
                        <>
                            <section className="portfolio-stats">
                                <article className="portfolio-stat-card">
                                    <span>Positions</span>
                                    <strong>{portfolio.summary.totalPositions}</strong>
                                </article>
                                <article className="portfolio-stat-card">
                                    <span>Total Invested</span>
                                    <strong>{formatCurrency(portfolio.summary.totalInvestedValue)}</strong>
                                </article>
                                <article className="portfolio-stat-card">
                                    <span>Market Value</span>
                                    <strong>{formatCurrency(
                                        livePortfolioTotals.totalMarketValue,
                                    )}</strong>
                                </article>
                                <article className={`portfolio-stat-card ${livePortfolioTotals.totalUnrealizedPnl >= 0 ? "positive" : "negative"}`}>
                                    <span>Unrealized PnL</span>
                                    <strong>{formatSignedCurrency(livePortfolioTotals.totalUnrealizedPnl)}</strong>
                                </article>
                                <article className={`portfolio-stat-card ${portfolio.summary.totalRealizedPnl >= 0 ? "positive" : "negative"}`}>
                                    <span>Realized PnL</span>
                                    <strong>{formatSignedCurrency(portfolio.summary.totalRealizedPnl)}</strong>
                                </article>
                            </section>

                            <section className="portfolio-panel">
                                <h2>Current Holdings</h2>
                                {holdingsWithLivePrice.length === 0 ? (
                                    <p>No holdings yet. Complete an order to build your portfolio.</p>
                                ) : (
                                    <table className="portfolio-table">
                                        <thead>
                                            <tr>
                                                <th>Symbol</th>
                                                <th>Qty</th>
                                                <th>Avg Buy</th>
                                                <th>Current</th>
                                                <th>Invested</th>
                                                <th>Market Value</th>
                                                <th>Unrealized PnL</th>
                                                <th>Sell</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {holdingsWithLivePrice.map((holding) => (
                                                <tr key={holding.stockId}>
                                                    <td><strong>{holding.symbol}</strong></td>
                                                    <td>{holding.quantity}</td>
                                                    <td>{formatCurrency(holding.averageBuyPrice)}</td>
                                                    <td>{formatCurrency(holding.currentPrice)}</td>
                                                    <td>{formatCurrency(holding.investedValue)}</td>
                                                    <td>{formatCurrency(holding.marketValue)}</td>
                                                    <td className={holding.unrealizedPnl >= 0 ? "positive" : "negative"}>
                                                        {formatSignedCurrency(holding.unrealizedPnl)} ({formatPercent(holding.unrealizedPnlPercent, false)}%)
                                                    </td>
                                                    <td>
                                                        <div className="portfolio-sell-action">
                                                            <select
                                                                value={sellQuantities[holding.stockId] ?? 1}
                                                                aria-label={`Sell quantity for ${holding.symbol}`}
                                                                onChange={(event) => {
                                                                    setSellQuantities((current) => ({
                                                                        ...current,
                                                                        [holding.stockId]: Number(event.target.value),
                                                                    }));
                                                                }}
                                                                disabled={sellingStockId === holding.stockId || isMarketClosed}
                                                            >
                                                                {Array.from({ length: holding.quantity }, (_, idx) => idx + 1).map((qty) => (
                                                                    <option key={`${holding.stockId}-${qty}`} value={qty}>{qty}</option>
                                                                ))}
                                                            </select>
                                                            <button
                                                                type="button"
                                                                onClick={() => void handleSell(holding)}
                                                                disabled={sellingStockId === holding.stockId || isMarketClosed}
                                                            >
                                                                {sellingStockId === holding.stockId ? "Selling..." : "Sell"}
                                                            </button>
                                                        </div>
                                                    </td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                )}
                            </section>

                            <section className="portfolio-panel">
                                <h2>Transactions</h2>
                                {portfolio.transactions.length === 0 ? (
                                    <p>No transactions yet.</p>
                                ) : (
                                    <>
                                    <table className="portfolio-table">
                                        <thead>
                                            <tr>
                                                <th>Type</th>
                                                <th>Symbol</th>
                                                <th>Qty</th>
                                                <th>Price</th>
                                                <th>Gross</th>
                                                <th>Realized PnL</th>
                                                <th>Executed</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {transactionsWithSymbols.map((transaction) => {
                                                const isSell = String(transaction.transactionType).toUpperCase() === "SELL";
                                                const rowClass = isSell ? "tx-row-sell" : "tx-row-buy";

                                                return (
                                                    <tr key={transaction.transactionId} className={rowClass}>
                                                        <td>
                                                            <span className={isSell ? "tx-badge tx-badge-sell" : "tx-badge tx-badge-buy"}>
                                                                {transaction.transactionType}
                                                            </span>
                                                        </td>
                                                        <td><strong>{transaction.symbol}</strong></td>
                                                        <td>{transaction.quantity}</td>
                                                        <td>{formatCurrency(transaction.price)}</td>
                                                        <td>{formatCurrency(transaction.grossAmount)}</td>
                                                        <td className={isSell ? (transaction.realizedPnl >= 0 ? "positive" : "negative") : ""}>
                                                            {isSell
                                                                ? formatSignedCurrency(transaction.realizedPnl)
                                                                : "-"}
                                                        </td>
                                                        <td>{formatEasternDateTime(transaction.executedAt)}</td>
                                                    </tr>
                                                );
                                            })}
                                        </tbody>
                                    </table>

                                    {/* ── Legend ── */}
                                    <div className="tx-legend">
                                        <span className="tx-legend-item">
                                            <span className="tx-badge tx-badge-buy">BUY</span>
                                            Stock purchased
                                        </span>
                                        <span className="tx-legend-item">
                                            <span className="tx-badge tx-badge-sell">SELL</span>
                                            Stock sold
                                        </span>
                                    </div>
                                    </>
                                )}
                            </section>
                        </>
                    )}
                </section>
            </main>
        </>
    );
}
