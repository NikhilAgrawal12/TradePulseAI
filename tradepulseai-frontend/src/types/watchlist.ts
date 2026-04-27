export type WatchlistEntry = {
  stockId: string;
  quantity: number;
};

export type AddWatchlistItemRequest = {
  stockId: string;
  quantity: number;
};

export type UpdateWatchlistItemRequest = {
  quantity: number;
};

