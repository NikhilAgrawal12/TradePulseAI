import { useEffect } from "react";
import { useNavigate } from "react-router";
import { Header } from "../../components/Header.tsx";
import { useOrders } from "../../context/OrdersContext";
import { isUserAuthenticated } from "../../utils/auth";
import "./OrdersPage.css";

export function OrdersPage() {
  const navigate = useNavigate();

  useEffect(() => {
    document.title = "Orders | TradePulseAI";
  }, []);

  useEffect(() => {
    if (!isUserAuthenticated()) {
      navigate("/login");
    }
  }, [navigate]);

  const { orders } = useOrders();

  return (
    <>
      <Header />
      <main className="orders-page">
        <div className="orders-container">
          <h1>Your Orders</h1>

          {orders.length === 0 ? (
            <section className="orders-empty">
              <p>No orders yet. Complete a payment to see your orders here.</p>
            </section>
          ) : (
            <section className="orders-list">
              {orders.map((order) => (
                <article key={order.id} className="order-card">
                  <header className="order-card-header">
                    <div>
                      <h2>Order #{order.id}</h2>
                      <p>{new Date(order.createdAtIso).toLocaleString()}</p>
                    </div>
                    <span className="order-total">${order.total.toFixed(2)}</span>
                  </header>

                  <div className="order-items">
                    {order.items.map((item) => (
                      <p key={`${order.id}-${item.stockId}`}>
                        <span>{item.symbol} x {item.quantity}</span>
                        <strong>${(item.price * item.quantity).toFixed(2)}</strong>
                      </p>
                    ))}
                  </div>

                  <footer className="order-summary">
                    <p><span>Order Total</span><strong>${order.subtotal.toFixed(2)}</strong></p>
                    <p><span>Tax</span><strong>${order.tax.toFixed(2)}</strong></p>
                    <p className="grand-total"><span>Total</span><strong>${order.total.toFixed(2)}</strong></p>
                  </footer>
                </article>
              ))}
            </section>
          )}
        </div>
      </main>
    </>
  );
}
