import axios from "axios";
import type { OrderHistoryEntry } from "../types/order";
import { getStoredToken, getUserIdFromToken } from "./auth";

function buildAuthHeaders() {
  const token = getStoredToken();
  const userId = getUserIdFromToken(token);

  if (!token || !userId) {
    throw new Error("Missing valid authentication token.");
  }

  return {
    Authorization: `Bearer ${token}`,
    "X-User-Id": userId,
  };
}

export async function fetchOrderHistory(): Promise<OrderHistoryEntry[]> {
  const response = await axios.get<OrderHistoryEntry[]>("/api/orders", {
    headers: buildAuthHeaders(),
  });
  return response.data;
}

