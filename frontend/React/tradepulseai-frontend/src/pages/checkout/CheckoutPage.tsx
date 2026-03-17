import { useEffect } from "react";
import { Link } from "react-router";
import { useNavigate } from "react-router";
import { Header } from "../../components/Header.tsx";
import { useCart, type CartItem } from "../../context/CartContext";
import "./CheckoutPage.css";

export function CheckoutPage() {
  useEffect(() => { document.title = "Order Cart | TradePulseAI"; }, []);

  const navigate = useNavigate();
  const { cart, removeFromCart, updateQuantity } = useCart();

  const totalPrice = cart.reduce((sum: number, item: CartItem) => sum + item.price * item.quantity, 0);

  const handleQuantityChange = (stockId: string, qty: string) => {
    const newQty = parseInt(qty) || 0;
    updateQuantity(stockId, newQty);
  };

  const handleCheckout = () => {
    const tax = totalPrice * 0.08;
    navigate("/payment", {
      state: {
        subtotal: totalPrice,
        tax,
        total: totalPrice + tax,
        items: cart,
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
                      <th>Symbol</th>
                      <th>Price</th>
                      <th>Quantity</th>
                      <th>Total</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    {cart.map((item: CartItem) => (
                      <tr key={item.stockId}>
                        <td className="cart-symbol"><strong>{item.symbol}</strong></td>
                        <td>${item.price.toFixed(2)}</td>
                        <td>
                          <input
                            type="number"
                            min="1"
                            value={item.quantity}
                            onChange={(e) => handleQuantityChange(item.stockId, e.target.value)}
                            className="quantity-input"
                          />
                        </td>
                        <td className="cart-total">${(item.price * item.quantity).toFixed(2)}</td>
                        <td>
                          <button
                            className="remove-btn"
                            onClick={() => removeFromCart(item.stockId)}
                            title="Remove from cart"
                          >✕</button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <div className="checkout-summary">
                <div className="summary-row">
                  <span>Order Total</span>
                  <strong>${totalPrice.toFixed(2)}</strong>
                </div>
                <div className="summary-row">
                  <span>Tax</span>
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
