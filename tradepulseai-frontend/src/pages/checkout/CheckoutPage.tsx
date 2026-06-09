import { useEffect, useMemo, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router";
import { Header } from "../../components/Header.tsx";
import { useCart } from "../../context/CartContext";
import { getMarketSession, type SessionMeta } from "../../utils/marketSession";
import { useStocks } from "../../utils/useStocks";
import "./CheckoutPage.css";

type EnrichedItem = {
  stockId: string;
  symbol: string;
  name: string | null;
  basePrice: number;
  changePercent: number | null;
  source: string | null;
  quantity: number;
};

export function CheckoutPage() {
  useEffect(() => { document.title = "Order Cart | TradePulseAI"; }, []);

  const navigate = useNavigate();
  const location = useLocation();
  const { cart, removeFromCart, updateQuantity } = useCart();
  const { stocks } = useStocks();

  // Update session badge every minute
  const [sessionMeta, setSessionMeta] = useState<SessionMeta>(() => getMarketSession());
  useEffect(() => {
    const id = window.setInterval(() => setSessionMeta(getMarketSession()), 60_000);
    return () => window.clearInterval(id);
  }, []);
  const paymentCancelledMessage = (location.state as { reason?: string; paymentCancelled?: boolean } | null)?.paymentCancelled
    ? (location.state as { reason?: string }).reason ?? "Payment was cancelled."
    : null;

  // stockId → Stock lookup so we always have the correct symbol/name/price
  // even when the backend gRPC fallback returns a numeric stockId as the symbol
  const stockById = useMemo(
    () => new Map(stocks.map((s) => [s.id, s])),
    [stocks],
  );

  const enrichedItems: EnrichedItem[] = useMemo(
    () =>
      cart.map((item) => {
        const stock = stockById.get(item.stockId);
        return {
          stockId: item.stockId,
          // prefer REST-API symbol; gRPC fallback gives numeric IDs
          symbol: stock?.symbol ?? item.symbol,
          name: stock?.name ?? null,
          // prefer backend cached aggregate close; gRPC fallback gives 0
          basePrice: stock?.price ?? item.price ?? 0,
          changePercent: stock?.changePercent ?? null,
          source: stock?.source ?? null,
          quantity: Number(item.quantity),
        };
      }),
    [cart, stockById],
  );

  // Price chain: backend all-stocks cache close → cart fallback price → 0
  function livePrice(item: EnrichedItem): number {
    return item.basePrice;
  }

  const totalPrice = useMemo(
    () => enrichedItems.reduce((sum, item) => sum + livePrice(item) * item.quantity, 0),
    [enrichedItems],
  );

  const handleQuantityChange = (stockId: string, qty: string) => {
    const newQty = parseInt(qty) || 0;
    void updateQuantity(stockId, newQty);
  };

  const handleCheckout = () => {
    const tax = totalPrice * 0.08;
    const pricedItems = enrichedItems.map((item) => ({
      stockId: item.stockId,
      symbol: item.symbol,
      price: livePrice(item),
      quantity: item.quantity,
    }));

    navigate("/payment", {
      state: {
        subtotal: totalPrice,
        tax,
        total: totalPrice + tax,
        items: pricedItems,
      },
    });
  };

  return (
    <>
      <Header />
      <main className="checkout-page">
        <div className="checkout-container">
          <h1>Order Cart</h1>
          {paymentCancelledMessage && <p className="checkout-notice">{paymentCancelledMessage}</p>}

          {cart.length === 0 ? (
            <div className="checkout-empty">
              <p>Your cart is empty</p>
              <Link to="/" className="continue-shopping-btn">← Continue browsing</Link>
            </div>
          ) : (
            <>
              <div className="checkout-table-wrap">
                <table className="checkout-table">
                  <thead>
                    <tr>
                      <th>Stock</th>
                      <th>Price</th>
                      <th>Change</th>
                      <th>Quantity</th>
                      <th>Total</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    {enrichedItems.map((item) => {
                      const price = livePrice(item);
                      const change = item.changePercent;
                      const isLive = item.source === "all-stocks-cache";

                      return (
                        <tr key={item.stockId}>
                          <td className="cart-symbol">
                            <strong>{item.symbol}</strong>
                            {item.name && (
                              <span className="cart-stock-name">{item.name}</span>
                            )}
                          </td>
                          <td>
                            <span className="cart-price">${price.toFixed(2)}</span>
                            {isLive && <span className={`cart-session-badge ${sessionMeta.cssClass}`}>{sessionMeta.label}</span>}
                          </td>
                          <td>
                            {change !== null ? (
                              <span className={change >= 0 ? "price-up" : "price-down"}>
                                {change >= 0 ? "+" : ""}{change.toFixed(2)}%
                              </span>
                            ) : (
                              <span className="cart-no-data">--</span>
                            )}
                          </td>
                          <td>
                            <input
                              type="number"
                              min="1"
                              value={Math.max(1, Math.round(item.quantity))}
                              onChange={(e) => handleQuantityChange(item.stockId, e.target.value)}
                              className="quantity-input"
                            />
                          </td>
                          <td className="cart-total">${(price * item.quantity).toFixed(2)}</td>
                          <td>
                            <button
                              className="remove-btn"
                              onClick={() => void removeFromCart(item.stockId)}
                              title="Remove from cart"
                            >✕</button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>

              <div className="checkout-summary">
                <div className="summary-row">
                  <span>Order Total</span>
                  <strong>${totalPrice.toFixed(2)}</strong>
                </div>
                <div className="summary-row">
                  <span>Tax (8%)</span>
                  <strong>${(totalPrice * 0.08).toFixed(2)}</strong>
                </div>
                <div className="summary-divider" />
                <div className="summary-row total-row">
                  <span>Total</span>
                  <strong>${(totalPrice * 1.08).toFixed(2)}</strong>
                </div>

                <div className="checkout-actions">
                  <button className="checkout-btn" onClick={handleCheckout}>
                    Complete Order
                  </button>
                  <Link to="/" className="continue-shopping">
                    ← Continue browsing
                  </Link>
                </div>
              </div>
            </>
          )}
        </div>
      </main>
    </>
  );
}
