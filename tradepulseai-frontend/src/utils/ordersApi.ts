import axios from "axios";
import type { OrderHistoryEntry } from "../types/order";
import { buildAuthHeaders } from "./auth";
import { toMoney } from "./money";

function normalizeOrders(orders: OrderHistoryEntry[]): OrderHistoryEntry[] {
  return orders.map((order) => ({
    ...order,
    subtotal: toMoney(order.subtotal),
    total: toMoney(order.total),
    items: order.items.map((item) => ({
      ...item,
      price: toMoney(item.price),
      lineTotal: toMoney(item.lineTotal),
    })),
  }));
}

export async function fetchOrderHistory(): Promise<OrderHistoryEntry[]> {
  const response = await axios.get<OrderHistoryEntry[]>("/api/orders", {
    headers: buildAuthHeaders(),
  });
  return normalizeOrders(response.data);
}

