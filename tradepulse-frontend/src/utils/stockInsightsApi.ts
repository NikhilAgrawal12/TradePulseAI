import axios from "axios";
import type { StockInsights, StockHistoryPoint, StockPrediction } from "../types/stockInsights";
import { toMoney } from "./money";

export type AnalyticsNewsItem = {
  stockId: number;
  symbol: string;
  tradingDate: string | null;
  news: string | null;
};

function normalizePoint(point: StockHistoryPoint): StockHistoryPoint {
  return {
    ...point,
    open: point.open == null ? null : toMoney(point.open),
    high: point.high == null ? null : toMoney(point.high),
    low: point.low == null ? null : toMoney(point.low),
    close: point.close == null ? null : toMoney(point.close),
    sma20: point.sma20 == null ? null : toMoney(point.sma20),
    sma50: point.sma50 == null ? null : toMoney(point.sma50),
    sma200: point.sma200 == null ? null : toMoney(point.sma200),
    volatility5Day: point.volatility5Day == null ? null : toMoney(point.volatility5Day),
    volatility20Day: point.volatility20Day == null ? null : toMoney(point.volatility20Day),
    volatility60Day: point.volatility60Day == null ? null : toMoney(point.volatility60Day),
    volatility90Day: point.volatility90Day == null ? null : toMoney(point.volatility90Day),
    volatility120Day: point.volatility120Day == null ? null : toMoney(point.volatility120Day),
    return1d: point.return1d == null ? null : toMoney(point.return1d),
  };
}

export async function fetchStockInsights(stockId: string): Promise<StockInsights> {
  const response = await axios.get<StockInsights>(`/api/stocks/${stockId}/insights`);
  const data = response.data;

  return {
    ...data,
    currentPerformance: {
      currentPrice: data.currentPerformance.currentPrice == null ? null : toMoney(data.currentPerformance.currentPrice),
      previousClose: data.currentPerformance.previousClose == null ? null : toMoney(data.currentPerformance.previousClose),
      dailyChange: data.currentPerformance.dailyChange == null ? null : toMoney(data.currentPerformance.dailyChange),
      dailyChangePercent: data.currentPerformance.dailyChangePercent == null ? null : toMoney(data.currentPerformance.dailyChangePercent),
    },
    metrics52Week: {
      high52Week: data.metrics52Week.high52Week == null ? null : toMoney(data.metrics52Week.high52Week),
      low52Week: data.metrics52Week.low52Week == null ? null : toMoney(data.metrics52Week.low52Week),
      distanceFromHighPercent: data.metrics52Week.distanceFromHighPercent == null ? null : toMoney(data.metrics52Week.distanceFromHighPercent),
      distanceFromLowPercent: data.metrics52Week.distanceFromLowPercent == null ? null : toMoney(data.metrics52Week.distanceFromLowPercent),
    },
    returns: {
      oneWeekReturn: data.returns.oneWeekReturn == null ? null : toMoney(data.returns.oneWeekReturn),
      oneMonthReturn: data.returns.oneMonthReturn == null ? null : toMoney(data.returns.oneMonthReturn),
      threeMonthReturn: data.returns.threeMonthReturn == null ? null : toMoney(data.returns.threeMonthReturn),
      sixMonthReturn: data.returns.sixMonthReturn == null ? null : toMoney(data.returns.sixMonthReturn),
      oneYearReturn: data.returns.oneYearReturn == null ? null : toMoney(data.returns.oneYearReturn),
      threeYearReturn: data.returns.threeYearReturn == null ? null : toMoney(data.returns.threeYearReturn),
    },
    volumeMetrics: {
      ...data.volumeMetrics,
      average30DayVolume: data.volumeMetrics.average30DayVolume == null ? null : toMoney(data.volumeMetrics.average30DayVolume),
      relativeVolume: data.volumeMetrics.relativeVolume == null ? null : toMoney(data.volumeMetrics.relativeVolume),
    },
    volatilityMetrics: {
      volatility5Day: data.volatilityMetrics.volatility5Day == null ? null : toMoney(data.volatilityMetrics.volatility5Day),
      volatility20Day: data.volatilityMetrics.volatility20Day == null ? null : toMoney(data.volatilityMetrics.volatility20Day),
      volatility60Day: data.volatilityMetrics.volatility60Day == null ? null : toMoney(data.volatilityMetrics.volatility60Day),
      volatility90Day: data.volatilityMetrics.volatility90Day == null ? null : toMoney(data.volatilityMetrics.volatility90Day),
      volatility120Day: data.volatilityMetrics.volatility120Day == null ? null : toMoney(data.volatilityMetrics.volatility120Day),
    },
    trendMetrics: {
      ...data.trendMetrics,
      sma20: data.trendMetrics.sma20 == null ? null : toMoney(data.trendMetrics.sma20),
      sma50: data.trendMetrics.sma50 == null ? null : toMoney(data.trendMetrics.sma50),
      sma200: data.trendMetrics.sma200 == null ? null : toMoney(data.trendMetrics.sma200),
    },
    momentumMetrics: {
      rsi14: data.momentumMetrics.rsi14 == null ? null : toMoney(data.momentumMetrics.rsi14),
      macd: data.momentumMetrics.macd == null ? null : toMoney(data.momentumMetrics.macd),
      macdSignal: data.momentumMetrics.macdSignal == null ? null : toMoney(data.momentumMetrics.macdSignal),
    },
    riskMetrics: {
      sharpeRatio: data.riskMetrics.sharpeRatio == null ? null : toMoney(data.riskMetrics.sharpeRatio),
      sortinoRatio: data.riskMetrics.sortinoRatio == null ? null : toMoney(data.riskMetrics.sortinoRatio),
      maxDrawdown: data.riskMetrics.maxDrawdown == null ? null : toMoney(data.riskMetrics.maxDrawdown),
    },
    drawdownAnalysis: {
      ...data.drawdownAnalysis,
      maxDrawdown: data.drawdownAnalysis.maxDrawdown == null ? null : toMoney(data.drawdownAnalysis.maxDrawdown),
    },
    monthlyReturnsHeatmap: data.monthlyReturnsHeatmap.map((cell) => ({
      ...cell,
      returnPercent: cell.returnPercent == null ? null : toMoney(cell.returnPercent),
    })),
    history: data.history.map(normalizePoint),
  };
}

export async function fetchStockPrediction(stockId: string): Promise<StockPrediction> {
  const response = await axios.get<StockPrediction>(`/api/stocks/${stockId}/prediction`);
  return response.data;
}

export async function fetchAnalyticsNews(limit = 10): Promise<AnalyticsNewsItem[]> {
  const response = await axios.get<AnalyticsNewsItem[]>(`/api/stocks/analytics/news`, { params: { limit } });
  return response.data;
}

