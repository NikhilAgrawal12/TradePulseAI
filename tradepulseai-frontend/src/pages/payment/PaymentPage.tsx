import { useEffect, useMemo, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router";
import { Header } from "../../components/Header.tsx";
import { useCart } from "../../context/CartContext";
import { useOrders } from "../../context/OrdersContext";
import { useWallet } from "../../context/WalletContext";
import type { CartItem } from "../../types/cart";
import { completeOrder } from "../../utils/cartApi";
import { fetchOrderHistory } from "../../utils/ordersApi";
import "./PaymentPage.css";

const PRICE_LOCK_SECONDS = 15;

export function PaymentPage() {
  useEffect(() => {
    document.title = "Payment | TradePulseAI";
  }, []);

  const location = useLocation();
  const navigate = useNavigate();
  const { cart, clearCart } = useCart();
  const { refreshOrders } = useOrders();
  const { balance, refreshWallet, isLoading: isWalletLoading } = useWallet();

  const [showSuccess, setShowSuccess] = useState(false);
  const [paidTotal, setPaidTotal] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [processing, setProcessing] = useState(false);
  const [secondsLeft, setSecondsLeft] = useState(PRICE_LOCK_SECONDS);

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

  const hasSufficientBalance = !isWalletLoading && balance >= total;

  useEffect(() => {
    if (showSuccess || processing) return;

    if (secondsLeft <= 0) {
      navigate("/checkout", {
        replace: true,
        state: { paymentCancelled: true, reason: "Price lock expired. Please review your cart and try again." },
      });
      return;
    }

    const timerId = window.setInterval(() => {
      setSecondsLeft((current) => {
        if (current <= 1) {
          window.clearInterval(timerId);
          return 0;
        }
        return current - 1;
      });
    }, 1000);

    return () => {
      window.clearInterval(timerId);
    };
  }, [navigate, processing, secondsLeft, showSuccess]);

  const handlePayWithWallet = async () => {
    setError(null);
    setProcessing(true);

    try {
      const response = await completeOrder();
      if (!response.status || response.status.toUpperCase() !== "COMPLETED") {
        setError("Payment was not completed. Please try again.");
        return;
      }

      await clearCart();
      await refreshOrders();
      await refreshWallet();

      const orders = await fetchOrderHistory();
      const completedOrder = response.orderId
        ? orders.find((order) => order.id === response.orderId)
        : orders[0];

      setPaidTotal(completedOrder?.total ?? total);
      setShowSuccess(true);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : "Unable to complete payment right now. Please try again.";
      console.error("Payment error:", errorMessage);
      setError(errorMessage);
    } finally {
      setProcessing(false);
    }
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
          <section className="payment-form-card">
            <h1>Pay with Wallet</h1>
            <p className="payment-price-lock" role="status" aria-live="polite">
              Price lock expires in <strong>{secondsLeft}s</strong>
            </p>

            <div className="wallet-pay-balance">
              <span className="wallet-pay-balance-label">Wallet Balance</span>
              <span className="wallet-pay-balance-amount">{isWalletLoading ? "Loading..." : `$${balance.toFixed(2)}`}</span>
            </div>

            {!showSuccess && (
              isWalletLoading ? (
                <p className="wallet-pay-sufficient-text">Checking wallet balance...</p>
              ) : hasSufficientBalance ? (
                <div className="wallet-pay-sufficient">
                  <p className="wallet-pay-sufficient-text">Sufficient balance to complete this purchase</p>
                  <button
                    type="button"
                    className="payment-primary-btn wallet-pay-btn"
                    disabled={processing || secondsLeft <= 0}
                    onClick={handlePayWithWallet}
                  >
                    {processing ? "Processing..." : `Pay $${total.toFixed(2)} from Wallet`}
                  </button>
                </div>
              ) : (
                <div className="wallet-pay-insufficient">
                  <p className="wallet-pay-insufficient-text">
                    Insufficient balance. You need <strong>${(total - balance).toFixed(2)}</strong> more.
                  </p>
                  <Link to="/wallet" className="payment-link-btn wallet-topup-btn">
                    Add Funds to Wallet
                  </Link>
                </div>
              )
            )}

            {!showSuccess && error && <p className="payment-error-msg">{error}</p>}
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
        </div>

        {showSuccess && (
          <div className="payment-success-overlay" role="dialog" aria-modal="true" aria-labelledby="payment-success-title">
            <div className="payment-success-card">
              <h2 id="payment-success-title">Payment Successful! 🎉</h2>
              <p>Your order of <strong>${paidTotal.toFixed(2)}</strong> has been placed.</p>
              <p className="payment-success-wallet">Wallet balance updated.</p>
              <button type="button" className="payment-primary-btn" onClick={() => navigate("/orders")}>View Orders</button>
            </div>
          </div>
        )}
      </main>
    </>
  );
}
