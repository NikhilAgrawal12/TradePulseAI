import axios from "axios";
import type { AddCartItemRequest, CartItem, CompleteOrderResponse, UpdateCartItemRequest } from "../types/cart";
import { getEmailFromToken, getStoredToken, getUserIdFromToken } from "./auth";
import { toMoney } from "./money";

function buildAuthHeaders() {
  const token = getStoredToken();
  const userId = getUserIdFromToken(token);
  const userEmail = getEmailFromToken(token);

  if (!token || !userId || !userEmail) {
    throw new Error("Missing valid authentication token.");
  }

  return {
    Authorization: `Bearer ${token}`,
    "X-User-Id": userId,
    "X-User-Email": userEmail,
  };
}

function normalizeCartItems(items: CartItem[]): CartItem[] {
  return items.map((item) => ({
    ...item,
    price: toMoney(item.price),
    lineTotal: item.lineTotal == null ? undefined : toMoney(item.lineTotal),
  }));
}

export async function fetchCartItems(): Promise<CartItem[]> {
  const response = await axios.get<CartItem[]>("/api/cart", {
    headers: buildAuthHeaders(),
  });
  return normalizeCartItems(response.data);
}

export async function addCartItem(payload: AddCartItemRequest): Promise<CartItem[]> {
  const response = await axios.post<CartItem[]>("/api/cart/items", payload, {
    headers: buildAuthHeaders(),
  });
  return normalizeCartItems(response.data);
}

export async function updateCartItemQuantity(stockId: string, payload: UpdateCartItemRequest): Promise<CartItem[]> {
  const response = await axios.put<CartItem[]>(`/api/cart/items/${stockId}`, payload, {
    headers: buildAuthHeaders(),
  });
  return normalizeCartItems(response.data);
}

export async function removeCartItem(stockId: string): Promise<CartItem[]> {
  const response = await axios.delete<CartItem[]>(`/api/cart/items/${stockId}`, {
    headers: buildAuthHeaders(),
  });
  return normalizeCartItems(response.data);
}

export async function clearCartItems(): Promise<CartItem[]> {
  const response = await axios.delete<CartItem[]>("/api/cart", {
    headers: buildAuthHeaders(),
  });
  return normalizeCartItems(response.data);
}

export async function completeOrder(payload: { items: CartItem[]; subtotal: number; total: number }): Promise<CompleteOrderResponse> {
  try {
    const normalizedPayload = {
      ...payload,
      items: normalizeCartItems(payload.items),
      subtotal: toMoney(payload.subtotal),
      total: toMoney(payload.total),
    };
    const response = await axios.post<CompleteOrderResponse>(
      "/api/cart/complete-order",
      normalizedPayload,
      {
        headers: buildAuthHeaders(),
      }
    );
    return response.data;
  } catch (error) {
    if (axios.isAxiosError(error)) {
      const message =
        error.response?.data?.message ||
        error.message ||
        "Failed to complete order";
      console.error("Complete order error:", {
        status: error.response?.status,
        message,
        data: error.response?.data,
      });
      throw new Error(message);
    }
    throw error;
  }
}
