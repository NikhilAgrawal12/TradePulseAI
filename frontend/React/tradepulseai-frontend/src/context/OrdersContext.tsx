import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import type { CartItem } from "../types/cart";
import { isUserAuthenticated, subscribeToAuthChanges } from "../utils/auth";

const STORAGE_KEY = "tradepulseai-orders";

export type Order = {
  id: string;
  createdAtIso: string;
  items: CartItem[];
  subtotal: number;
  tax: number;
  total: number;
};

type OrdersContextType = {
  orders: Order[];
  addOrder: (orderInput: Omit<Order, "id" | "createdAtIso">) => Order;
};

const OrdersContext = createContext<OrdersContextType | undefined>(undefined);

function readStoredOrders(): Order[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return [];
    }
    const parsed = JSON.parse(raw) as Order[];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function OrdersProvider({ children }: { children: ReactNode }) {
  const [orders, setOrders] = useState<Order[]>(() => readStoredOrders());

  useEffect(() => {
    return subscribeToAuthChanges(() => {
      if (!isUserAuthenticated()) {
        localStorage.removeItem(STORAGE_KEY);
        setOrders([]);
      }
    });
  }, []);

  const value = useMemo<OrdersContextType>(() => ({
    orders,
    addOrder: (orderInput) => {
      const order: Order = {
        id: `ord-${Date.now()}`,
        createdAtIso: new Date().toISOString(),
        ...orderInput,
      };

      setOrders((prev) => {
        const next = [order, ...prev];
        localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
        return next;
      });

      return order;
    },
  }), [orders]);

  return <OrdersContext.Provider value={value}>{children}</OrdersContext.Provider>;
}

export function useOrders() {
  const context = useContext(OrdersContext);
  if (!context) {
    throw new Error("useOrders must be used within OrdersProvider");
  }
  return context;
}

