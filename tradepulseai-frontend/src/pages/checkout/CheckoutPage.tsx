import { useEffect, useMemo } from "react";
import { Link, useNavigate } from "react-router";
import { Header } from "../../components/Header.tsx";
import { useCart } from "../../context/CartContext";
import { useStocks } from "../../utils/useStocks";
import { useWebSocketPrices } from "../../utils/useWebSocketPrices";
import "./CheckoutPage.css";

type EnrichedItem = {
  stockId: string;
  symbol: string;
  name: string | null;
  basePrice: number;
  quantity: number;
};

export function CheckoutPage() {
  useEffect(() => { document.title = "Order Cart | TradePulseAI"; }, []);

  const navigate = useNavigate();
  const { cart, removeFromCart, updateQuantity } = useCart();
  const { stocks } = useStocks();

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
          // prefer REST-API OHLC close; gRPC fallback gives 0
          basePrice: stock?.price ?? item.price ?? 0,
          quantity: Number(item.quantity),
        };
      }),
    [cart, stockById],
  );

  const symbols = useMemo(
    () => enrichedItems.map((item) => item.symbol),
    [enrichedItems],
  );
  const aggregateBySymbol = useWebSocketPrices(symbols);

  // Price chain: live websocket close → REST OHLC price → 0
  function livePrice(item: EnrichedItem): number {
    const agg = aggregateBySymbol[item.symbol.trim().toUpperCase()];
    return typeof agg?.close === "number" ? agg.close : item.basePrice;
  }

  function changePercent(item: EnrichedItem): number | null {
    const agg = aggregateBySymbol[item.symbol.trim().toUpperCase()];
    if (!agg || agg.previousClose === null || agg.previousClose <= 0) return null;
    return ((agg.close - agg.previousClose) / agg.previousClose) * 100;
  }

  const totalPrice = useMemo(
    () => enrichedItems.reduce((sum, item) => sum + livePrice(item) * item.quantity, 0),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [enrichedItems, aggregateBySymbol],
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
                      const change = changePercent(item);
                      const sym = item.symbol.trim().toUpperCase();
                      const isLive = typeof aggregateBySymbol[sym]?.close === "number";

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
                            {isLive && <span className="cart-live-badge">Live</span>}
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
