export type PortfolioSummary = {
  totalPositions: number;
  totalQuantity: number;
  totalInvestedValue: number;
  totalMarketValue: number;
  totalUnrealizedPnl: number;
  totalUnrealizedPnlPercent: number;
  totalRealizedPnl: number;
};

export type PortfolioHolding = {
  stockId: string;
  symbol?: string | null;
  companyName?: string | null;
  exchange?: string | null;
  quantity: number;
  averageBuyPrice: number;
  currentPrice: number;
  investedValue: number;
  marketValue: number;
  unrealizedPnl: number;
  unrealizedPnlPercent: number;
  realizedPnl: number;
};

export type PortfolioTransaction = {
  transactionId: number;
  stockId: string;
  symbol?: string | null;
  companyName?: string | null;
  exchange?: string | null;
  transactionType: "BUY" | "SELL" | string;
  price: number;
  quantity: number;
  grossAmount: number;
  realizedPnl: number;
  executedAt: string;
};

export type PortfolioResponse = {
  summary: PortfolioSummary;
  holdings: PortfolioHolding[];
  transactions: PortfolioTransaction[];
};

export type SellPortfolioItemRequest = {
  quantity: number;
  price: number;
};
