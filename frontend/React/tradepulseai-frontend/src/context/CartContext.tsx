import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import type { CartItem } from "../types/cart";
import { isUserAuthenticated, showSignInRequiredMessage, getStoredToken } from "../utils/auth";
import { addCartItem, clearCartItems, fetchCartItems, removeCartItem, updateCartItemQuantity } from "../utils/cartApi";

const CART_ERROR_MESSAGE = "Unable to update cart right now. Please try again.";

type CartContextType = {
  cart: CartItem[];
  totalItems: number;
  addToCart: (stockId: string, symbol: string, price: number, qty: number) => Promise<void>;
  removeFromCart: (stockId: string) => Promise<void>;
  updateQuantity: (stockId: string, qty: number) => Promise<void>;
  clearCart: () => Promise<void>;
};

const CartContext = createContext<CartContextType | undefined>(undefined);

export function CartProvider({ children }: { children: ReactNode }) {
  const [cart, setCart] = useState<CartItem[]>([]);
  const token = getStoredToken();

  useEffect(() => {
    let cancelled = false;

    const loadCart = async () => {
      if (!isUserAuthenticated()) {
        if (!cancelled) {
          setCart([]);
        }
        return;
      }

      try {
        const items = await fetchCartItems();
        if (!cancelled) {
          setCart(items);
        }
      } catch {
        if (!cancelled) {
          setCart([]);
        }
      }
    };

    void loadCart();

    return () => {
      cancelled = true;
    };
  }, [token]);

  const addToCart = async (stockId: string, symbol: string, price: number, qty: number) => {
    if (!isUserAuthenticated()) {
      showSignInRequiredMessage();
      return;
    }

    try {
      const updatedCart = await addCartItem({
        stockId,
        symbol,
        price,
        quantity: qty,
      });

      setCart(updatedCart);
    } catch {
      showSignInRequiredMessage(CART_ERROR_MESSAGE);
    }
  };

  const removeFromCart = async (stockId: string) => {
    if (!isUserAuthenticated()) {
      showSignInRequiredMessage();
      return;
    }

    try {
      const updatedCart = await removeCartItem(stockId);
      setCart(updatedCart);
    } catch {
      showSignInRequiredMessage(CART_ERROR_MESSAGE);
    }
  };

  const updateQuantity = async (stockId: string, qty: number) => {
    if (!isUserAuthenticated()) {
      showSignInRequiredMessage();
      return;
    }

    if (qty <= 0) {
      await removeFromCart(stockId);
      return;
    }

    try {
      const updatedCart = await updateCartItemQuantity(stockId, { quantity: qty });
      setCart(updatedCart);
    } catch {
      showSignInRequiredMessage(CART_ERROR_MESSAGE);
    }
  };

  const clearCart = async () => {
    if (!isUserAuthenticated()) {
      showSignInRequiredMessage();
      return;
    }

    try {
      const updatedCart = await clearCartItems();
      setCart(updatedCart);
    } catch {
      showSignInRequiredMessage(CART_ERROR_MESSAGE);
    }
  };

  const totalItems = useMemo(() => cart.reduce((sum, item) => sum + item.quantity, 0), [cart]);

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

