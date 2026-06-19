import { useEffect, useMemo, useState, type ReactNode } from "react";
import { Link, useParams } from "react-router";
import { Header } from "../../components/Header.tsx";
import type { MonthlyReturnHeatmapCell, StockHistoryPoint, StockInsights } from "../../types/stockInsights";
import { fetchStockInsights } from "../../utils/stockInsightsApi";
import { formatMoney, formatPercent, formatSignedCurrency } from "../../utils/money";
import "./StockInsightsPage.css";

type RangeKey = "1M" | "3M" | "6M" | "1Y" | "3Y";

type LineDefinition = {
  key: keyof StockHistoryPoint;
  label: string;
  color: string;
};

const RANGE_DAYS: Record<RangeKey, number> = {
  "1M": 31,
  "3M": 92,
  "6M": 183,
  "1Y": 366,
  "3Y": 1096,
};

const MONTH_LABELS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

function formatDateLabel(value: string | null | undefined): string {
  if (!value) {
    return "--";
  }
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" });
}

function formatDateShort(value: string | null | undefined): string {
  if (!value) {
    return "--";
  }
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

function formatMaybeMoney(value: number | null | undefined): string {
  return value == null ? "--" : `$${formatMoney(value)}`;
}

function formatMaybeSignedMoney(value: number | null | undefined): string {
  return value == null ? "--" : formatSignedCurrency(value);
}

function formatMaybePercent(value: number | null | undefined): string {
  return value == null ? "--" : `${formatPercent(value)}%`;
}

function formatMaybePlain(value: number | null | undefined): string {
  return value == null ? "--" : formatMoney(value);
}

function formatMaybeRatio(value: number | null | undefined): string {
  return value == null ? "--" : `${formatMoney(value)}x`;
}

function formatVolume(value: number | null | undefined): string {
  return value == null ? "--" : Math.round(value).toLocaleString();
}

function filterHistoryByRange(history: StockHistoryPoint[], range: RangeKey): StockHistoryPoint[] {
  if (history.length === 0) {
    return [];
  }

  const latest = history[history.length - 1]?.tradingDate;
  if (!latest) {
    return history;
  }

  const latestDate = new Date(latest);
  if (Number.isNaN(latestDate.getTime())) {
    return history;
  }

  const cutoff = new Date(latestDate);
  cutoff.setDate(cutoff.getDate() - RANGE_DAYS[range]);
  return history.filter((point) => {
    if (!point.tradingDate) {
      return false;
    }
    const pointDate = new Date(point.tradingDate);
    return !Number.isNaN(pointDate.getTime()) && pointDate >= cutoff;
  });
}

function getSeriesBounds(data: StockHistoryPoint[], keys: Array<keyof StockHistoryPoint>): { min: number; max: number } {
  const values = data.flatMap((point) =>
    keys
      .map((key) => point[key])
      .filter((value): value is number => typeof value === "number" && Number.isFinite(value)),
  );

  if (values.length === 0) {
    return { min: 0, max: 1 };
  }

  const min = Math.min(...values);
  const max = Math.max(...values);
  if (min === max) {
    return { min: min - 1, max: max + 1 };
  }

  const padding = (max - min) * 0.08;
  return { min: min - padding, max: max + padding };
}

function buildLinePath(data: StockHistoryPoint[], key: keyof StockHistoryPoint, width: number, height: number, min: number, max: number): string {
  if (data.length === 0) {
    return "";
  }

  const innerWidth = width - 24;
  const innerHeight = height - 24;
  return data
    .map((point, index) => {
      const rawValue = point[key];
      if (typeof rawValue !== "number" || !Number.isFinite(rawValue)) {
        return null;
      }
      const x = 12 + (index / Math.max(data.length - 1, 1)) * innerWidth;
      const y = 12 + innerHeight - ((rawValue - min) / Math.max(max - min, 1e-9)) * innerHeight;
      return `${index === 0 ? "M" : "L"}${x},${y}`;
    })
    .filter((value): value is string => value !== null)
    .join(" ");
}

function ChartCard({ title, children, subtitle }: { title: string; subtitle?: string; children: ReactNode }) {
  return (
    <section className="insights-chart-card">
      <div className="insights-chart-head">
        <div>
          <h3>{title}</h3>
          {subtitle ? <p>{subtitle}</p> : null}
        </div>
      </div>
      {children}
    </section>
  );
}

function MetricSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="insights-section-card">
      <h3>{title}</h3>
      {children}
    </section>
  );
}

