import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import type { WatchlistEntry } from "../types/watchlist";
import { getStoredToken, isUserAuthenticated, showSignInRequiredMessage } from "../utils/auth";
import { addWatchlistItem, clearWatchlistItems, fetchWatchlistItems, removeWatchlistItem, updateWatchlistItem } from "../utils/watchlistApi";

const WATCHLIST_ERROR_MESSAGE = "Unable to update watchlist right now. Please try again.";

type WatchlistContextType = {
  watchlist: WatchlistEntry[];
  totalWatchlistItems: number;
  addToWatchlist: (stockId: string, symbol: string, refPrice: number, quantity: number) => Promise<void>;
  removeFromWatchlist: (stockId: string) => Promise<void>;
  updateWatchlistEntry: (stockId: string, refPrice: number, quantity: number) => Promise<void>;
  clearWatchlist: () => Promise<void>;
};

const WatchlistContext = createContext<WatchlistContextType | undefined>(undefined);

export function WatchlistProvider({ children }: { children: ReactNode }) {
  const [watchlist, setWatchlist] = useState<WatchlistEntry[]>([]);
  const token = getStoredToken();

  useEffect(() => {
    let cancelled = false;

    const loadWatchlist = async () => {
      if (!isUserAuthenticated()) {
        if (!cancelled) {
          setWatchlist([]);
        }
        return;
      }

      try {
        const items = await fetchWatchlistItems();
        if (!cancelled) {
          setWatchlist(items);
        }
      } catch {
        if (!cancelled) {
          setWatchlist([]);
        }
      }
    };

    void loadWatchlist();

    return () => {
      cancelled = true;
    };
  }, [token]);

  const addToWatchlist = async (stockId: string, symbol: string, refPrice: number, quantity: number) => {
    if (!isUserAuthenticated()) {
      showSignInRequiredMessage();
      return;
    }

    try {
      const updatedWatchlist = await addWatchlistItem({
        stockId,
        symbol,
        refPrice,
        quantity,
      });
      setWatchlist(updatedWatchlist);
    } catch {
      showSignInRequiredMessage(WATCHLIST_ERROR_MESSAGE);
    }
  };

  const removeFromWatchlist = async (stockId: string) => {
    if (!isUserAuthenticated()) {
      showSignInRequiredMessage();
      return;
    }

    try {
      const updatedWatchlist = await removeWatchlistItem(stockId);
      setWatchlist(updatedWatchlist);
    } catch {
      showSignInRequiredMessage(WATCHLIST_ERROR_MESSAGE);
    }
  };

  const updateWatchlistEntry = async (stockId: string, refPrice: number, quantity: number) => {
    if (!isUserAuthenticated()) {
      showSignInRequiredMessage();
      return;
    }

    if (quantity <= 0) {
      await removeFromWatchlist(stockId);
      return;
    }

    try {
      const updatedWatchlist = await updateWatchlistItem(stockId, { refPrice, quantity });
      setWatchlist(updatedWatchlist);
    } catch {
      showSignInRequiredMessage(WATCHLIST_ERROR_MESSAGE);
    }
  };

  const clearWatchlist = async () => {
    if (!isUserAuthenticated()) {
      showSignInRequiredMessage();
      return;
    }

    try {
      const updatedWatchlist = await clearWatchlistItems();
      setWatchlist(updatedWatchlist);
    } catch {
      showSignInRequiredMessage(WATCHLIST_ERROR_MESSAGE);
    }
  };

  const totalWatchlistItems = useMemo(() => watchlist.reduce((sum, item) => sum + item.quantity, 0), [watchlist]);

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
