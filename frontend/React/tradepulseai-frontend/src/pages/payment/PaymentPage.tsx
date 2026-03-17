import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router";
import { Header } from "../../components/Header.tsx";
import { useCart, type CartItem } from "../../context/CartContext";
import { useOrders } from "../../context/OrdersContext";
import "./PaymentPage.css";

export function PaymentPage() {
  useEffect(() => {
    document.title = "Payment | TradePulseAI";
  }, []);

  const location = useLocation();
  const navigate = useNavigate();
  const { cart, clearCart } = useCart();
  const { addOrder } = useOrders();
  const [showSuccess, setShowSuccess] = useState(false);
  const [paidTotal, setPaidTotal] = useState(0);

  const state = location.state as {
    subtotal?: number;
    tax?: number;
    total?: number;
    items?: CartItem[];
  } | null;

  const items = state?.items?.length ? state.items : cart;
  const subtotal = useMemo(
    () => state?.subtotal ?? items.reduce((sum, item) => sum + item.price * item.quantity, 0),
    [items, state?.subtotal],
  );
  const tax = useMemo(() => state?.tax ?? subtotal * 0.08, [state?.tax, subtotal]);
  const total = useMemo(() => state?.total ?? subtotal + tax, [state?.total, subtotal, tax]);

  const handlePayNow = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    addOrder({
      items,
      subtotal,
      tax,
      total,
    });

    setPaidTotal(total);
    clearCart();
    setShowSuccess(true);
  };

  if (items.length === 0 && !showSuccess) {
    return (
      <>
        <Header />
        <main className="payment-page">
          <div className="payment-container payment-empty">
            <h1>No order to pay</h1>
            <p>Your cart is empty. Add stocks in your cart to continue.</p>
            <Link to="/checkout" className="payment-link-btn">Go to Order Cart</Link>
          </div>
        </main>
      </>
    );
  }

  return (
    <>
      <Header />
      <main className="payment-page">
        <div className="payment-container">
          <>
            <section className="payment-form-card">
              <h1>Complete Payment</h1>
              <form onSubmit={handlePayNow} className="payment-form">
                <label htmlFor="card-name">Cardholder Name</label>
                <input id="card-name" type="text" placeholder="Nikhil Agrawal" required />

                <label htmlFor="card-number">Card Number</label>
                <input id="card-number" type="text" inputMode="numeric" maxLength={19} placeholder="1234 5678 9012 3456" required />

                <div className="payment-row">
                  <div>
                    <label htmlFor="expiry">Expiry</label>
                    <input id="expiry" type="text" placeholder="MM/YY" maxLength={5} required />
                  </div>
                  <div>
                    <label htmlFor="cvv">CVV</label>
                    <input id="cvv" type="password" inputMode="numeric" maxLength={4} placeholder="123" required />
                  </div>
                </div>

                <button type="submit" className="payment-primary-btn">Pay ${total.toFixed(2)}</button>
              </form>
            </section>

            <aside className="payment-summary-card">
              <h2>Order Summary</h2>
              <div className="payment-items">
                {items.map((item) => (
                  <p key={item.stockId}>
                    <span>{item.symbol} x {item.quantity}</span>
                    <strong>${(item.price * item.quantity).toFixed(2)}</strong>
                  </p>
                ))}
              </div>
              <div className="payment-summary-row"><span>Order Total</span><strong>${subtotal.toFixed(2)}</strong></div>
              <div className="payment-summary-row"><span>Tax</span><strong>${tax.toFixed(2)}</strong></div>
              <div className="payment-summary-row total"><span>Total</span><strong>${total.toFixed(2)}</strong></div>
            </aside>
          </>
        </div>

        {showSuccess && (
          <div className="payment-success-overlay" role="dialog" aria-modal="true" aria-labelledby="payment-success-title">
            <div className="payment-success-card">
              <h2 id="payment-success-title">Payment successful</h2>
              <p>Your order of <strong>${paidTotal.toFixed(2)}</strong> has been placed.</p>
              <button type="button" className="payment-primary-btn" onClick={() => navigate("/orders")}>OK</button>
            </div>
          </div>
        )}
      </main>
    </>
  );
}
