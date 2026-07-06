import { useEffect, useMemo, useState, type ReactNode } from "react";
import { Link, useParams } from "react-router";
import { Header } from "../../components/Header.tsx";
import type { MonthlyReturnHeatmapCell, StockHistoryPoint, StockInsights, StockPrediction } from "../../types/stockInsights";
import type { Stock } from "../../types/stock";
import { fetchStockInsights, fetchStockPrediction } from "../../utils/stockInsightsApi";
import { formatMoney, formatPercent, formatSignedCurrency } from "../../utils/money";
import { useStreamedStocks } from "../../utils/useStreamedStocks";
import "./StockInsightsPage.css";

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
    // Date-only strings from the API are trading dates, so parse in local time to avoid UTC day shifts.
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
  if (!Number.isFinite(value)) {
    return "--";
  }
  if (Math.abs(value) >= 1_000_000_000) {
    return `${formatMoney(value / 1_000_000_000)}B`;
  }
  if (Math.abs(value) >= 1_000_000) {
    return `${formatMoney(value / 1_000_000)}M`;
  }
  if (Math.abs(value) >= 1_000) {
    return `${formatMoney(value / 1_000)}K`;
  }
   return formatMoney(value);
 }

 function filterHistoryByRange(history: StockHistoryPoint[], range: RangeKey): StockHistoryPoint[] {
  if (history.length === 0) {
    return [];
  }

  const latest = history[history.length - 1]?.tradingDate;
  if (!latest) {
    return history;
  }

  const latestDate = parseDisplayDate(latest);
  if (Number.isNaN(latestDate.getTime())) {
    return history;
  }

  const cutoff = new Date(latestDate);
  cutoff.setDate(cutoff.getDate() - RANGE_DAYS[range]);
  return history.filter((point) => {
    if (!point.tradingDate) {
      return false;
    }
    const pointDate = parseDisplayDate(point.tradingDate);
    return !Number.isNaN(pointDate.getTime()) && pointDate >= cutoff;
  });
}