function MetricGrid({ items }: { items: Array<{ label: string; value: string; tone?: "positive" | "negative" }> }) {
  return (
    <div className="insights-metric-grid">
      {items.map((item) => (
        <div key={item.label} className="insights-metric-card">
          <span>{item.label}</span>
          <strong className={item.tone === "positive" ? "metric-positive" : item.tone === "negative" ? "metric-negative" : undefined}>{item.value}</strong>
        </div>
      ))}
    </div>
  );
}

function MultiLineChart({ data, lines, valueFormatter }: { data: StockHistoryPoint[]; lines: LineDefinition[]; valueFormatter?: (value: number) => string }) {
  const width = 900;
  const height = 280;
  const bounds = useMemo(() => getSeriesBounds(data, lines.map((line) => line.key)), [data, lines]);
  const yTicks = useMemo(() => {
    return Array.from({ length: 5 }, (_, index) => {
      const ratio = index / 4;
      return bounds.max - (bounds.max - bounds.min) * ratio;
    });
  }, [bounds]);

  return (
    <div className="insights-chart-shell">
      <svg viewBox={`0 0 ${width} ${height}`} className="insights-svg-chart" role="img" aria-label="Stock line chart">
        {yTicks.map((tick, index) => {
          const y = 12 + (index / 4) * (height - 24);
          return (
            <g key={tick}>
              <line x1="12" y1={y} x2={width - 12} y2={y} className="chart-grid-line" />
              <text x="16" y={y - 4} className="chart-axis-label">
                {valueFormatter ? valueFormatter(tick) : formatMoney(tick)}
              </text>
            </g>
          );
        })}
        {lines.map((line) => (
          <path key={line.label} d={buildLinePath(data, line.key, width, height, bounds.min, bounds.max)} fill="none" stroke={line.color} strokeWidth="3" strokeLinejoin="round" strokeLinecap="round" />
        ))}
      </svg>
      <div className="insights-chart-legend">
        {lines.map((line) => (
          <span key={line.label}><i style={{ backgroundColor: line.color }} />{line.label}</span>
        ))}
      </div>
    </div>
  );
}

function CandlestickChart({ data }: { data: StockHistoryPoint[] }) {
  const width = 900;
  const height = 280;
  const bounds = useMemo(() => getSeriesBounds(data, ["low", "high"]), [data]);
  const innerWidth = width - 24;
  const innerHeight = height - 24;

  return (
    <div className="insights-chart-shell">
      <svg viewBox={`0 0 ${width} ${height}`} className="insights-svg-chart" role="img" aria-label="Stock candlestick chart">
        {data.map((point, index) => {
          if ([point.open, point.high, point.low, point.close].some((value) => typeof value !== "number")) {
            return null;
          }
          const x = 12 + (index / Math.max(data.length - 1, 1)) * innerWidth;
          const candleWidth = Math.max(innerWidth / Math.max(data.length * 1.8, 24), 2);
          const highY = 12 + innerHeight - (((point.high as number) - bounds.min) / (bounds.max - bounds.min)) * innerHeight;
          const lowY = 12 + innerHeight - (((point.low as number) - bounds.min) / (bounds.max - bounds.min)) * innerHeight;
          const openY = 12 + innerHeight - (((point.open as number) - bounds.min) / (bounds.max - bounds.min)) * innerHeight;
          const closeY = 12 + innerHeight - (((point.close as number) - bounds.min) / (bounds.max - bounds.min)) * innerHeight;
          const color = (point.close as number) >= (point.open as number) ? "#16a34a" : "#dc2626";
          const bodyY = Math.min(openY, closeY);
          const bodyHeight = Math.max(Math.abs(closeY - openY), 2);

          return (
            <g key={`${point.tradingDate}-${index}`}>
              <line x1={x} y1={highY} x2={x} y2={lowY} stroke={color} strokeWidth="1.5" />
              <rect x={x - candleWidth / 2} y={bodyY} width={candleWidth} height={bodyHeight} fill={color} rx="1" />
            </g>
          );
        })}
      </svg>
    </div>
  );
}

