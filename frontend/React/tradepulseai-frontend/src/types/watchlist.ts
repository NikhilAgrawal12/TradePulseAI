export type WatchlistEntry = {
  id?: string;
  stockId: string;
  symbol: string;
  refPrice: number;
  quantity: number;
};

export type AddWatchlistItemRequest = {
  stockId: string;
  symbol: string;
  refPrice: number;
  quantity: number;
};

export type UpdateWatchlistItemRequest = {
  refPrice: number;
  quantity: number;
};

