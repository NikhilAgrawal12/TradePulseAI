import { useEffect, useMemo, useState, type ReactNode } from "react";
import { Link, useParams } from "react-router";
import { Header } from "../../components/Header.tsx";
import type { MonthlyReturnHeatmapCell, StockAnalytics, StockHistoryPoint, StockPrediction } from "../../types/stockAnalytics";
import type { Stock } from "../../types/stock";
import { fetchStockAnalytics, fetchStockPrediction } from "../../utils/stockAnalyticsApi";
import { formatMoney, formatPercent, formatSignedCurrency } from "../../utils/money";
import { useStreamedStocks } from "../../utils/useStreamedStocks";
import "./StockAnalyticsPage.css";

type ParsedNewsHeadline = {
  headline: string;
  url: string | null;
  publisher: string | null;
};

type RangeKey = "1M" | "3M" | "6M" | "1Y" | "3Y";

type LineDefinition = {
  key: keyof StockHistoryPoint;
  label: string;
  color: string;
};

type AxisTick = {
  index: number;
  label: string;
};

const RANGE_DAYS: Record<RangeKey, number> = {
  "1M": 31,
  "3M": 92,
  "6M": 183,
  "1Y": 366,
  "3Y": 1096,
};

const MONTH_LABELS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

function parseDisplayDate(value: string): Date {
  const dateOnlyMatch = value.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (dateOnlyMatch) {
    const year = Number(dateOnlyMatch[1]);
    const month = Number(dateOnlyMatch[2]);
    const day = Number(dateOnlyMatch[3]);
    return new Date(year, month - 1, day);
  }
  return new Date(value);
}

