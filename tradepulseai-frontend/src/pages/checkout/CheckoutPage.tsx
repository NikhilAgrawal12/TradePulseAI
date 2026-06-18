import { useEffect, useMemo, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router";
import { Header } from "../../components/Header.tsx";
import { useCart } from "../../context/CartContext";
import { getMarketSession, getMarketSessionFromBackend, type SessionMeta } from "../../utils/marketSession";
import { roundMoney } from "../../utils/money";
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
  const [checkoutNotice, setCheckoutNotice] = useState<string | null>(null);

  // Update session badge every minute
  const [sessionMeta, setSessionMeta] = useState<SessionMeta>(() => getMarketSession());
  useEffect(() => {
    let cancelled = false;

    const refreshSession = async () => {
      const next = await getMarketSessionFromBackend();
      if (!cancelled) {
        setSessionMeta(next);
      }
    };

    void refreshSession();
    const id = window.setInterval(() => {
      void refreshSession();
    }, 60_000);

    return () => {
      cancelled = true;
      window.clearInterval(id);
    };
  }, []);
  const closedMarketMessage =
    "Markets are currently closed. Trading is available from 4:00 AM to 8:00 PM ET as follows: Pre-Market: 4:00 AM – 9:30 AM ET, Regular Market: 9:30 AM – 4:00 PM ET, After-Hours: 4:00 PM – 8:00 PM ET. Please try again when the market reopens at 4:00 AM ET.";
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
          basePrice: roundMoney(stock?.price ?? item.price ?? 0),
          changePercent: stock?.changePercent ?? null,
          source: stock?.source ?? null,
          quantity: Number(item.quantity),
        };
      }),
    [cart, stockById],
  );

  // Price chain: backend all-stocks cache close → cart fallback price → 0
  function livePrice(item: EnrichedItem): number {
    return roundMoney(item.basePrice);
  }

  const totalPrice = useMemo(
    () => roundMoney(enrichedItems.reduce((sum, item) => sum + livePrice(item) * item.quantity, 0)),
    [enrichedItems],
  );

  const isMarketClosed = sessionMeta.session === "closed";
  const visibleNotice = checkoutNotice ?? paymentCancelledMessage ?? (isMarketClosed ? closedMarketMessage : null);

  const handleQuantityChange = (stockId: string, qty: string) => {
    const newQty = parseInt(qty) || 0;
    void updateQuantity(stockId, newQty);
  };

  const handleCheckout = () => {
    if (isMarketClosed) {
      setCheckoutNotice(
        "Markets are currently closed. Trading is available from 4:00 AM to 8:00 PM ET as follows: Pre-Market: 4:00 AM – 9:30 AM ET, Regular Market: 9:30 AM – 4:00 PM ET, After-Hours: 4:00 PM – 8:00 PM ET. Please try again when the market reopens at 4:00 AM ET.",
      );
      return;
    }

    setCheckoutNotice(null);

    const pricedItems = enrichedItems.map((item) => ({
      stockId: item.stockId,
      symbol: item.symbol,
      price: roundMoney(livePrice(item)),
      quantity: item.quantity,
    }));

    navigate("/payment", {
      state: {
        subtotal: totalPrice,
        total: roundMoney(totalPrice),
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
          {visibleNotice && <p className="checkout-notice">{visibleNotice}</p>}

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
                <div className="summary-row total-row">
                  <span>Total</span>
                  <strong>${totalPrice.toFixed(2)}</strong>
                </div>

                <div className="checkout-actions">
                  <button className="checkout-btn" onClick={handleCheckout} disabled={isMarketClosed}>
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
