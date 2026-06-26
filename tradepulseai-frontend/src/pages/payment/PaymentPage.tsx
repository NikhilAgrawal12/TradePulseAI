import { useEffect, useMemo, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router";
import { Header } from "../../components/Header.tsx";
import { useCart } from "../../context/CartContext";
import { useOrders } from "../../context/OrdersContext";
import { useWallet } from "../../context/WalletContext";
import type { CartItem } from "../../types/cart";
import { completeOrder, lockOrderQuote } from "../../utils/cartApi";
import { formatMoney, roundMoney, toMoney } from "../../utils/money";
import "./PaymentPage.css";

const PRICE_LOCK_SECONDS = 15;

type LockedQuoteState = {
  items: CartItem[];
  subtotal: number;
  total: number;
  lockSeconds: number;
};

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
  const [receipt, setReceipt] = useState<{
    orderNumber?: number;
    paidAtIso: string;
    total: number;
    itemCount: number;
  } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [processing, setProcessing] = useState(false);
  const [secondsLeft, setSecondsLeft] = useState(PRICE_LOCK_SECONDS);
  const [quoteLoading, setQuoteLoading] = useState(true);
  const [quoteError, setQuoteError] = useState<string | null>(null);

  const state = location.state as {
    subtotal?: number;
    total?: number;
    items?: CartItem[];
  } | null;

  const items = state?.items?.length ? state.items : cart;
  const [lockedQuote, setLockedQuote] = useState<LockedQuoteState | null>(null);

  useEffect(() => {
    if (lockedQuote || items.length === 0) {
      return;
    }

    let cancelled = false;

    const loadLockedQuote = async () => {
      try {
        setQuoteLoading(true);
        setQuoteError(null);
        const fallbackTotal = roundMoney(items.reduce((sum, item) => sum + item.price * item.quantity, 0));
        const response = await lockOrderQuote({
          items,
          subtotal: fallbackTotal,
          total: fallbackTotal,
        });
        if (cancelled) {
          return;
        }
        const normalizedLockSeconds = response.lockSeconds > 0 ? response.lockSeconds : PRICE_LOCK_SECONDS;
        setLockedQuote({
          items: response.items,
          subtotal: roundMoney(response.subtotal),
          total: roundMoney(response.total),
          lockSeconds: normalizedLockSeconds,
        });
        setSecondsLeft(normalizedLockSeconds);
      } catch (lockError) {
        if (cancelled) {
          return;
        }
        const message = lockError instanceof Error
          ? lockError.message
          : "Unable to lock fresh stock prices right now. Please try again.";
        setQuoteError(message);
      } finally {
        if (!cancelled) {
          setQuoteLoading(false);
        }
      }
    };

    void loadLockedQuote();

    return () => {
      cancelled = true;
    };
  }, [items, lockedQuote]);

  const displayItems = lockedQuote?.items ?? [];

  const subtotal = useMemo(
    () => lockedQuote?.subtotal ?? roundMoney(displayItems.reduce((sum, item) => sum + item.price * item.quantity, 0)),
    [displayItems, lockedQuote],
  );
  const total = useMemo(() => lockedQuote?.total ?? roundMoney(subtotal), [lockedQuote, subtotal]);
  const priceUpdated = useMemo(() => {
    if (!lockedQuote || items.length === 0) {
      return false;
    }

    const sourceByStockId = new Map(items.map((item) => [String(item.stockId), roundMoney(item.price)]));
    return lockedQuote.items.some((item) => {
      const sourcePrice = sourceByStockId.get(String(item.stockId));
      if (sourcePrice === undefined) {
        return false;
      }
      return Math.abs(sourcePrice - roundMoney(item.price)) >= 0.01;
    });
  }, [items, lockedQuote]);

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
      const response = await completeOrder({
        items: displayItems,
        subtotal,
        total,
      });
      if (!response.status || response.status.toUpperCase() !== "COMPLETED") {
        setError("Payment was not completed. Please try again.");
        return;
      }

      await clearCart();
      const latestOrders = await refreshOrders();
      await refreshWallet();
      const placedOrder = latestOrders.find((order) => order.id === response.orderId) ?? latestOrders[0];
      setReceipt({
        orderNumber: placedOrder?.orderNumber,
        paidAtIso: placedOrder?.createdAtIso ?? new Date().toISOString(),
        total,
        itemCount: displayItems.length,
      });
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

  if (!lockedQuote && quoteLoading) {
    return (
      <>
        <Header />
        <main className="payment-page">
          <div className="payment-container payment-empty">
            <h1>Locking latest prices...</h1>
            <p>Please wait while we fetch and freeze the current quote values for this payment.</p>
          </div>
        </main>
      </>
    );
  }

  if (!lockedQuote && quoteError) {
    return (
      <>
        <Header />
        <main className="payment-page">
          <div className="payment-container payment-empty">
            <h1>Unable to lock prices</h1>
            <p>{quoteError}</p>
            <Link to="/checkout" className="payment-link-btn">Back to Order Cart</Link>
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
              <span className="wallet-pay-balance-amount">{isWalletLoading ? "Loading..." : `$${formatMoney(balance)}`}</span>
            </div>

            {!showSuccess && !processing && (
                isWalletLoading ? (
                <p className="wallet-pay-sufficient-text">Checking wallet balance...</p>
              ) : hasSufficientBalance ? (
                <div className="wallet-pay-sufficient">
                  <button
                    type="button"
                    className="payment-primary-btn wallet-pay-btn"
                    disabled={processing || secondsLeft <= 0}
                    onClick={handlePayWithWallet}
                  >
                    {processing ? "Processing..." : `Pay $${formatMoney(total)} from Wallet`}
                  </button>
                </div>
              ) : (
                <div className="wallet-pay-insufficient">
                  <p className="wallet-pay-insufficient-text">
                    Insufficient balance. You need <strong>${formatMoney(toMoney(total - balance))}</strong> more.
                  </p>
                  <Link to="/wallet" className="payment-link-btn wallet-topup-btn">
                    Add Funds to Wallet
                  </Link>
                </div>
              )
            )}

            {!showSuccess && !processing && error && <p className="payment-error-msg">{error}</p>}
          </section>

          <aside className="payment-summary-card">
            <h2>Order Summary</h2>
            <div className="payment-items">
              {displayItems.map((item) => (
                <p key={item.stockId}>
                  <span>{item.symbol} x {item.quantity}</span>
                  <strong>${formatMoney(roundMoney(item.lineTotal ?? (item.price * item.quantity)))}</strong>
                </p>
              ))}
            </div>
            <div className="payment-summary-row total"><span>Total</span><strong>${formatMoney(total)}</strong></div>
            {priceUpdated && (
              <p className="payment-price-refresh-msg" role="status" aria-live="polite">
                Prices were refreshed using the latest stock quote values.
              </p>
            )}
          </aside>
        </div>

        {showSuccess && (
          <div className="payment-success-overlay" role="dialog" aria-modal="true" aria-labelledby="payment-success-title">
            <div className="payment-success-card">
              <h2 id="payment-success-title">Payment Successful! 🎉</h2>
              <p>Your wallet payment is complete and your order was placed.</p>
              <div className="payment-success-details">
                <p><span>Amount Paid</span><strong>${formatMoney(receipt?.total ?? total)}</strong></p>
                <p><span>Items</span><strong>{receipt?.itemCount ?? displayItems.length}</strong></p>
                <p><span>Date</span><strong>{new Date(receipt?.paidAtIso ?? new Date().toISOString()).toLocaleString()}</strong></p>
                <p><span>Order #</span><strong>{receipt?.orderNumber ?? "Pending"}</strong></p>
              </div>
              <button type="button" className="payment-primary-btn" onClick={() => navigate("/orders")}>View Orders</button>
            </div>
          </div>
        )}
      </main>
    </>
  );
}
