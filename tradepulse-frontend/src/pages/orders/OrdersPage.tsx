import { useEffect, useState } from "react";
import { useNavigate } from "react-router";
import { Header } from "../../components/Header.tsx";
import type { OrderHistoryEntry } from "../../types/order";
import { isUserAuthenticated } from "../../utils/auth";
import { formatMoney } from "../../utils/money";
import { fetchOrderHistoryPage } from "../../utils/ordersApi";
import "./OrdersPage.css";

const ORDERS_PAGE_SIZE = 10;

export function OrdersPage() {
  const navigate = useNavigate();

  useEffect(() => {
    document.title = "Orders | TradePulse";
  }, []);

  useEffect(() => {
    if (!isUserAuthenticated()) {
      navigate("/login");
    }
  }, [navigate]);

  const [orders, setOrders] = useState<OrderHistoryEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  useEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: "smooth" });
  }, [page]);

  useEffect(() => {
    if (!isUserAuthenticated()) {
      return;
    }

    let cancelled = false;
    const loadOrders = async () => {
      try {
        setLoading(true);
        setError(null);
        const response = await fetchOrderHistoryPage(page, ORDERS_PAGE_SIZE);
        if (cancelled) {
          return;
        }
        setOrders(response.content);
        setTotalPages(response.totalPages);
        setTotalElements(response.totalElements);
      } catch (loadError) {
        if (cancelled) {
          return;
        }
        const message = loadError instanceof Error ? loadError.message : "Failed to fetch order history.";
        setError(message);
        setOrders([]);
        setTotalPages(0);
        setTotalElements(0);
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    void loadOrders();
    return () => {
      cancelled = true;
    };
  }, [page]);

  return (
    <>
      <Header />
      <main className="orders-page">
        <div className="orders-container">
          <h1>Your Orders</h1>

          {loading && <p>Loading order history...</p>}
          {error && <p style={{ color: "#b91c1c" }}>{error}</p>}

          {!loading && !error && orders.length === 0 ? (
            <section className="orders-empty">
              <p>No orders yet. Complete a payment to see your orders here.</p>
            </section>
          ) : (
            !loading && !error && (
              <section className="orders-list">
                {orders.map((order) => (
                  <article key={order.id} className="order-card">
                    <header className="order-card-header">
                      <div>
                        <h2>Order #{order.orderNumber}</h2>
                        <p>{new Date(order.createdAtIso).toLocaleString()}</p>
                      </div>
                      <span className="order-total">${formatMoney(order.total)}</span>
                    </header>

                    <div className="order-items">
                      {order.items.map((item) => (
                        <p key={`${order.id}-${item.stockId}`}>
                          <span>{item.symbol} x {item.quantity}</span>
                          <strong>${formatMoney(item.lineTotal)}</strong>
                        </p>
                      ))}
                    </div>

                    <footer className="order-summary">
                      <p className="grand-total"><span>Total</span><strong>${formatMoney(order.total)}</strong></p>
                    </footer>
                  </article>
                ))}
              </section>
            )
          )}

          {!loading && !error && totalPages > 0 && (
            <div className="orders-pagination pagination-controls">
              <button className="pagination-button" type="button" onClick={() => setPage((current) => Math.max(current - 1, 0))} disabled={page === 0}>
                Previous
              </button>
              <span className="pagination-label">
                Page {page + 1} of {Math.max(totalPages, 1)} ({totalElements} orders)
              </span>
              <button
                className="pagination-button"
                type="button"
                onClick={() => setPage((current) => (current + 1 < totalPages ? current + 1 : current))}
                disabled={page + 1 >= totalPages}
              >
                Next
              </button>
            </div>
          )}
        </div>
      </main>
    </>
  );
}
