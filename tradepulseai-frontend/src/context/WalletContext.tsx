import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { isUserAuthenticated, subscribeToAuthChanges } from "../utils/auth";
import { fetchWallet } from "../utils/walletApi";
import { toMoney } from "../utils/money";

type WalletContextType = {
  balance: number;
  isLoading: boolean;
  refreshWallet: () => Promise<void>;
};

const WalletContext = createContext<WalletContextType | undefined>(undefined);

export function WalletProvider({ children }: { children: ReactNode }) {
  const [balance, setBalance] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [authVersion, setAuthVersion] = useState(0);

  useEffect(() => {
    return subscribeToAuthChanges(() => {
      setAuthVersion((v) => v + 1);
    });
  }, []);

  const refreshWallet = useCallback(async () => {
    if (!isUserAuthenticated()) {
      setBalance(0);
      return;
    }
    setIsLoading(true);
    try {
      const wallet = await fetchWallet();
      setBalance(toMoney(wallet.balance));
    } catch {
      setBalance(0);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void refreshWallet();
  }, [authVersion, refreshWallet]);

  return (
    <WalletContext.Provider value={{ balance, isLoading, refreshWallet }}>
      {children}
    </WalletContext.Provider>
  );
}

export function useWallet() {
  const context = useContext(WalletContext);
  if (!context) {
    throw new Error("useWallet must be used within WalletProvider");
  }
  return context;
}

