import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import type { OrderHistoryEntry } from "../types/order";
import { isUserAuthenticated, subscribeToAuthChanges } from "../utils/auth";
import { fetchOrderHistory } from "../utils/ordersApi";

type OrdersContextType = {
  orders: OrderHistoryEntry[];
  loading: boolean;
  error: string | null;
  refreshOrders: () => Promise<void>;
};

const OrdersContext = createContext<OrdersContextType | undefined>(undefined);

export function OrdersProvider({ children }: { children: ReactNode }) {
  const [orders, setOrders] = useState<OrderHistoryEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refreshOrders = useCallback(async () => {
    if (!isUserAuthenticated()) {
      setOrders([]);
      setError(null);
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const response = await fetchOrderHistory();
      setOrders(response);
    } catch (loadError) {
      const message = loadError instanceof Error ? loadError.message : "Failed to fetch order history.";
      setError(message);
      setOrders([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refreshOrders();

    return subscribeToAuthChanges(() => {
      if (!isUserAuthenticated()) {
        setOrders([]);
        setError(null);
      } else {
        void refreshOrders();
      }
    });
  }, [refreshOrders]);

  const value = useMemo<OrdersContextType>(() => ({
    orders,
    loading,
    error,
    refreshOrders,
  }), [orders, loading, error, refreshOrders]);

  return <OrdersContext.Provider value={value}>{children}</OrdersContext.Provider>;
}

export function useOrders() {
  const context = useContext(OrdersContext);
  if (!context) {
    throw new Error("useOrders must be used within OrdersProvider");
  }
  return context;
}