function buildXAxisTicks(data: StockHistoryPoint[], maxTicks = 6): AxisTick[] {
  if (data.length === 0) {
    return [];
  }

  if (data.length === 1) {
    const onlyDate = data[0]?.tradingDate;
    return onlyDate
      ? [{
          index: 0,
          label: parseDisplayDate(onlyDate).toLocaleDateString(undefined, { month: "short", year: "2-digit" }),
        }]
      : [];
  }

  const requestedTicks = Math.max(2, Math.min(maxTicks, data.length));
  const step = (data.length - 1) / (requestedTicks - 1);
  const used = new Set<number>();
  const ticks: AxisTick[] = [];

  for (let i = 0; i < requestedTicks; i += 1) {
    const index = Math.round(i * step);
    if (used.has(index)) {
      continue;
    }
    used.add(index);
    const dateLabel = data[index]?.tradingDate;
    if (!dateLabel) {
      continue;
    }
    const parsed = parseDisplayDate(dateLabel);
    ticks.push({
      index,
      label: Number.isNaN(parsed.getTime())
        ? dateLabel
        : parsed.toLocaleDateString(undefined, { month: "short", year: "2-digit" }),
    });
  }

  const lastDateLabel = data[data.length - 1]?.tradingDate;
  if (!used.has(data.length - 1) && lastDateLabel) {
    const parsed = parseDisplayDate(lastDateLabel);
    ticks.push({
      index: data.length - 1,
      label: Number.isNaN(parsed.getTime())
        ? lastDateLabel
        : parsed.toLocaleDateString(undefined, { month: "short", year: "2-digit" }),
    });
  }

  return ticks.sort((left, right) => left.index - right.index);
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

function buildLinePath(
  data: StockHistoryPoint[],
  key: keyof StockHistoryPoint,
  left: number,
  top: number,
  innerWidth: number,
  innerHeight: number,
  min: number,
  max: number,
): string {
  if (data.length === 0) {
    return "";
  }

  return data
    .map((point, index) => {
      const rawValue = point[key];
      if (typeof rawValue !== "number" || !Number.isFinite(rawValue)) {
        return null;
      }
      const x = left + (index / Math.max(data.length - 1, 1)) * innerWidth;
      const y = top + innerHeight - ((rawValue - min) / Math.max(max - min, 1e-9)) * innerHeight;
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

function MetricSection({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  return (
    <section className="insights-section-card">
      <h3>{title}</h3>
      {subtitle && <p className="insights-section-subtitle">{subtitle}</p>}
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

function CandlestickLegend() {
  return (
    <div className="chart-legend-grid">
      <div className="legend-item">
        <svg viewBox="0 0 60 90" className="legend-candle">
          {/* Green candle (close > open) */}
          <line x1="30" y1="10" x2="30" y2="45" stroke="#16a34a" strokeWidth="1.5" />
          <rect x="24" y="25" width="12" height="20" fill="#16a34a" rx="1" />
          <text x="30" y="70" textAnchor="middle" className="legend-label">Close &gt; Open</text>
        </svg>
      </div>
      <div className="legend-item">
        <svg viewBox="0 0 60 90" className="legend-candle">
          {/* Red candle (close < open) */}
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
          {/* Green bar (positive return) */}
          <rect x="20" y="15" width="20" height="35" fill="#16a34a" rx="2" />
          <text x="30" y="70" textAnchor="middle" className="legend-label">Positive Day</text>
        </svg>
      </div>
      <div className="legend-item">
        <svg viewBox="0 0 60 90" className="legend-bars">
          {/* Red bar (negative return) */}
          <rect x="20" y="15" width="20" height="35" fill="#dc2626" rx="2" />
          <text x="30" y="70" textAnchor="middle" className="legend-label">Negative Day</text>
        </svg>
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
        style={{
          background: `conic-gradient(#16a34a 0 ${buyPct}%, #dc2626 ${buyPct}% 100%)`,
        }}
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

function MlConfidenceBar({ confidence }: { confidence: number }) {
  const pct = Math.max(0, Math.min(100, confidence * 100));
  return (
    <div className="ml-confidence-block">
      <div className="ml-confidence-head">
        <span>Confidence</span>
        <strong>{formatPercent(pct, false)}%</strong>
      </div>
      <div className="ml-confidence-track" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(pct)}>
        <div className="ml-confidence-fill" style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
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
     <div className="insights-chart-shell">
       <svg viewBox={`0 0 ${width} ${height}`} className="insights-svg-chart" role="img" aria-label="Stock line chart">
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
     <div className="insights-chart-shell">
       <svg viewBox={`0 0 ${width} ${height}`} className="insights-svg-chart" role="img" aria-label="Stock candlestick chart">
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
     <div className="insights-chart-shell">
       <svg viewBox={`0 0 ${width} ${height}`} className="insights-svg-chart" role="img" aria-label="Stock volume chart">
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
           const isPositive = (point.dailyReturnPercent ?? 0) >= 0;
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
     document.title = "Stock Insights | TradePulseAI";
   }, []);

   useEffect(() => {
     window.scrollTo({ top: 0, left: 0, behavior: "auto" });
   }, [stockId]);

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
          const [nextInsights, nextPrediction] = await Promise.all([
            fetchStockInsights(stockId),
            fetchStockPrediction(stockId).catch(() => null),
          ]);
         if (!mounted) {
           return;
         }
         setInsights(nextInsights);
          setPrediction(nextPrediction);
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

   // Set search term to filter streamed stocks by the current symbol
   useEffect(() => {
     if (!insights?.symbol) {
       setSearchTerm("");
       return;
     }
     setSearchTerm(insights.symbol);
   }, [insights?.symbol, setSearchTerm]);

   // Apply real-time updates from streamed stocks to insights
   useEffect(() => {
     if (!insights?.symbol || streamedStocks.length === 0) {
       return;
     }

     const matchedStock = streamedStocks.find((stock) =>
       stock.symbol.toUpperCase() === insights.symbol.toUpperCase()
     ) as Stock | undefined;

     if (!matchedStock) {
       return;
     }

     const livePrice = typeof matchedStock.price === "number" ? matchedStock.price : null;
     const liveChangePercent = typeof matchedStock.changePercent === "number" ? matchedStock.changePercent : null;

     setInsights((prev) => {
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

       return {
         ...prev,
         lastUpdated: matchedStock.lastUpdated ?? prev.lastUpdated,
         currentPerformance: {
           ...prev.currentPerformance,
           currentPrice: nextCurrentPrice,
           previousClose: nextPreviousClose,
           dailyChange: nextDailyChange,
           dailyChangePercent: liveChangePercent == null
             ? prev.currentPerformance.dailyChangePercent
             : liveChangePercent,
         },
       };
     });
   }, [insights?.symbol, streamedStocks]);

   const rangeHistory = useMemo(() => filterHistoryByRange(insights?.history ?? [], selectedRange), [insights?.history, selectedRange]);
   const candlestickHistory = useMemo(() => filterHistoryByRange(insights?.history ?? [], candlestickRange), [insights?.history, candlestickRange]);
   const volumeHistory = useMemo(() => filterHistoryByRange(insights?.history ?? [], volumeRange), [insights?.history, volumeRange]);
   const movingAverageHistory = useMemo(() => filterHistoryByRange(insights?.history ?? [], movingAverageRange), [insights?.history, movingAverageRange]);
   const rollingVolatilityHistory = useMemo(() => filterHistoryByRange(insights?.history ?? [], rollingVolatilityRange), [insights?.history, rollingVolatilityRange]);

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
                    { label: "Previous Day Close", value: formatMaybeMoney(insights.currentPerformance.previousClose) },
                    { label: "Daily Change ($)", value: formatMaybeSignedMoney(insights.currentPerformance.dailyChange), tone: summaryTone(insights.currentPerformance.dailyChange) },
                    { label: "Daily Change (%)", value: formatMaybePercent(insights.currentPerformance.dailyChangePercent), tone: summaryTone(insights.currentPerformance.dailyChangePercent) },
                  ]}
                />
              </MetricSection>

              <section className="insights-ml-section-card">
                <div className="insights-ml-head">
                  <h3>Machine Learning Signal</h3>
                  <p>Model summary and confidence for the next trading window.</p>
                </div>

                {prediction ? (
                  <div className="insights-ml-grid">
                    <div className="insights-ml-primary">
                      <div className="insights-ml-action-row">
                        <span>Action</span>
                        <strong
                          className={`insights-ml-action-chip ${prediction.action === "BUY" ? "buy" : prediction.action === "SELL" ? "sell" : "hold"}`}
                        >
                          {prediction.action}
                        </strong>
                      </div>
                      <MlConfidenceBar confidence={prediction.confidence} />
                      <div className="insights-ml-meta-row">
                        <span className="insights-ml-meta-pill">
                          <small>Horizon</small>
                          <strong>{prediction.horizonDays} trading days</strong>
                        </span>
                        <span className="insights-ml-meta-pill">
                          <small>Model</small>
                          <strong>{prediction.modelName}</strong>
                        </span>
                        <span className="insights-ml-meta-pill subtle">
                          <small>Updated</small>
                          <strong>{formatDateLabel(prediction.generatedAt)}</strong>
                        </span>
                      </div>
                      <div className="insights-ml-quality-grid">
                        <div className="insights-ml-quality-card">
                          <span>Balanced Accuracy</span>
                          <strong>{prediction.testBalancedAccuracy == null ? "--" : `${formatPercent(prediction.testBalancedAccuracy * 100, false)}%`}</strong>
                        </div>
                        <div className="insights-ml-quality-card">
                          <span>Precision</span>
                          <strong>{prediction.testPrecision == null ? "--" : `${formatPercent(prediction.testPrecision * 100, false)}%`}</strong>
                        </div>
                        <div className="insights-ml-quality-card">
                          <span>Recall</span>
                          <strong>{prediction.testRecall == null ? "--" : `${formatPercent(prediction.testRecall * 100, false)}%`}</strong>
                        </div>
                        <div className="insights-ml-quality-card">
                          <span>F1 Score</span>
                          <strong>{prediction.testF1 == null ? "--" : `${formatPercent(prediction.testF1 * 100, false)}%`}</strong>
                        </div>
                      </div>
                    </div>
                    <div className="insights-ml-visual">
                      <MlProbabilityDonut buyProbability={prediction.probabilityBuy} sellProbability={prediction.probabilitySell} />
                    </div>
                  </div>
                ) : (
                  <div className="insights-state-card error"><p>ML prediction is currently unavailable.</p></div>
                )}
              </section>

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
                    { label: "Volume As Of", value: formatDateLabel(insights.volumeMetrics.latestTradingDate) },
                    { label: "Latest Trading Day Volume", value: formatVolume(insights.volumeMetrics.latestTradingDayVolume) },
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
                  ]}
                />
              </MetricSection>

               <ChartCard title="Price History" subtitle="Line chart showing price movement over time">
                 <section className="insights-range-row" aria-label="Price history range selector">
                   {(["1M", "3M", "6M", "1Y", "3Y"] as RangeKey[]).map((range) => (
                     <button key={range} type="button" className={`range-pill ${selectedRange === range ? "active" : ""}`} onClick={() => setSelectedRange(range)}>
                       {range}
                     </button>
                   ))}
                 </section>
                 <MultiLineChart data={rangeHistory} lines={[{ key: "close", label: "Price", color: "#7c3aed" }]} valueFormatter={(value) => formatMoney(value)} />
               </ChartCard>

               <ChartCard title="Candlestick Chart">
                 <section className="insights-range-row" aria-label="Candlestick range selector">
                   {(["1M", "3M", "6M", "1Y"] as const).map((range) => (
                     <button key={range} type="button" className={`range-pill ${candlestickRange === range ? "active" : ""}`} onClick={() => setCandlestickRange(range as RangeKey)}>
                       {range}
                     </button>
                   ))}
                 </section>
                 <CandlestickChart data={candlestickHistory} />
                 <CandlestickLegend />
               </ChartCard>

               <ChartCard title="Volume Chart">
                 <section className="insights-range-row" aria-label="Volume range selector">
                   {(["1M", "3M", "6M", "1Y"] as const).map((range) => (
                     <button key={range} type="button" className={`range-pill ${volumeRange === range ? "active" : ""}`} onClick={() => setVolumeRange(range as RangeKey)}>
                       {range}
                     </button>
                   ))}
                 </section>
                 <VolumeChart data={volumeHistory} />
                 <VolumeLegend />
               </ChartCard>

              <ChartCard title="Moving Averages" subtitle="20, 50, and 200 day SMAs">
                <section className="insights-range-row" aria-label="Moving averages range selector">
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

              <ChartCard title="Rolling Volatility" subtitle="Risk trend across 30-day and 90-day windows">
                <section className="insights-range-row" aria-label="Rolling volatility range selector">
                  {(["1M", "3M", "6M", "1Y"] as RangeKey[]).map((range) => (
                    <button key={range} type="button" className={`range-pill ${rollingVolatilityRange === range ? "active" : ""}`} onClick={() => setRollingVolatilityRange(range)}>
                      {range}
                    </button>
                  ))}
                </section>
                <MultiLineChart
                  data={rollingVolatilityHistory}
                  lines={[
                    { key: "volatility30Day", label: "30D Volatility", color: "#dc2626" },
                    { key: "volatility90Day", label: "90D Volatility", color: "#0f766e" },
                  ]}
                  valueFormatter={(value) => `${formatMoney(value)}%`}
                />
              </ChartCard>

              <MetricSection title="Performance Distribution" subtitle="Based on last 1 year of trading data">
                <MetricGrid
                  items={[
                    { label: "Positive Days", value: insights.performanceDistribution.positiveDays.toString() },
                    { label: "Negative Days", value: insights.performanceDistribution.negativeDays.toString() },
                    { label: "Flat Days", value: insights.performanceDistribution.flatDays.toString() },
                  ]}
                />
                <DistributionPieChart
                  positiveDays={insights.performanceDistribution.positiveDays}
                  negativeDays={insights.performanceDistribution.negativeDays}
                  flatDays={insights.performanceDistribution.flatDays}
                />
              </MetricSection>

              <MetricSection title="Monthly Returns Heatmap">
                <MonthlyReturnsHeatmap cells={insights.monthlyReturnsHeatmap} />
              </MetricSection>

              <MetricSection title="Drawdown Analysis">
                <MetricGrid
                  items={[
                    { label: "Maximum Drawdown", value: formatMaybePercent(insights.drawdownAnalysis.maxDrawdown), tone: "negative" },
                    { label: "Peak Date", value: formatDateLabel(insights.drawdownAnalysis.peakDate) },
                    { label: "Trough Date", value: formatDateLabel(insights.drawdownAnalysis.troughDate) },
                  ]}
                />
              </MetricSection>


              <MetricSection title="Advanced Metrics">
                <MetricGrid
                  items={[
                    { label: "Sharpe Ratio", value: formatMaybePlain(insights.riskMetrics.sharpeRatio), tone: summaryTone(insights.riskMetrics.sharpeRatio) },
                    { label: "Sortino Ratio", value: formatMaybePlain(insights.riskMetrics.sortinoRatio), tone: summaryTone(insights.riskMetrics.sortinoRatio) },
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
                    <p>Latest OHLC and volume observations in the selected range.</p>
                  </div>
                </div>
                <div className="insights-table-wrap">
                  <table className="insights-table">
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