function VolumeChart({ data }: { data: StockHistoryPoint[] }) {
  const width = 900;
  const height = 220;
  const maxVolume = Math.max(...data.map((point) => point.volume ?? 0), 1);
  const innerWidth = width - 24;
  const innerHeight = height - 24;

  return (
    <div className="insights-chart-shell">
      <svg viewBox={`0 0 ${width} ${height}`} className="insights-svg-chart" role="img" aria-label="Stock volume chart">
        {data.map((point, index) => {
          const volume = point.volume ?? 0;
          const x = 12 + (index / Math.max(data.length - 1, 1)) * innerWidth;
          const barWidth = Math.max(innerWidth / Math.max(data.length * 1.6, 20), 2);
          const barHeight = (volume / maxVolume) * innerHeight;
          const y = 12 + innerHeight - barHeight;
          const isPositive = (point.dailyReturnPercent ?? 0) >= 0;
          return <rect key={`${point.tradingDate}-${index}`} x={x - barWidth / 2} y={y} width={barWidth} height={barHeight} fill={isPositive ? "rgba(37, 99, 235, 0.78)" : "rgba(148, 163, 184, 0.88)"} rx="1" />;
        })}
      </svg>
    </div>
  );
}

function ReturnHistogram({ history }: { history: StockHistoryPoint[] }) {
  const returns = history
    .map((point) => point.dailyReturnPercent)
    .filter((value): value is number => typeof value === "number" && Number.isFinite(value));

  const buckets = useMemo(() => {
    if (returns.length === 0) {
      return [] as Array<{ label: string; count: number }>;
    }
    const min = Math.min(...returns);
    const max = Math.max(...returns);
    const bucketCount = 12;
    const width = Math.max((max - min) / bucketCount, 0.5);
    const counts = Array.from({ length: bucketCount }, () => 0);

    returns.forEach((value) => {
      const bucketIndex = Math.min(Math.floor((value - min) / width), bucketCount - 1);
      counts[Math.max(bucketIndex, 0)] += 1;
    });

    return counts.map((count, index) => {
      const start = min + index * width;
      const end = start + width;
      return { label: `${formatMoney(start)}% to ${formatMoney(end)}%`, count };
    });
  }, [returns]);

  const maxCount = Math.max(...buckets.map((bucket) => bucket.count), 1);

  return (
    <div className="histogram-grid">
      {buckets.map((bucket) => (
        <div key={bucket.label} className="histogram-item">
          <div className="histogram-bar-shell">
            <div className="histogram-bar" style={{ height: `${(bucket.count / maxCount) * 100}%` }} />
          </div>
          <strong>{bucket.count}</strong>
          <span>{bucket.label}</span>
        </div>
      ))}
    </div>
  );
}

