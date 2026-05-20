import axios from "axios";
import type { OrderHistoryEntry } from "../types/order";
import { buildAuthHeaders } from "./auth";

export async function fetchOrderHistory(): Promise<OrderHistoryEntry[]> {
  const response = await axios.get<OrderHistoryEntry[]>("/api/orders", {
    headers: buildAuthHeaders(),
  });
  return response.data;
}

