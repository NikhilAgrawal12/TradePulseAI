import axios from "axios";
import type { OrderHistoryEntry } from "../types/order";
import { getEmailFromToken, getStoredToken } from "./auth";

function buildAuthHeaders() {
  const token = getStoredToken();
  const email = getEmailFromToken(token);

  if (!token || !email) {
    throw new Error("Missing valid authentication token.");
  }

  return {
    Authorization: `Bearer ${token}`,
    "X-User-Email": email,
  };
}

export async function fetchOrderHistory(): Promise<OrderHistoryEntry[]> {
  const response = await axios.get<OrderHistoryEntry[]>("/api/orders", {
    headers: buildAuthHeaders(),
  });
  return response.data;
}