function MonthlyReturnsHeatmap({ cells }: { cells: MonthlyReturnHeatmapCell[] }) {
  const years = Array.from(new Set(cells.map((cell) => cell.year))).sort((left, right) => right - left);
  const cellMap = new Map(cells.map((cell) => [`${cell.year}-${cell.month}`, cell.returnPercent]));

  const getCellClass = (value: number | null | undefined) => {
    if (value == null) return "heatmap-cell heatmap-neutral";
    if (value >= 8) return "heatmap-cell heatmap-strong-positive";
    if (value > 0) return "heatmap-cell heatmap-positive";
    if (value <= -8) return "heatmap-cell heatmap-strong-negative";
    if (value < 0) return "heatmap-cell heatmap-negative";
    return "heatmap-cell heatmap-neutral";
  };

  return (
    <div className="heatmap-table">
      <div className="heatmap-row heatmap-header">
        <span />
        {MONTH_LABELS.map((label) => <strong key={label}>{label}</strong>)}
      </div>
      {years.map((year) => (
        <div key={year} className="heatmap-row">
          <strong>{year}</strong>
          {MONTH_LABELS.map((_, index) => {
            const value = cellMap.get(`${year}-${index + 1}`) ?? null;
            return (
              <div key={`${year}-${index + 1}`} className={getCellClass(value)} title={value == null ? `${year} ${MONTH_LABELS[index]}: no data` : `${year} ${MONTH_LABELS[index]}: ${formatMaybePercent(value)}`}>
                {value == null ? "--" : `${value > 0 ? "+" : ""}${formatMoney(value)}%`}
              </div>
            );
          })}
        </div>
      ))}
    </div>
  );
}