function formatDateLabel(value: string | null | undefined): string {
  if (!value) {
    return "--";
  }
  const parsed = parseDisplayDate(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" });
}

function formatDateShort(value: string | null | undefined): string {
  if (!value) {
    return "--";
  }
  const parsed = parseDisplayDate(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

function splitNewsHeadlines(news: string | null | undefined): ParsedNewsHeadline[] {
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

function formatCompactVolume(value: number): string {
  if (!Number.isFinite(value)) return "--";
  if (Math.abs(value) >= 1_000_000_000) return `${formatMoney(value / 1_000_000_000)}B`;
  if (Math.abs(value) >= 1_000_000) return `${formatMoney(value / 1_000_000)}M`;
  if (Math.abs(value) >= 1_000) return `${formatMoney(value / 1_000)}K`;
  return formatMoney(value);
}

function filterHistoryByRange(history: StockHistoryPoint[], range: RangeKey): StockHistoryPoint[] {
  if (history.length === 0) return [];
  const latest = history[history.length - 1]?.tradingDate;
  if (!latest) return history;
  const latestDate = parseDisplayDate(latest);
  if (Number.isNaN(latestDate.getTime())) return history;
  const cutoff = new Date(latestDate);
  cutoff.setDate(cutoff.getDate() - RANGE_DAYS[range]);
  return history.filter((point) => {
    if (!point.tradingDate) return false;
    const pointDate = parseDisplayDate(point.tradingDate);
    return !Number.isNaN(pointDate.getTime()) && pointDate >= cutoff;
  });
}

function buildXAxisTicks(data: StockHistoryPoint[], maxTicks = 6): AxisTick[] {
  if (data.length === 0) return [];
  if (data.length === 1) {
    const onlyDate = data[0]?.tradingDate;
    return onlyDate ? [{ index: 0, label: parseDisplayDate(onlyDate).toLocaleDateString(undefined, { month: "short", year: "2-digit" }) }] : [];
  }
  const requestedTicks = Math.max(2, Math.min(maxTicks, data.length));
  const step = (data.length - 1) / (requestedTicks - 1);
  const used = new Set<number>();
  const ticks: AxisTick[] = [];
  for (let i = 0; i < requestedTicks; i += 1) {
    const index = Math.round(i * step);
    if (used.has(index)) continue;
    used.add(index);
    const dateLabel = data[index]?.tradingDate;
    if (!dateLabel) continue;
    const parsed = parseDisplayDate(dateLabel);
    ticks.push({ index, label: Number.isNaN(parsed.getTime()) ? dateLabel : parsed.toLocaleDateString(undefined, { month: "short", year: "2-digit" }) });
  }
  const lastDateLabel = data[data.length - 1]?.tradingDate;
  if (!used.has(data.length - 1) && lastDateLabel) {
    const parsed = parseDisplayDate(lastDateLabel);
    ticks.push({ index: data.length - 1, label: Number.isNaN(parsed.getTime()) ? lastDateLabel : parsed.toLocaleDateString(undefined, { month: "short", year: "2-digit" }) });
  }
  return ticks.sort((a, b) => a.index - b.index);
}

function getSeriesBounds(data: StockHistoryPoint[], keys: Array<keyof StockHistoryPoint>): { min: number; max: number } {
  const values = data.flatMap((point) => keys.map((key) => point[key]).filter((v): v is number => typeof v === "number" && Number.isFinite(v)));
  if (values.length === 0) return { min: 0, max: 1 };
  const min = Math.min(...values);
  const max = Math.max(...values);
  if (min === max) return { min: min - 1, max: max + 1 };
  const padding = (max - min) * 0.08;
  return { min: min - padding, max: max + padding };
}

function buildLinePath(data: StockHistoryPoint[], key: keyof StockHistoryPoint, left: number, top: number, innerWidth: number, innerHeight: number, min: number, max: number): string {
  if (data.length === 0) return "";
  return data.map((point, index) => {
    const rawValue = point[key];
    if (typeof rawValue !== "number" || !Number.isFinite(rawValue)) return null;
    const x = left + (index / Math.max(data.length - 1, 1)) * innerWidth;
    const y = top + innerHeight - ((rawValue - min) / Math.max(max - min, 1e-9)) * innerHeight;
    return `${index === 0 ? "M" : "L"}${x},${y}`;
  }).filter((v): v is string => v !== null).join(" ");
}

function ChartCard({ title, children, subtitle, icon }: { title: string; subtitle?: string; children: ReactNode; icon?: string }) {
  return (
    <section className="analytics-chart-card">
      <div className="analytics-chart-head">
        <div>
          <div className="analytics-section-head">
            {icon && <span className="analytics-section-icon">{icon}</span>}
            <h3>{title}</h3>
          </div>
          {subtitle ? <p>{subtitle}</p> : null}
        </div>
      </div>
      {children}
    </section>
  );
}

type AccentColor = "green" | "blue" | "amber" | "teal" | "red" | "indigo" | "violet";

function MetricSection({ title, subtitle, children, icon, accent }: { title: string; subtitle?: string; children: ReactNode; icon?: string; accent?: AccentColor }) {
  return (
    <section className={`analytics-section-card${accent ? ` analytics-section-card--${accent}` : ""}`}>
      <div className="analytics-section-head">
        {icon && <span className="analytics-section-icon">{icon}</span>}
        <h3>{title}</h3>
      </div>
      {subtitle && <p className="analytics-section-subtitle">{subtitle}</p>}
      {children}
    </section>
  );
}

function MetricGrid({ items }: { items: Array<{ label: string; value: string; tone?: "positive" | "negative" }> }) {
   return (
     <div className="analytics-metric-grid">
       {items.map((item) => (
         <div key={item.label} className="analytics-metric-card">
           <span>{item.label}</span>
           <strong className={item.tone === "positive" ? "metric-positive" : item.tone === "negative" ? "metric-negative" : undefined}>{item.value}</strong>
         </div>
       ))}
     </div>
   );
}

const DISTRIBUTION_COLORS = {
  positive: "#16a34a",
  negative: "#dc2626",
  flat: "#94a3b8",
};

function DistributionPieChart({ positiveDays, negativeDays, flatDays }: { positiveDays: number; negativeDays: number; flatDays: number }) {
  const total = (positiveDays ?? 0) + (negativeDays ?? 0) + (flatDays ?? 0);
  if (total === 0) return null;

  const size = 200;
  const cx = size / 2;
  const cy = size / 2;
  const outerR = 80;
  const innerR = 44; // donut hole

  const segments = [
    { value: positiveDays ?? 0, color: DISTRIBUTION_COLORS.positive, label: "Positive" },
    { value: negativeDays ?? 0, color: DISTRIBUTION_COLORS.negative, label: "Negative" },
    { value: flatDays ?? 0, color: DISTRIBUTION_COLORS.flat, label: "Flat" },
  ].filter((s) => s.value > 0);

  function polarToXY(angleDeg: number, radius: number) {
    const rad = ((angleDeg - 90) * Math.PI) / 180;
    return { x: cx + radius * Math.cos(rad), y: cy + radius * Math.sin(rad) };
  }

  function buildDonutSlice(startAngle: number, endAngle: number) {
    // Clamp to avoid floating-point full-circle issues
    const sweep = Math.min(endAngle - startAngle, 359.9999);
    const end = startAngle + sweep;
    const large = sweep > 180 ? 1 : 0;
    const s1 = polarToXY(startAngle, outerR);
    const e1 = polarToXY(end, outerR);
    const s2 = polarToXY(end, innerR);
    const e2 = polarToXY(startAngle, innerR);
    return [
      `M ${s1.x} ${s1.y}`,
      `A ${outerR} ${outerR} 0 ${large} 1 ${e1.x} ${e1.y}`,
      `L ${s2.x} ${s2.y}`,
      `A ${innerR} ${innerR} 0 ${large} 0 ${e2.x} ${e2.y}`,
      "Z",
    ].join(" ");
  }

  let current = 0;
  const slices = segments.map((s) => {
    const startAngle = current;
    const sweep = (s.value / total) * 360;
    current += sweep;
    return { ...s, startAngle, endAngle: current };
  });

  const pct = (v: number) => ((v / total) * 100).toFixed(1);

  return (
    <div className="distribution-pie-wrapper">
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="distribution-pie-svg">
        {slices.map((s) => (
          <path
            key={s.label}
            d={buildDonutSlice(s.startAngle, s.endAngle)}
            fill={s.color}
            stroke="#fff"
            strokeWidth="2.5"
          />
        ))}
        <text x={cx} y={cy - 6} textAnchor="middle" fontSize="13" fontWeight="700" fill="#2e1065">{total}</text>
        <text x={cx} y={cy + 11} textAnchor="middle" fontSize="10" fill="#7c6a9e">trading days</text>
      </svg>
      <div className="distribution-pie-legend">
        {slices.map((s) => (
          <div key={s.label} className="distribution-pie-legend-item">
            <span className="distribution-pie-dot" style={{ background: s.color }} />
            <span className="distribution-pie-legend-label">{s.label}</span>
            <span className="distribution-pie-legend-pct">{pct(s.value)}%</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function MlProbabilityDonut({ buyProbability, sellProbability }: { buyProbability: number; sellProbability: number }) {
  const buy = Math.max(0, Math.min(1, buyProbability));
  const sell = Math.max(0, Math.min(1, sellProbability));
  const buyPct = Math.round(buy * 1000) / 10;
  const sellPct = Math.round(sell * 1000) / 10;

  return (
    <div className="ml-probability-wrap">
      <div
        className="ml-probability-donut"
        style={{ background: `conic-gradient(#16a34a 0 ${buyPct}%, #dc2626 ${buyPct}% 100%)` }}
      >
        <div className="ml-probability-inner">
          <strong>{buyPct}%</strong>
          <span>BUY</span>
        </div>
      </div>
      <div className="ml-probability-legend">
        <div><i className="ml-dot buy" /> BUY {buyPct}%</div>
        <div><i className="ml-dot sell" /> SELL {sellPct}%</div>
      </div>
    </div>
  );
}

function MlSignalBreakdown({ prediction }: { prediction: StockPrediction }) {
  const buyPct = Math.max(0, Math.min(100, prediction.probabilityBuy * 100));
  const sellPct = Math.max(0, Math.min(100, prediction.probabilitySell * 100));

  return (
    <div className="ml-signal-breakdown">
      <div className="ml-signal-headline">
        <span>Signal split</span>
        <strong>{prediction.action === "BUY" ? "BUY bias" : prediction.action === "SELL" ? "SELL bias" : "Balanced setup"}</strong>
      </div>
      <div className="ml-signal-row">
        <div className="ml-signal-row-head">
          <span>BUY probability</span>
          <strong>{formatPercent(buyPct, false)}%</strong>
        </div>
        <div className="ml-signal-track" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(buyPct)}>
          <div className="ml-signal-fill buy" style={{ width: `${buyPct}%` }} />
        </div>
      </div>
      <div className="ml-signal-row">
        <div className="ml-signal-row-head">
          <span>SELL probability</span>
          <strong>{formatPercent(sellPct, false)}%</strong>
        </div>
        <div className="ml-signal-track" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(sellPct)}>
          <div className="ml-signal-fill sell" style={{ width: `${sellPct}%` }} />
        </div>
      </div>
      <p className="ml-signal-note">Latest BUY and SELL probabilities for the next trading window.</p>
    </div>
  );
}

function convictionTone(label: StockPrediction["convictionLabel"]): string {
  if (label === "HIGH") return "High conviction";
  if (label === "MEDIUM") return "Medium conviction";
  if (label === "LOW") return "Low conviction";
  return "Neutral setup";
}


function MultiLineChart({ data, lines, valueFormatter }: { data: StockHistoryPoint[]; lines: LineDefinition[]; valueFormatter?: (value: number) => string }) {
   const width = 900;
   const height = 360;
   const left = 70;
   const right = 14;
   const top = 12;
   const bottom = 60;
   const innerWidth = width - left - right;
   const innerHeight = height - top - bottom;
   const bounds = useMemo(() => getSeriesBounds(data, lines.map((line) => line.key)), [data, lines]);
   const xTicks = useMemo(() => buildXAxisTicks(data, 6), [data]);
   const yTicks = useMemo(() => {
     return Array.from({ length: 5 }, (_, index) => {
       const ratio = index / 4;
       return bounds.max - (bounds.max - bounds.min) * ratio;
     });
   }, [bounds]);

   return (
     <div className="analytics-chart-shell">
       <svg viewBox={`0 0 ${width} ${height}`} className="analytics-svg-chart" role="img" aria-label="Stock line chart">
         {yTicks.map((tick, index) => {
           const y = top + (index / 4) * innerHeight;
           return (
             <g key={tick}>
               <line x1={left} y1={y} x2={width - right} y2={y} className="chart-grid-line" />
               <text x={left - 10} y={y - 4} className="chart-axis-label" textAnchor="end">
                 {valueFormatter ? valueFormatter(tick) : formatMoney(tick)}
               </text>
             </g>
           );
         })}
         <line x1={left} y1={top + innerHeight} x2={width - right} y2={top + innerHeight} className="chart-axis-line" />
         {xTicks.map((tick) => {
           const x = left + (tick.index / Math.max(data.length - 1, 1)) * innerWidth;
           return (
             <g key={`${tick.index}-${tick.label}`}>
               <line x1={x} y1={top + innerHeight} x2={x} y2={top + innerHeight + 5} className="chart-axis-line" />
               <text x={x} y={top + innerHeight + 22} className="chart-axis-label chart-axis-label-x" textAnchor="middle">
                 {tick.label}
               </text>
             </g>
           );
         })}
         {lines.map((line) => (
           <path
             key={line.label}
             d={buildLinePath(data, line.key, left, top, innerWidth, innerHeight, bounds.min, bounds.max)}
             fill="none"
             stroke={line.color}
             strokeWidth="3"
             strokeLinejoin="round"
             strokeLinecap="round"
           />
         ))}
        </svg>
        <div className="analytics-chart-legend">
          {lines.map((line) => (
            <span key={line.label}><i style={{ backgroundColor: line.color }} />{line.label}</span>
          ))}
        </div>
      </div>
    );
  }

  function CandlestickChart({ data }: { data: StockHistoryPoint[] }) {
   const width = 900;
   const height = 360;
   const left = 70;
   const right = 14;
   const top = 12;
   const bottom = 60;
   const bounds = useMemo(() => getSeriesBounds(data, ["low", "high"]), [data]);
   const xTicks = useMemo(() => buildXAxisTicks(data, 6), [data]);
   const yTicks = useMemo(() => {
     return Array.from({ length: 5 }, (_, index) => {
       const ratio = index / 4;
       return bounds.max - (bounds.max - bounds.min) * ratio;
     });
   }, [bounds]);
   const innerWidth = width - left - right;
   const innerHeight = height - top - bottom;

   return (
     <div className="analytics-chart-shell">
       <svg viewBox={`0 0 ${width} ${height}`} className="analytics-svg-chart" role="img" aria-label="Stock candlestick chart">
         {yTicks.map((tick, index) => {
           const y = top + (index / 4) * innerHeight;
           return (
             <g key={tick}>
               <line x1={left} y1={y} x2={width - right} y2={y} className="chart-grid-line" />
               <text x={left - 10} y={y - 4} className="chart-axis-label" textAnchor="end">
                 {formatMoney(tick)}
               </text>
             </g>
           );
         })}
        {data.map((point, index) => {
          if ([point.open, point.high, point.low, point.close].some((value) => typeof value !== "number")) {
            return null;
          }
          const x = left + (index / Math.max(data.length - 1, 1)) * innerWidth;
          const candleWidth = Math.max(innerWidth / Math.max(data.length * 1.8, 24), 2);
          const highY = top + innerHeight - (((point.high as number) - bounds.min) / (bounds.max - bounds.min)) * innerHeight;
          const lowY = top + innerHeight - (((point.low as number) - bounds.min) / (bounds.max - bounds.min)) * innerHeight;
          const openY = top + innerHeight - (((point.open as number) - bounds.min) / (bounds.max - bounds.min)) * innerHeight;
          const closeY = top + innerHeight - (((point.close as number) - bounds.min) / (bounds.max - bounds.min)) * innerHeight;
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
         <line x1={left} y1={top + innerHeight} x2={width - right} y2={top + innerHeight} className="chart-axis-line" />
         {xTicks.map((tick) => {
           const x = left + (tick.index / Math.max(data.length - 1, 1)) * innerWidth;
           return (
             <g key={`${tick.index}-${tick.label}`}>
               <line x1={x} y1={top + innerHeight} x2={x} y2={top + innerHeight + 5} className="chart-axis-line" />
               <text x={x} y={top + innerHeight + 22} className="chart-axis-label chart-axis-label-x" textAnchor="middle">
                 {tick.label}
               </text>
             </g>
           );
         })}
       </svg>
     </div>
   );
 }

  function VolumeChart({ data }: { data: StockHistoryPoint[] }) {
   const width = 900;
   const height = 300;
   const left = 70;
   const right = 14;
   const top = 12;
   const bottom = 60;
   const maxVolume = Math.max(...data.map((point) => point.volume ?? 0), 1);
   const xTicks = useMemo(() => buildXAxisTicks(data, 6), [data]);
   const yTicks = useMemo(() => {
     return Array.from({ length: 5 }, (_, index) => {
       const ratio = index / 4;
       return maxVolume * (1 - ratio);
     });
   }, [maxVolume]);
   const innerWidth = width - left - right;
   const innerHeight = height - top - bottom;

   return (
     <div className="analytics-chart-shell">
       <svg viewBox={`0 0 ${width} ${height}`} className="analytics-svg-chart" role="img" aria-label="Stock volume chart">
         {yTicks.map((tick, index) => {
           const y = top + (index / 4) * innerHeight;
           return (
             <g key={tick}>
               <line x1={left} y1={y} x2={width - right} y2={y} className="chart-grid-line" />
               <text x={left - 10} y={y - 4} className="chart-axis-label" textAnchor="end">
                 {formatCompactVolume(tick)}
               </text>
             </g>
           );
         })}
        {data.map((point, index) => {
          const volume = point.volume ?? 0;
          const x = left + (index / Math.max(data.length - 1, 1)) * innerWidth;
          const barWidth = Math.max(innerWidth / Math.max(data.length * 1.6, 20), 2);
          const barHeight = (volume / maxVolume) * innerHeight;
          const y = top + innerHeight - barHeight;
           const isPositive = (point.return1d ?? 0) >= 0;
           return <rect key={`${point.tradingDate}-${index}`} x={x - barWidth / 2} y={y} width={barWidth} height={barHeight} fill={isPositive ? "#16a34a" : "#dc2626"} rx="1" />;
        })}
         <line x1={left} y1={top + innerHeight} x2={width - right} y2={top + innerHeight} className="chart-axis-line" />
         {xTicks.map((tick) => {
           const x = left + (tick.index / Math.max(data.length - 1, 1)) * innerWidth;
           return (
             <g key={`${tick.index}-${tick.label}`}>
               <line x1={x} y1={top + innerHeight} x2={x} y2={top + innerHeight + 5} className="chart-axis-line" />
               <text x={x} y={top + innerHeight + 22} className="chart-axis-label chart-axis-label-x" textAnchor="middle">
                 {tick.label}
               </text>
             </g>
           );
         })}
       </svg>
     </div>
    );
  }

function CandlestickLegend() {
  return (
    <div className="chart-legend-grid">
      <div className="legend-item">
        <svg viewBox="0 0 60 90" className="legend-candle">
          <line x1="30" y1="10" x2="30" y2="45" stroke="#16a34a" strokeWidth="1.5" />
          <rect x="24" y="25" width="12" height="20" fill="#16a34a" rx="1" />
          <text x="30" y="70" textAnchor="middle" className="legend-label">Close &gt; Open</text>
        </svg>
      </div>
      <div className="legend-item">
        <svg viewBox="0 0 60 90" className="legend-candle">
          <line x1="30" y1="10" x2="30" y2="45" stroke="#dc2626" strokeWidth="1.5" />
          <rect x="24" y="25" width="12" height="20" fill="#dc2626" rx="1" />
          <text x="30" y="70" textAnchor="middle" className="legend-label">Close &lt; Open</text>
        </svg>
      </div>
      <div className="legend-note">
        <p><strong>Wick:</strong> High to low price range</p>
        <p><strong>Body:</strong> Open to close price range</p>
      </div>
    </div>
  );
}

function VolumeLegend() {
  return (
    <div className="chart-legend-grid">
      <div className="legend-item">
        <svg viewBox="0 0 60 90" className="legend-bars">
          <rect x="20" y="15" width="20" height="35" fill="#16a34a" rx="2" />
          <text x="30" y="70" textAnchor="middle" className="legend-label">Positive Day</text>
        </svg>
      </div>
      <div className="legend-item">
        <svg viewBox="0 0 60 90" className="legend-bars">
          <rect x="20" y="15" width="20" height="35" fill="#dc2626" rx="2" />
          <text x="30" y="70" textAnchor="middle" className="legend-label">Negative Day</text>
        </svg>
      </div>
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

export function StockAnalyticsPage() {
   const { stockId } = useParams();
   const [analytics, setAnalytics] = useState<StockAnalytics | null>(null);
   const [prediction, setPrediction] = useState<StockPrediction | null>(null);
   const [loading, setLoading] = useState(true);
   const [error, setError] = useState<string | null>(null);
   const [selectedRange, setSelectedRange] = useState<RangeKey>("1M");
   const [candlestickRange, setCandlestickRange] = useState<RangeKey>("1M");
   const [volumeRange, setVolumeRange] = useState<RangeKey>("1M");
   const [movingAverageRange, setMovingAverageRange] = useState<RangeKey>("1M");
   const [rollingVolatilityRange, setRollingVolatilityRange] = useState<RangeKey>("1M");

   const { stocks: streamedStocks, setSearchTerm } = useStreamedStocks();

   useEffect(() => {
     document.title = "Stock Analytics | TradePulse";
   }, []);

   useEffect(() => {
     window.scrollTo({ top: 0, left: 0, behavior: "auto" });
   }, [stockId]);

   useEffect(() => {
     let mounted = true;

     const loadAnalytics = async () => {
       if (!stockId) {
         setError("Stock id is missing.");
         setLoading(false);
         return;
       }

       try {
         setLoading(true);
          const [nextAnalytics, nextPrediction] = await Promise.all([
            fetchStockAnalytics(stockId),
            fetchStockPrediction(stockId).catch(() => null),
          ]);
         if (!mounted) {
           return;
         }
         setAnalytics(nextAnalytics);
          setPrediction(nextPrediction);
         setError(null);
       } catch {
         if (!mounted) {
           return;
         }
         setError("Unable to load stock analytics right now. Please try again shortly.");
       } finally {
         if (mounted) {
           setLoading(false);
         }
       }
     };

     void loadAnalytics();

     return () => {
       mounted = false;
     };
   }, [stockId]);

   // Set search term to filter streamed stocks by the current symbol
   useEffect(() => {
     if (!analytics?.symbol) {
       setSearchTerm("");
       return;
     }
     setSearchTerm(analytics.symbol);
   }, [analytics?.symbol, setSearchTerm]);

   // Apply real-time updates from streamed stocks to analytics
   useEffect(() => {
     if (!analytics?.symbol || streamedStocks.length === 0) {
       return;
     }

     const matchedStock = streamedStocks.find((stock) =>
       stock.symbol.toUpperCase() === analytics.symbol.toUpperCase()
     ) as Stock | undefined;

     if (!matchedStock) {
       return;
     }

      const livePrice = typeof matchedStock.price === "number" ? matchedStock.price : null;

     setAnalytics((prev) => {
       if (!prev) {
         return prev;
       }

       const nextCurrentPrice = livePrice == null ? prev.currentPerformance.currentPrice : livePrice;
       // Keep previous close fixed to backend-provided prior-day baseline.
       const nextPreviousClose = prev.currentPerformance.previousClose;
       const nextDailyChange =
         nextCurrentPrice != null && nextPreviousClose != null
           ? nextCurrentPrice - nextPreviousClose
           : prev.currentPerformance.dailyChange;
        const nextDailyChangePercent =
          nextCurrentPrice != null && nextPreviousClose != null && nextPreviousClose !== 0
            ? ((nextCurrentPrice - nextPreviousClose) / nextPreviousClose) * 100
            : prev.currentPerformance.dailyChangePercent;

       return {
         ...prev,
         lastUpdated: matchedStock.lastUpdated ?? prev.lastUpdated,
         currentPerformance: {
           ...prev.currentPerformance,
           currentPrice: nextCurrentPrice,
           previousClose: nextPreviousClose,
           dailyChange: nextDailyChange,
            dailyChangePercent: nextDailyChangePercent,
         },
       };
     });
   }, [analytics?.symbol, streamedStocks]);

   const rangeHistory = useMemo(() => filterHistoryByRange(analytics?.history ?? [], selectedRange), [analytics?.history, selectedRange]);
   const candlestickHistory = useMemo(() => filterHistoryByRange(analytics?.history ?? [], candlestickRange), [analytics?.history, candlestickRange]);
   const volumeHistory = useMemo(() => filterHistoryByRange(analytics?.history ?? [], volumeRange), [analytics?.history, volumeRange]);
   const movingAverageHistory = useMemo(() => filterHistoryByRange(analytics?.history ?? [], movingAverageRange), [analytics?.history, movingAverageRange]);
   const rollingVolatilityHistory = useMemo(() => filterHistoryByRange(analytics?.history ?? [], rollingVolatilityRange), [analytics?.history, rollingVolatilityRange]);
   const latestNewsHeadlines = useMemo(
     () =>
       (analytics?.latestNews ?? [])
         .flatMap((item) =>
           splitNewsHeadlines(item.news).map((headline) => ({
             tradingDate: item.tradingDate,
             ...headline,
           })),
         )
         .slice(0, 3),
     [analytics?.latestNews],
   );

   const displayDailyChangePercent = useMemo(() => {
     if (!analytics) {
       return null;
     }
     const current = analytics.currentPerformance.currentPrice;
     const previous = analytics.currentPerformance.previousClose;
     if (current == null || previous == null || previous === 0) {
       return analytics.currentPerformance.dailyChangePercent;
     }
     return ((current - previous) / previous) * 100;
   }, [analytics]);

  const summaryTone = (value: number | null | undefined): "positive" | "negative" | undefined => {
    if (value == null) return undefined;
    if (value > 0) return "positive";
    if (value < 0) return "negative";
    return undefined;
  };

  const formatFlag = (value: boolean | null | undefined): string => {
    if (value == null) return "--";
    return value ? "Yes" : "No";
  };

  return (
    <>
      <Header />
      <main className="stock-analytics-page">
        <div className="stock-analytics-shell">
          <div className="stock-analytics-topbar">
            <Link to="/" className="analytics-back-link">← Back to stocks</Link>
          </div>

          {loading ? (
            <div className="analytics-state-card"><p>Loading stock analytics...</p></div>
          ) : error || !analytics ? (
            <div className="analytics-state-card error"><p>{error ?? "Unable to load stock analytics."}</p></div>
          ) : (
            <>
              <section className="analytics-hero-card">
                {(() => {
                  const exchangeLabel = analytics.exchange?.trim() ?? "";
                  const marketLabel = analytics.market?.trim() ?? "";
                  const subtitleParts: string[] = [];

                  if (exchangeLabel) {
                    subtitleParts.push(exchangeLabel);
                  }
                  if (marketLabel && marketLabel.toLowerCase() !== exchangeLabel.toLowerCase()) {
                    subtitleParts.push(marketLabel);
                  }
                  if (subtitleParts.length === 0) {
                    subtitleParts.push("Exchange unavailable");
                  }
                  if (analytics.lastUpdated) {
                    subtitleParts.push(`Updated ${formatDateLabel(analytics.lastUpdated)}`);
                  }

                  const dailyPct = displayDailyChangePercent ?? 0;
                  const changeClass = dailyPct > 0 ? "hero-change hero-change--positive" : dailyPct < 0 ? "hero-change hero-change--negative" : "hero-change";

                  return (
                <>
                  <div className="analytics-hero-left">
                    <p className="analytics-eyebrow">Stock Analytics</p>
                    <h1>{analytics.symbol} <span className="hero-name">· {analytics.name}</span></h1>
                    <p className="analytics-subtitle">{subtitleParts.join(" • ")}</p>
                  </div>
                  <div className="analytics-hero-price">
                    <strong>{formatMaybeMoney(analytics.currentPerformance.currentPrice)}</strong>
                    <span className={changeClass}>
                      {formatMaybeSignedMoney(analytics.currentPerformance.dailyChange)}
                      <em>{formatMaybePercent(displayDailyChangePercent)}</em>
                    </span>
                    <small className="hero-prev-close">Prev close {formatMaybeMoney(analytics.currentPerformance.previousClose)}</small>
                  </div>
                </>
                  );
                })()}
              </section>

              <MetricSection title="Current Performance" icon="📊">
                <MetricGrid
                  items={[
                    { label: "Current Price", value: formatMaybeMoney(analytics.currentPerformance.currentPrice) },
                    { label: "Previous Day Close", value: formatMaybeMoney(analytics.currentPerformance.previousClose) },
                    { label: "Daily Change ($)", value: formatMaybeSignedMoney(analytics.currentPerformance.dailyChange), tone: summaryTone(analytics.currentPerformance.dailyChange) },
                    { label: "Daily Change (%)", value: formatMaybePercent(displayDailyChangePercent), tone: summaryTone(displayDailyChangePercent) },
                  ]}
                />
              </MetricSection>

              <section className="analytics-ml-section-card">
                <div className="analytics-section-head analytics-ml-title-row">
                  <span className="analytics-section-icon">🤖</span>
                  <h3>Machine Learning Signal</h3>
                </div>
                {prediction ? (
                  <div className="analytics-ml-grid">
                    <div className="analytics-ml-primary">
                      <div className="analytics-ml-action-row">
                        <span>Action</span>
                        <strong className={`analytics-ml-action-chip ${prediction.action === "BUY" ? "buy" : prediction.action === "SELL" ? "sell" : "hold"}`}>
                          {prediction.action}
                        </strong>
                      </div>
                      <MlSignalBreakdown prediction={prediction} />
                      <div className="analytics-ml-meta-row">
                        <span className="analytics-ml-meta-pill">
                          <small>Confidence</small>
                          <strong>{formatMaybePercent(prediction.confidence * 100)}</strong>
                        </span>
                        <span className="analytics-ml-meta-pill">
                          <small>Model</small>
                          <strong>{prediction.modelName}</strong>
                        </span>
                        <span className="analytics-ml-meta-pill subtle">
                          <small>Updated</small>
                          <strong>{formatDateLabel(prediction.generatedAt)}</strong>
                        </span>
                      </div>
                    </div>
                    <div className="analytics-ml-visual">
                      <MlProbabilityDonut buyProbability={prediction.probabilityBuy} sellProbability={prediction.probabilitySell} />
                      <div className="analytics-ml-side-grid">
                        <div className="analytics-ml-side-card">
                          <span>Conviction</span>
                          <strong>{convictionTone(prediction.convictionLabel)}</strong>
                        </div>
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="analytics-state-card error"><p>ML prediction is currently unavailable.</p></div>
                )}
              </section>

              <MetricSection title="Latest News" icon="📰">
                {latestNewsHeadlines.length > 0 ? (
                  <article className="analytics-news-featured-card">
                    <ol className="analytics-news-headline-list">
                      {latestNewsHeadlines.map((item, index) => (
                        <li key={`${item.tradingDate ?? "na"}-${index}-${item.headline}`}>
                          <span className="analytics-news-rank">{index + 1}</span>
                          <div>
                            {item.url ? (
                              <a href={item.url} target="_blank" rel="noopener noreferrer" className="analytics-news-link" title={item.headline}>
                                {item.headline}
                              </a>
                            ) : (
                              <p className="analytics-news-featured-text" title={item.headline}>{item.headline}</p>
                            )}
                            <div className="analytics-news-meta">
                              <span className="analytics-news-date">{formatDateLabel(item.tradingDate)}</span>
                              {item.publisher ? <span className="analytics-news-publisher">{item.publisher}</span> : null}
                            </div>
                          </div>
                        </li>
                      ))}
                    </ol>
                  </article>
                ) : (
                  <p className="analytics-empty-state">No news available yet.</p>
                )}
              </MetricSection>

              <MetricSection title="52-Week Range" icon="📉" accent="indigo">
                <MetricGrid
                  items={[
                    { label: "52-Week High", value: formatMaybeMoney(analytics.metrics52Week.high52Week) },
                    { label: "52-Week Low", value: formatMaybeMoney(analytics.metrics52Week.low52Week) },
                    { label: "Distance from High", value: formatMaybePercent(analytics.metrics52Week.distanceFromHighPercent), tone: summaryTone(analytics.metrics52Week.distanceFromHighPercent) },
                    { label: "Distance from Low", value: formatMaybePercent(analytics.metrics52Week.distanceFromLowPercent), tone: summaryTone(analytics.metrics52Week.distanceFromLowPercent) },
                  ]}
                />
              </MetricSection>

              <MetricSection title="Returns" icon="📈" accent="green">
                <MetricGrid
                  items={[
                    { label: "1 Week", value: formatMaybePercent(analytics.returns.oneWeekReturn), tone: summaryTone(analytics.returns.oneWeekReturn) },
                    { label: "1 Month", value: formatMaybePercent(analytics.returns.oneMonthReturn), tone: summaryTone(analytics.returns.oneMonthReturn) },
                    { label: "3 Months", value: formatMaybePercent(analytics.returns.threeMonthReturn), tone: summaryTone(analytics.returns.threeMonthReturn) },
                    { label: "6 Months", value: formatMaybePercent(analytics.returns.sixMonthReturn), tone: summaryTone(analytics.returns.sixMonthReturn) },
                    { label: "1 Year", value: formatMaybePercent(analytics.returns.oneYearReturn), tone: summaryTone(analytics.returns.oneYearReturn) },
                    { label: "3 Years", value: formatMaybePercent(analytics.returns.threeYearReturn), tone: summaryTone(analytics.returns.threeYearReturn) },
                  ]}
                />
              </MetricSection>

              <MetricSection title="Volume" icon="📦" accent="blue">
                <MetricGrid
                  items={[
                    { label: "As Of Date", value: formatDateLabel(analytics.volumeMetrics.latestTradingDate) },
                    { label: "Latest Day Volume", value: formatVolume(analytics.volumeMetrics.latestTradingDayVolume) },
                    { label: "30-Day Avg Volume", value: formatVolume(analytics.volumeMetrics.average30DayVolume) },
                    { label: "Relative Volume", value: formatMaybeRatio(analytics.volumeMetrics.relativeVolume) },
                  ]}
                />
              </MetricSection>

              <ChartCard title="Price History" subtitle="Closing price movement over selected period" icon="📈">
                <section className="analytics-range-row" aria-label="Price history range selector">
                  {(["1M", "3M", "6M", "1Y", "3Y"] as RangeKey[]).map((range) => (
                    <button key={range} type="button" className={`range-pill ${selectedRange === range ? "active" : ""}`} onClick={() => setSelectedRange(range)}>
                      {range}
                    </button>
                  ))}
                </section>
                <MultiLineChart data={rangeHistory} lines={[{ key: "close", label: "Price", color: "#7c3aed" }]} valueFormatter={(value) => formatMoney(value)} />
              </ChartCard>

              <ChartCard title="Candlestick Chart" subtitle="Open / High / Low / Close per trading day" icon="🕯️">
                <section className="analytics-range-row" aria-label="Candlestick range selector">
                  {(["1M", "3M", "6M", "1Y"] as const).map((range) => (
                    <button key={range} type="button" className={`range-pill ${candlestickRange === range ? "active" : ""}`} onClick={() => setCandlestickRange(range as RangeKey)}>
                      {range}
                    </button>
                  ))}
                </section>
                <CandlestickChart data={candlestickHistory} />
                <CandlestickLegend />
              </ChartCard>

              <ChartCard title="Volume Chart" subtitle="Daily trading volume coloured by price direction" icon="📊">
                <section className="analytics-range-row" aria-label="Volume range selector">
                  {(["1M", "3M", "6M", "1Y"] as const).map((range) => (
                    <button key={range} type="button" className={`range-pill ${volumeRange === range ? "active" : ""}`} onClick={() => setVolumeRange(range as RangeKey)}>
                      {range}
                    </button>
                  ))}
                </section>
                <VolumeChart data={volumeHistory} />
                <VolumeLegend />
              </ChartCard>

              <MetricSection title="Volatility" icon="⚡" accent="amber">
                <MetricGrid
                  items={[
                    { label: "5-Day Volatility", value: formatMaybePercent(analytics.volatilityMetrics.volatility5Day) },
                    { label: "20-Day Volatility", value: formatMaybePercent(analytics.volatilityMetrics.volatility20Day) },
                    { label: "60-Day Volatility", value: formatMaybePercent(analytics.volatilityMetrics.volatility60Day) },
                    { label: "90-Day Volatility", value: formatMaybePercent(analytics.volatilityMetrics.volatility90Day) },
                    { label: "120-Day Volatility", value: formatMaybePercent(analytics.volatilityMetrics.volatility120Day) },
                  ]}
                />
              </MetricSection>

              <ChartCard title="Moving Averages" subtitle="20, 50 and 200 day simple moving averages" icon="〰️">
                <section className="analytics-range-row" aria-label="Moving averages range selector">
                  {(["1M", "3M", "6M", "1Y"] as RangeKey[]).map((range) => (
                    <button key={range} type="button" className={`range-pill ${movingAverageRange === range ? "active" : ""}`} onClick={() => setMovingAverageRange(range)}>
                      {range}
                    </button>
                  ))}
                </section>
                <MultiLineChart
                  data={movingAverageHistory}
                  lines={[
                    { key: "sma20", label: "20 SMA", color: "#2563eb" },
                    { key: "sma50", label: "50 SMA", color: "#f97316" },
                    { key: "sma200", label: "200 SMA", color: "#16a34a" },
                  ]}
                  valueFormatter={(value) => formatMoney(value)}
                />
              </ChartCard>

              <MetricSection title="Trend" icon="📐" accent="teal">
                <MetricGrid
                  items={[
                    { label: "20-Day SMA", value: formatMaybeMoney(analytics.trendMetrics.sma20) },
                    { label: "50-Day SMA", value: formatMaybeMoney(analytics.trendMetrics.sma50) },
                    { label: "200-Day SMA", value: formatMaybeMoney(analytics.trendMetrics.sma200) },
                    { label: "Golden Cross", value: formatFlag(analytics.trendMetrics.goldenCross) },
                    { label: "Death Cross", value: formatFlag(analytics.trendMetrics.deathCross) },
                  ]}
                />
              </MetricSection>

              <ChartCard title="Rolling Volatility" subtitle="Risk trend across 20, 60 and 90-day windows" icon="🌊">
                <section className="analytics-range-row" aria-label="Rolling volatility range selector">
                  {(["1M", "3M", "6M", "1Y"] as RangeKey[]).map((range) => (
                    <button key={range} type="button" className={`range-pill ${rollingVolatilityRange === range ? "active" : ""}`} onClick={() => setRollingVolatilityRange(range)}>
                      {range}
                    </button>
                  ))}
                </section>
                <MultiLineChart
                  data={rollingVolatilityHistory}
                  lines={[
                    { key: "volatility20Day", label: "20D Volatility", color: "#0f766e" },
                    { key: "volatility60Day", label: "60D Volatility", color: "#2563eb" },
                    { key: "volatility90Day", label: "90D Volatility", color: "#dc2626" },
                  ]}
                  valueFormatter={(value) => `${formatMoney(value)}%`}
                />
              </ChartCard>

              <MetricSection title="Momentum" icon="💹" accent="indigo">
                <MetricGrid
                  items={[
                    { label: "RSI (14)", value: formatMaybePlain(analytics.momentumMetrics.rsi14) },
                    { label: "MACD", value: formatMaybePlain(analytics.momentumMetrics.macd), tone: summaryTone(analytics.momentumMetrics.macd) },
                    { label: "MACD Signal", value: formatMaybePlain(analytics.momentumMetrics.macdSignal), tone: summaryTone(analytics.momentumMetrics.macdSignal) },
                  ]}
                />
              </MetricSection>

              <MetricSection title="Performance Distribution" icon="🎯" subtitle="Based on last 1 year of trading data" accent="violet">
                <MetricGrid
                  items={[
                    { label: "Positive Days", value: analytics.performanceDistribution.positiveDays.toString(), tone: "positive" },
                    { label: "Negative Days", value: analytics.performanceDistribution.negativeDays.toString(), tone: "negative" },
                    { label: "Flat Days", value: analytics.performanceDistribution.flatDays.toString() },
                  ]}
                />
                <DistributionPieChart
                  positiveDays={analytics.performanceDistribution.positiveDays}
                  negativeDays={analytics.performanceDistribution.negativeDays}
                  flatDays={analytics.performanceDistribution.flatDays}
                />
              </MetricSection>

              <MetricSection title="Monthly Returns Heatmap" icon="🔥" subtitle="Month-by-month return percentage across all tracked years">
                <MonthlyReturnsHeatmap cells={analytics.monthlyReturnsHeatmap} />
              </MetricSection>

              <MetricSection title="Risk & Drawdown" icon="🛡️" accent="red">
                <MetricGrid
                  items={[
                    { label: "Sharpe Ratio", value: formatMaybePlain(analytics.riskMetrics.sharpeRatio), tone: summaryTone(analytics.riskMetrics.sharpeRatio) },
                    { label: "Sortino Ratio", value: formatMaybePlain(analytics.riskMetrics.sortinoRatio), tone: summaryTone(analytics.riskMetrics.sortinoRatio) },
                    { label: "Maximum Drawdown", value: formatMaybePercent(analytics.drawdownAnalysis.maxDrawdown), tone: "negative" },
                    { label: "Peak Date", value: formatDateLabel(analytics.drawdownAnalysis.peakDate) },
                    { label: "Trough Date", value: formatDateLabel(analytics.drawdownAnalysis.troughDate) },
                  ]}
                />
              </MetricSection>

              <section className="analytics-history-table-card">
                <div className="analytics-chart-head">
                  <div>
                    <h3>Recent Daily Data</h3>
                    <p>Latest OHLC and volume observations in the selected range.</p>
                  </div>
                </div>
                <div className="analytics-table-wrap">
                  <table className="analytics-table">
                    <thead>
                      <tr>
                        <th>Date</th>
                        <th>Open</th>
                        <th>High</th>
                        <th>Low</th>
                        <th>Close</th>
                        <th>Volume</th>
                      </tr>
                    </thead>
                    <tbody>
                      {rangeHistory.slice(-20).reverse().map((point) => (
                        <tr key={`${point.tradingDate}-${point.close}`}>
                          <td>{formatDateShort(point.tradingDate)}</td>
                          <td>{formatMaybeMoney(point.open)}</td>
                          <td>{formatMaybeMoney(point.high)}</td>
                          <td>{formatMaybeMoney(point.low)}</td>
                          <td>{formatMaybeMoney(point.close)}</td>
                          <td>{formatVolume(point.volume)}</td>
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
