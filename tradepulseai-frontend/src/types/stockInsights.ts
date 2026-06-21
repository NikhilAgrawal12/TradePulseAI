export type StockHistoryPoint = {
  tradingDate: string | null;
  open: number | null;
  high: number | null;
  low: number | null;
  close: number | null;
  volume: number | null;
  sma20: number | null;
  sma50: number | null;
  sma200: number | null;
  volatility30Day: number | null;
  volatility90Day: number | null;
  volatility1Year: number | null;
  dailyReturnPercent: number | null;
};

export type MonthlyReturnHeatmapCell = {
  year: number;
  month: number;
  returnPercent: number | null;
};

export type StockInsights = {
  id: string;
  symbol: string;
  name: string;
  exchange: string | null;
  market: string | null;
  lastUpdated: string | null;
  currentPerformance: {
    currentPrice: number | null;
    previousClose: number | null;
    dailyChange: number | null;
    dailyChangePercent: number | null;
  };
  metrics52Week: {
    high52Week: number | null;
    low52Week: number | null;
    distanceFromHighPercent: number | null;
    distanceFromLowPercent: number | null;
  };
  returns: {
    oneWeekReturn: number | null;
    oneMonthReturn: number | null;
    threeMonthReturn: number | null;
    sixMonthReturn: number | null;
    oneYearReturn: number | null;
    threeYearReturn: number | null;
  };
  volumeMetrics: {
    latestTradingDayVolume: number | null;
    latestTradingDate: string | null;
    average30DayVolume: number | null;
    relativeVolume: number | null;
  };
  volatilityMetrics: {
    volatility30Day: number | null;
    volatility90Day: number | null;
    volatility1Year: number | null;
  };
  trendMetrics: {
    sma20: number | null;
    sma50: number | null;
    sma200: number | null;
    goldenCross: boolean | null;
    deathCross: boolean | null;
  };
  momentumMetrics: {
    rsi14: number | null;
    macd: number | null;
    macdSignal: number | null;
    momentum30Day: number | null;
  };
  riskMetrics: {
    sharpeRatio: number | null;
    sortinoRatio: number | null;
    maxDrawdown: number | null;
    betaVsSp500: number | null;
  };
  performanceDistribution: {
    positiveDays: number;
    negativeDays: number;
    flatDays: number;
  };
  volumeDistribution: {
    minVolume: number | null;
    averageVolume: number | null;
    maxVolume: number | null;
  };
  drawdownAnalysis: {
    maxDrawdown: number | null;
    peakDate: string | null;
    troughDate: string | null;
  };
  bestWorstDays: {
    bestDailyGain: number | null;
    bestDailyGainDate: string | null;
    worstDailyLoss: number | null;
    worstDailyLossDate: string | null;
  };
  monthlyReturnsHeatmap: MonthlyReturnHeatmapCell[];
  history: StockHistoryPoint[];
};