export function StockInsightsPage() {
  const { stockId } = useParams();
  const [insights, setInsights] = useState<StockInsights | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedRange, setSelectedRange] = useState<RangeKey>("6M");

  useEffect(() => {
    document.title = "Stock Insights | TradePulseAI";
  }, []);

  useEffect(() => {
    let mounted = true;

    const loadInsights = async () => {
      if (!stockId) {
        setError("Stock id is missing.");
        setLoading(false);
        return;
      }

      try {
        setLoading(true);
        const nextInsights = await fetchStockInsights(stockId);
        if (!mounted) {
          return;
        }
        setInsights(nextInsights);
        setError(null);
      } catch {
        if (!mounted) {
          return;
        }
        setError("Unable to load stock insights right now. Please try again shortly.");
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    };

    void loadInsights();

    return () => {
      mounted = false;
    };
  }, [stockId]);

  const rangeHistory = useMemo(() => filterHistoryByRange(insights?.history ?? [], selectedRange), [insights?.history, selectedRange]);

  const summaryTone = (value: number | null | undefined): "positive" | "negative" | undefined => {
    if (value == null) return undefined;
    if (value > 0) return "positive";
    if (value < 0) return "negative";
    return undefined;
  };

  const toneClass = (value: number | null | undefined): string => {
    const tone = summaryTone(value);
    if (tone === "positive") {
      return "metric-positive";
    }
    if (tone === "negative") {
      return "metric-negative";
    }
    return "";
  };

  const marketStateLabel = useMemo(() => {
    if (!insights?.trendMetrics) {
      return "--";
    }
    if (insights.trendMetrics.goldenCross) {
      return "Golden cross active";
    }
    if (insights.trendMetrics.deathCross) {
      return "Death cross active";
    }
    return "Trend neutral";
  }, [insights?.trendMetrics]);

  return (
    <>
      <Header />
      <main className="stock-insights-page">
        <div className="stock-insights-shell">
          <div className="stock-insights-topbar">
            <Link to="/" className="insights-back-link">← Back to stocks</Link>
          </div>

          {loading ? (
            <div className="insights-state-card"><p>Loading stock insights...</p></div>
          ) : error || !insights ? (
            <div className="insights-state-card error"><p>{error ?? "Unable to load stock insights."}</p></div>
          ) : (
            <>
              <section className="insights-hero-card">
                <div>
                  <p className="insights-eyebrow">Stock Overview Statistics</p>
                  <h1>{insights.symbol} · {insights.name}</h1>
                  <p className="insights-subtitle">
                    {insights.exchange ?? "Exchange unavailable"}
                    {insights.market ? ` • ${insights.market}` : ""}
                    {insights.lastUpdated ? ` • Updated ${formatDateLabel(insights.lastUpdated)}` : ""}
                  </p>
                </div>
                <div className="insights-hero-price">
                  <strong>{formatMaybeMoney(insights.currentPerformance.currentPrice)}</strong>
                  <span className={toneClass(insights.currentPerformance.dailyChangePercent)}>
                    {formatMaybeSignedMoney(insights.currentPerformance.dailyChange)} · {formatMaybePercent(insights.currentPerformance.dailyChangePercent)}
                  </span>
                </div>
              </section>

              <MetricSection title="Current Performance">
                <MetricGrid
                  items={[
                    { label: "Current Price", value: formatMaybeMoney(insights.currentPerformance.currentPrice) },
                    { label: "Previous Close", value: formatMaybeMoney(insights.currentPerformance.previousClose) },
                    { label: "Daily Change ($)", value: formatMaybeSignedMoney(insights.currentPerformance.dailyChange), tone: summaryTone(insights.currentPerformance.dailyChange) },
                    { label: "Daily Change (%)", value: formatMaybePercent(insights.currentPerformance.dailyChangePercent), tone: summaryTone(insights.currentPerformance.dailyChangePercent) },
                  ]}
                />
              </MetricSection>

              <MetricSection title="52-Week Metrics">
                <MetricGrid
                  items={[
                    { label: "52-Week High", value: formatMaybeMoney(insights.metrics52Week.high52Week) },
                    { label: "52-Week Low", value: formatMaybeMoney(insights.metrics52Week.low52Week) },
                    { label: "Distance from 52-Week High", value: formatMaybePercent(insights.metrics52Week.distanceFromHighPercent), tone: summaryTone(insights.metrics52Week.distanceFromHighPercent) },
                    { label: "Distance from 52-Week Low", value: formatMaybePercent(insights.metrics52Week.distanceFromLowPercent), tone: summaryTone(insights.metrics52Week.distanceFromLowPercent) },
                  ]}
                />
              </MetricSection>

              <MetricSection title="Returns">
                <MetricGrid
                  items={[
                    { label: "1 Week Return", value: formatMaybePercent(insights.returns.oneWeekReturn), tone: summaryTone(insights.returns.oneWeekReturn) },
                    { label: "1 Month Return", value: formatMaybePercent(insights.returns.oneMonthReturn), tone: summaryTone(insights.returns.oneMonthReturn) },
                    { label: "3 Month Return", value: formatMaybePercent(insights.returns.threeMonthReturn), tone: summaryTone(insights.returns.threeMonthReturn) },
                    { label: "6 Month Return", value: formatMaybePercent(insights.returns.sixMonthReturn), tone: summaryTone(insights.returns.sixMonthReturn) },
                    { label: "1 Year Return", value: formatMaybePercent(insights.returns.oneYearReturn), tone: summaryTone(insights.returns.oneYearReturn) },
                    { label: "3 Year Return", value: formatMaybePercent(insights.returns.threeYearReturn), tone: summaryTone(insights.returns.threeYearReturn) },
                  ]}
                />
              </MetricSection>

              <MetricSection title="Volume Metrics">
                <MetricGrid
                  items={[
                    { label: "Today's Volume", value: formatVolume(insights.volumeMetrics.todaysVolume) },
                    { label: "Average 30-Day Volume", value: formatVolume(insights.volumeMetrics.average30DayVolume) },
                    { label: "Relative Volume", value: formatMaybeRatio(insights.volumeMetrics.relativeVolume) },
                  ]}
                />
              </MetricSection>

              <MetricSection title="Volatility">
                <MetricGrid
                  items={[
                    { label: "30-Day Volatility", value: formatMaybePercent(insights.volatilityMetrics.volatility30Day) },
                    { label: "90-Day Volatility", value: formatMaybePercent(insights.volatilityMetrics.volatility90Day) },
                    { label: "1-Year Volatility", value: formatMaybePercent(insights.volatilityMetrics.volatility1Year) },
                  ]}
                />
              </MetricSection>

              <section className="insights-range-row" aria-label="Chart range selector">
                {(["1M", "3M", "6M", "1Y", "3Y"] as RangeKey[]).map((range) => (
                  <button key={range} type="button" className={`range-pill ${selectedRange === range ? "active" : ""}`} onClick={() => setSelectedRange(range)}>
                    {range}
                  </button>
                ))}
              </section>

              <ChartCard title="Price History" subtitle="Line chart for the selected range">
                <MultiLineChart data={rangeHistory} lines={[{ key: "close", label: "Price", color: "#7c3aed" }]} valueFormatter={(value) => `$${formatMoney(value)}`} />
              </ChartCard>

              <ChartCard title="Candlestick Chart" subtitle="Open, high, low, and close by day">
                <CandlestickChart data={rangeHistory} />
              </ChartCard>

              <ChartCard title="Volume Chart" subtitle="Daily traded volume for the selected range">
                <VolumeChart data={rangeHistory} />
              </ChartCard>

              <ChartCard title="Moving Averages" subtitle="Price with 20, 50, and 200 day SMAs">
                <MultiLineChart
                  data={rangeHistory}
                  lines={[
                    { key: "close", label: "Price", color: "#7c3aed" },
                    { key: "sma20", label: "20 SMA", color: "#2563eb" },
                    { key: "sma50", label: "50 SMA", color: "#f97316" },
                    { key: "sma200", label: "200 SMA", color: "#16a34a" },
                  ]}
                  valueFormatter={(value) => `$${formatMoney(value)}`}
                />
              </ChartCard>

              <ChartCard title="Rolling Volatility" subtitle="Risk trend across 30-day, 90-day, and 1-year windows">
                <MultiLineChart
                  data={rangeHistory}
                  lines={[
                    { key: "volatility30Day", label: "30D Volatility", color: "#dc2626" },
                    { key: "volatility90Day", label: "90D Volatility", color: "#0f766e" },
                    { key: "volatility1Year", label: "1Y Volatility", color: "#7c2d12" },
                  ]}
                  valueFormatter={(value) => `${formatMoney(value)}%`}
                />
              </ChartCard>

              <div className="insights-two-col-grid">
                <MetricSection title="Performance Distribution">
                  <MetricGrid
                    items={[
                      { label: "Positive Days", value: insights.performanceDistribution.positiveDays.toString() },
                      { label: "Negative Days", value: insights.performanceDistribution.negativeDays.toString() },
                      { label: "Flat Days", value: insights.performanceDistribution.flatDays.toString() },
                    ]}
                  />
                </MetricSection>

                <MetricSection title="Volume Distribution">
                  <MetricGrid
                    items={[
                      { label: "Min Volume", value: formatVolume(insights.volumeDistribution.minVolume) },
                      { label: "Avg Volume", value: formatVolume(insights.volumeDistribution.averageVolume) },
                      { label: "Max Volume", value: formatVolume(insights.volumeDistribution.maxVolume) },
                    ]}
                  />
                </MetricSection>
              </div>

              <MetricSection title="Monthly Returns Heatmap">
                <MonthlyReturnsHeatmap cells={insights.monthlyReturnsHeatmap} />
              </MetricSection>

              <MetricSection title="Return Histogram">
                <ReturnHistogram history={insights.history} />
              </MetricSection>

              <div className="insights-two-col-grid">
                <MetricSection title="Drawdown Analysis">
                  <MetricGrid
                    items={[
                      { label: "Maximum Drawdown", value: formatMaybePercent(insights.drawdownAnalysis.maxDrawdown), tone: "negative" },
                      { label: "Peak Date", value: formatDateLabel(insights.drawdownAnalysis.peakDate) },
                      { label: "Trough Date", value: formatDateLabel(insights.drawdownAnalysis.troughDate) },
                    ]}
                  />
                </MetricSection>

                <MetricSection title="Best and Worst Days">
                  <MetricGrid
                    items={[
                      { label: "Best Daily Gain", value: formatMaybePercent(insights.bestWorstDays.bestDailyGain), tone: "positive" },
                      { label: "Best Gain Date", value: formatDateLabel(insights.bestWorstDays.bestDailyGainDate) },
                      { label: "Worst Daily Loss", value: formatMaybePercent(insights.bestWorstDays.worstDailyLoss), tone: "negative" },
                      { label: "Worst Loss Date", value: formatDateLabel(insights.bestWorstDays.worstDailyLossDate) },
                    ]}
                  />
                </MetricSection>
              </div>

              <MetricSection title="Advanced Metrics">
                <MetricGrid
                  items={[
                    { label: "Sharpe Ratio", value: formatMaybePlain(insights.riskMetrics.sharpeRatio), tone: summaryTone(insights.riskMetrics.sharpeRatio) },
                    { label: "Sortino Ratio", value: formatMaybePlain(insights.riskMetrics.sortinoRatio), tone: summaryTone(insights.riskMetrics.sortinoRatio) },
                    { label: "Beta (vs S&P 500)", value: formatMaybePlain(insights.riskMetrics.betaVsSp500) },
                    { label: "SMA 20", value: formatMaybeMoney(insights.trendMetrics.sma20) },
                    { label: "SMA 50", value: formatMaybeMoney(insights.trendMetrics.sma50) },
                    { label: "SMA 200", value: formatMaybeMoney(insights.trendMetrics.sma200) },
                    { label: "Trend State", value: marketStateLabel },
                    { label: "RSI (14)", value: formatMaybePlain(insights.momentumMetrics.rsi14) },
                    { label: "MACD", value: formatMaybePlain(insights.momentumMetrics.macd), tone: summaryTone(insights.momentumMetrics.macd) },
                    { label: "MACD Signal", value: formatMaybePlain(insights.momentumMetrics.macdSignal), tone: summaryTone(insights.momentumMetrics.macdSignal) },
                    { label: "Momentum (30 Day)", value: formatMaybePercent(insights.momentumMetrics.momentum30Day), tone: summaryTone(insights.momentumMetrics.momentum30Day) },
                  ]}
                />
              </MetricSection>

              <section className="insights-history-table-card">
                <div className="insights-chart-head">
                  <div>
                    <h3>Recent Daily Data</h3>
                    <p>Latest OHLC, volume, SMA, and volatility observations in the selected range.</p>
                  </div>
                </div>
                <div className="insights-table-wrap">
                  <table className="insights-table">
                    <thead>
                      <tr>
                        <th>Date</th>
                        <th>Close</th>
                        <th>Volume</th>
                        <th>Daily Return</th>
                        <th>20 SMA</th>
                        <th>50 SMA</th>
                        <th>200 SMA</th>
                      </tr>
                    </thead>
                    <tbody>
                      {rangeHistory.slice(-20).reverse().map((point) => (
                        <tr key={`${point.tradingDate}-${point.close}`}>
                          <td>{formatDateShort(point.tradingDate)}</td>
                          <td>{formatMaybeMoney(point.close)}</td>
                          <td>{formatVolume(point.volume)}</td>
                          <td className={toneClass(point.dailyReturnPercent)}>{formatMaybePercent(point.dailyReturnPercent)}</td>
                          <td>{formatMaybeMoney(point.sma20)}</td>
                          <td>{formatMaybeMoney(point.sma50)}</td>
                          <td>{formatMaybeMoney(point.sma200)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>
            </>
          )}
        </div>
      </main>
    </>
  );
}



