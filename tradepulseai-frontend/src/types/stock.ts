export type Stock = {
  id: string;
  symbol: string;
  name?: string | null;
  exchange?: string | null;
  market?: string | null;
  locale?: string | null;
  active?: boolean | null;
  price?: number | null;
  changePercent?: number | null;
  volume?: number | null;
  open?: number | null;
  high?: number | null;
  low?: number | null;
  vwap?: number | null;
  source?: string | null;
  lastUpdated?: string | null;
};
