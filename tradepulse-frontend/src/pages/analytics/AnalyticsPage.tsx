import { useEffect, useState } from "react";
import { Header } from "../../components/Header.tsx";
import { fetchAnalyticsNews } from "../../utils/stockInsightsApi";

type AnalyticsNewsItem = {
    stockId: number;
    symbol: string;
    tradingDate: string | null;
    news: string | null;
    newsCount: number | null;
};

function formatDate(value: string | null): string {
  if (!value) {
    return "--";
  }
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime())
    ? value
    : parsed.toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" });
}

export function AnalyticsPage() {
    const [news, setNews] = useState<AnalyticsNewsItem[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        document.title = "Analytics | TradePulse";
        fetchAnalyticsNews(12)
            .then(setNews)
            .finally(() => setLoading(false));
    }, []);

    return (
        <>
            <Header />
            <h1>Analytics</h1>
            <p>Latest market news.</p>
            <section style={{ marginTop: 16 }}>
                <h2>Top News</h2>
                {loading ? <p>Loading news...</p> : null}
                {!loading && news.length === 0 ? <p>No news available yet.</p> : null}
                {!loading && news.length > 0 ? (
                    <div style={{ display: "grid", gap: 10 }}>
                        {news.map((item) => (
                            <article key={`${item.stockId}-${item.tradingDate}-${item.news}`} style={{ border: "1px solid #e5e7eb", borderRadius: 10, padding: 12 }}>
                                <div style={{ display: "flex", justifyContent: "space-between", gap: 12 }}>
                                    <strong>{item.symbol}</strong>
                                    <small>{formatDate(item.tradingDate)}</small>
                                </div>
                                <p style={{ margin: "8px 0" }}>{item.news ?? "--"}</p>
                                <small>Articles: {item.newsCount ?? 0}</small>
                            </article>
                        ))}
                    </div>
                ) : null}
            </section>
        </>
    );
}
