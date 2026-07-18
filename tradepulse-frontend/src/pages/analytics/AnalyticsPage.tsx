import { useEffect, useState } from "react";
import { Header } from "../../components/Header.tsx";
import { fetchAnalyticsNews } from "../../utils/stockInsightsApi";
import "./AnalyticsPage.css";

type AnalyticsNewsItem = {
    stockId: number;
    symbol: string;
    tradingDate: string | null;
    news: string | null;
};

type ParsedHeadline = {
  headline: string;
  url: string | null;
  publisher: string | null;
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

function splitHeadlines(news: string | null): ParsedHeadline[] {
  if (!news) {
    return [];
  }

  try {
    const parsed = JSON.parse(news) as Array<{ headline?: string; url?: string | null; publisher?: string | null }>;
    if (Array.isArray(parsed)) {
      return parsed
        .map((item) => ({
          headline: (item.headline ?? "").trim(),
          url: item.url?.trim() || null,
          publisher: item.publisher?.trim() || null,
        }))
        .filter((item) => item.headline.length > 0)
        .slice(0, 3);
    }
  } catch {
    // Fallback to legacy plain-text format.
  }

  return news
    .split("|")
    .map((headline) => ({ headline: headline.trim(), url: null, publisher: null }))
    .filter((item) => item.headline.length > 0)
    .slice(0, 3);
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
            <main className="analytics-page">
              <div className="analytics-shell">
                <section className="analytics-hero">
                  <h1>Analytics</h1>
                  <p>Latest market headlines with stock-first context.</p>
                </section>

                <section className="analytics-news-section" aria-live="polite">
                  <div className="analytics-news-header">
                    <h2>Top News</h2>
                    {!loading && news.length > 0 ? <span>{news.length} stories</span> : null}
                  </div>

                  {loading ? <p className="analytics-news-state">Loading news...</p> : null}
                  {!loading && news.length === 0 ? <p className="analytics-news-state">No news available yet.</p> : null}

                  {!loading && news.length > 0 ? (
                    <div className="analytics-news-grid">
                      {news.map((item) => {
                        const headlines = splitHeadlines(item.news);
                        return (
                          <article key={`${item.stockId}-${item.tradingDate}-${item.news}`} className="analytics-news-card">
                            <header className="analytics-news-card-head">
                              <strong>{item.symbol}</strong>
                              <small>{formatDate(item.tradingDate)}</small>
                            </header>

                            {headlines.length > 0 ? (
                              <ol className="analytics-headline-list">
                                {headlines.map((headline, index) => (
                                  <li key={`${item.stockId}-${index}-${headline.headline}`}>
                                    <span className="analytics-headline-rank">{index + 1}</span>
                                    <div>
                                      {headline.url ? (
                                        <a
                                          href={headline.url}
                                          target="_blank"
                                          rel="noopener noreferrer"
                                          title={headline.headline}
                                          className="analytics-headline-link"
                                        >
                                          {headline.headline}
                                        </a>
                                      ) : (
                                        <p title={headline.headline}>{headline.headline}</p>
                                      )}
                                      <div className="analytics-headline-meta">
                                        <small className="analytics-headline-date">{formatDate(item.tradingDate)}</small>
                                        {headline.publisher ? <small className="analytics-headline-publisher">{headline.publisher}</small> : null}
                                      </div>
                                    </div>
                                  </li>
                                ))}
                              </ol>
                            ) : (
                              <p className="analytics-headline-empty">No headline text available.</p>
                            )}
                          </article>
                        );
                      })}
                    </div>
                  ) : null}
                </section>
              </div>
            </main>
        </>
    );
}
