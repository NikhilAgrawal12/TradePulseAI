import { createContext, useContext, useState, type ReactNode } from "react";

export type WatchlistEntry = {
  stockId: string;
  symbol: string;
  refPrice: number;
  quantity: number;
};

type WatchlistContextType = {
  watchlist: WatchlistEntry[];
  totalWatchlistItems: number;
  addToWatchlist: (stockId: string, symbol: string, refPrice: number, quantity: number) => void;
  removeFromWatchlist: (stockId: string) => void;
  updateWatchlistEntry: (stockId: string, refPrice: number, quantity: number) => void;
  clearWatchlist: () => void;
};

const WatchlistContext = createContext<WatchlistContextType | undefined>(undefined);

export function WatchlistProvider({ children }: { children: ReactNode }) {
  const [watchlist, setWatchlist] = useState<WatchlistEntry[]>([]);

  const addToWatchlist = (stockId: string, symbol: string, refPrice: number, quantity: number) => {
    setWatchlist((prev) => {
      const existing = prev.find((item) => item.stockId === stockId);
      if (existing) {
        return prev.map((item) =>
          item.stockId === stockId
            ? { ...item, quantity: item.quantity + quantity }
            : item
        );
      }
      return [...prev, { stockId, symbol, refPrice, quantity }];
    });
  };

  const removeFromWatchlist = (stockId: string) => {
    setWatchlist((prev) => prev.filter((item) => item.stockId !== stockId));
  };

  const updateWatchlistEntry = (stockId: string, refPrice: number, quantity: number) => {
    setWatchlist((prev) =>
      prev.map((item) =>
        item.stockId === stockId
          ? { ...item, refPrice, quantity }
          : item
      )
    );
  };

  const clearWatchlist = () => setWatchlist([]);

  const totalWatchlistItems = watchlist.reduce((sum, item) => sum + item.quantity, 0);

  return (
    <WatchlistContext.Provider
      value={{ watchlist, totalWatchlistItems, addToWatchlist, removeFromWatchlist, updateWatchlistEntry, clearWatchlist }}
    >
      {children}
    </WatchlistContext.Provider>
  );
}

export function useWatchlist() {
  const context = useContext(WatchlistContext);
  if (!context) {
    throw new Error("useWatchlist must be used within WatchlistProvider");
  }
  return context;
}

