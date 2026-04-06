export type StockRecommendation = "BUY" | "HOLD" | "SELL";

export type Stock = {
  id: string;
  symbol: string;
  name: string;
  sector: string;
  exchange: string;
  price: number;
  changePercent: number;
  rating: {
    score: number;
    analysts: number;
  };
  marketCapBillion: number;
  volume: number;
  recommendation: StockRecommendation;
  keywords: string[];
};

