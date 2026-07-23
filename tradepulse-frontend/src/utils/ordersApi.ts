import axios from "axios";
import type { OrderHistoryEntry } from "../types/order";
import { buildAuthHeaders } from "./auth";
import { toMoney } from "./money";

export type PaginatedResponse<T> = {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};

function normalizeOrders(orders: OrderHistoryEntry[]): OrderHistoryEntry[] {
  return orders.map((order) => ({
    ...order,
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

export async function fetchOrderHistoryPage(page: number, size: number): Promise<PaginatedResponse<OrderHistoryEntry>> {
  const response = await axios.get<PaginatedResponse<OrderHistoryEntry>>("/api/orders/paged", {
    headers: buildAuthHeaders(),
    params: { page, size },
  });

  return {
    ...response.data,
    content: normalizeOrders(response.data.content ?? []),
  };
}

