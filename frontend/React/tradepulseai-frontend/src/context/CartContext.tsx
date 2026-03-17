import { createContext, useContext, useState, type ReactNode } from "react";

export type CartItem = {
  stockId: string;
  symbol: string;
  price: number;
  quantity: number;
};

type CartContextType = {
  cart: CartItem[];
  totalItems: number;
  addToCart: (stockId: string, symbol: string, price: number, qty: number) => void;
  removeFromCart: (stockId: string) => void;
  updateQuantity: (stockId: string, qty: number) => void;
  clearCart: () => void;
};

const CartContext = createContext<CartContextType | undefined>(undefined);

export function CartProvider({ children }: { children: ReactNode }) {
  const [cart, setCart] = useState<CartItem[]>([]);

  const addToCart = (stockId: string, symbol: string, price: number, qty: number) => {
    setCart((prev) => {
      const existing = prev.find((item) => item.stockId === stockId);
      if (existing) {
        return prev.map((item) =>
          item.stockId === stockId
            ? { ...item, quantity: item.quantity + qty }
            : item
        );
      }
      return [...prev, { stockId, symbol, price, quantity: qty }];
    });
  };

  const removeFromCart = (stockId: string) => {
    setCart((prev) => prev.filter((item) => item.stockId !== stockId));
  };

  const updateQuantity = (stockId: string, qty: number) => {
    if (qty <= 0) {
      removeFromCart(stockId);
    } else {
      setCart((prev) =>
        prev.map((item) =>
          item.stockId === stockId ? { ...item, quantity: qty } : item
        )
      );
    }
  };

  const clearCart = () => setCart([]);

  const totalItems = cart.reduce((sum, item) => sum + item.quantity, 0);

  return (
    <CartContext.Provider value={{ cart, totalItems, addToCart, removeFromCart, updateQuantity, clearCart }}>
      {children}
    </CartContext.Provider>
  );
}

export function useCart() {
  const context = useContext(CartContext);
  if (!context) {
    throw new Error("useCart must be used within CartProvider");
  }
  return context;
}


