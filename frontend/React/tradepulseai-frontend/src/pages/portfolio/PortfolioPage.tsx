import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { Header } from "../../components/Header";
import type { PortfolioHolding, PortfolioResponse } from "../../types/portfolio";
import { isUserAuthenticated } from "../../utils/auth";
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
    return `$${value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export function PortfolioPage() {
    const navigate = useNavigate();
    const { stocks } = useStocks();
    const [portfolio, setPortfolio] = useState<PortfolioResponse>(EMPTY_PORTFOLIO);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [sellingStockId, setSellingStockId] = useState<string | null>(null);
    const [sellQuantities, setSellQuantities] = useState<Record<string, number>>({});

    useEffect(() => {
        document.title = "Portfolio | TradePulseAI";
    }, []);

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

    const holdingsWithLivePrice = useMemo(() => {
        return portfolio.holdings.map((holding) => {
            const livePrice = stockPriceMap.get(holding.stockId) ?? holding.currentPrice;
            const marketValue = livePrice * holding.quantity;
            const unrealizedPnl = marketValue - holding.investedValue;
            const unrealizedPnlPercent =
                holding.investedValue > 0 ? (unrealizedPnl / holding.investedValue) * 100 : 0;

            return {
                ...holding,
                currentPrice: livePrice,
                marketValue,
                unrealizedPnl,
                unrealizedPnlPercent,
            };
        });
    }, [portfolio.holdings, stockPriceMap]);

    const handleSell = async (holding: PortfolioHolding) => {
        const quantity = sellQuantities[holding.stockId] ?? 1;
        if (quantity <= 0 || quantity > holding.quantity) {
            setError("Please provide a valid sell quantity.");
            return;
        }

        const currentPrice = stockPriceMap.get(holding.stockId) ?? holding.currentPrice;

        try {
            setSellingStockId(holding.stockId);
            setError(null);
            const updated = await sellPortfolioItem(holding.stockId, {
                quantity,
                price: currentPrice,
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
                                        holdingsWithLivePrice.reduce((sum, item) => sum + item.marketValue, 0),
                                    )}</strong>
                                </article>
                                <article className={`portfolio-stat-card ${portfolio.summary.totalUnrealizedPnl >= 0 ? "positive" : "negative"}`}>
                                    <span>Unrealized PnL</span>
                                    <strong>
                                        {portfolio.summary.totalUnrealizedPnl >= 0 ? "+" : ""}
                                        {formatCurrency(portfolio.summary.totalUnrealizedPnl)}
                                    </strong>
                                </article>
                                <article className={`portfolio-stat-card ${portfolio.summary.totalRealizedPnl >= 0 ? "positive" : "negative"}`}>
                                    <span>Realized PnL</span>
                                    <strong>
                                        {portfolio.summary.totalRealizedPnl >= 0 ? "+" : ""}
                                        {formatCurrency(portfolio.summary.totalRealizedPnl)}
                                    </strong>
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
                                                <tr key={holding.id}>
                                                    <td><strong>{holding.symbol}</strong></td>
                                                    <td>{holding.quantity}</td>
                                                    <td>{formatCurrency(holding.averageBuyPrice)}</td>
                                                    <td>{formatCurrency(holding.currentPrice)}</td>
                                                    <td>{formatCurrency(holding.investedValue)}</td>
                                                    <td>{formatCurrency(holding.marketValue)}</td>
                                                    <td className={holding.unrealizedPnl >= 0 ? "positive" : "negative"}>
                                                        {holding.unrealizedPnl >= 0 ? "+" : ""}
                                                        {formatCurrency(holding.unrealizedPnl)} ({holding.unrealizedPnlPercent.toFixed(2)}%)
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
                                                                disabled={sellingStockId === holding.stockId}
                                                            >
                                                                {Array.from({ length: holding.quantity }, (_, idx) => idx + 1).map((qty) => (
                                                                    <option key={`${holding.stockId}-${qty}`} value={qty}>{qty}</option>
                                                                ))}
                                                            </select>
                                                            <button
                                                                type="button"
                                                                onClick={() => void handleSell(holding)}
                                                                disabled={sellingStockId === holding.stockId}
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
                                            {portfolio.transactions.map((transaction) => (
                                                <tr key={transaction.id}>
                                                    <td>{transaction.transactionType}</td>
                                                    <td><strong>{transaction.symbol}</strong></td>
                                                    <td>{transaction.quantity}</td>
                                                    <td>{formatCurrency(transaction.price)}</td>
                                                    <td>{formatCurrency(transaction.grossAmount)}</td>
                                                    <td className={transaction.realizedPnl >= 0 ? "positive" : "negative"}>
                                                        {transaction.realizedPnl >= 0 ? "+" : ""}{formatCurrency(transaction.realizedPnl)}
                                                    </td>
                                                    <td>{new Date(transaction.executedAt).toLocaleString()}</td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                )}
                            </section>
                        </>
                    )}
                </section>
            </main>
        </>
    );
}
