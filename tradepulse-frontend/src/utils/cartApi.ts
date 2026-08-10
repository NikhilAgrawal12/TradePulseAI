import axios from "axios";
import type { AddCartItemRequest, CartItem, CompleteOrderResponse, LockQuoteRequest, LockQuoteResponse, UpdateCartItemRequest } from "../types/cart";
import { buildAuthHeaders } from "./auth";
import { toMoney } from "./money";

const COMPLETE_ORDER_TIMEOUT_MS = 30_000;

function normalizeCartItems(items: CartItem[]): CartItem[] {
  return items.map((item) => ({
    ...item,
    price: toMoney(item.price),
    lineTotal: item.lineTotal == null ? undefined : toMoney(item.lineTotal),
  }));
}

export async function fetchCartItems(): Promise<CartItem[]> {
  const response = await axios.get<CartItem[]>("/api/cart", {
    headers: buildAuthHeaders({ includeEmail: true }),
  });
  return normalizeCartItems(response.data);
}

export async function addCartItem(payload: AddCartItemRequest): Promise<CartItem[]> {
  const response = await axios.post<CartItem[]>("/api/cart/items", payload, {
    headers: buildAuthHeaders({ includeEmail: true }),
  });
  return normalizeCartItems(response.data);
}

export async function updateCartItemQuantity(stockId: string, payload: UpdateCartItemRequest): Promise<CartItem[]> {
  const response = await axios.put<CartItem[]>(`/api/cart/items/${stockId}`, payload, {
    headers: buildAuthHeaders({ includeEmail: true }),
  });
  return normalizeCartItems(response.data);
}

export async function removeCartItem(stockId: string): Promise<CartItem[]> {
  const response = await axios.delete<CartItem[]>(`/api/cart/items/${stockId}`, {
    headers: buildAuthHeaders({ includeEmail: true }),
  });
  return normalizeCartItems(response.data);
}

export async function clearCartItems(): Promise<CartItem[]> {
  const response = await axios.delete<CartItem[]>("/api/cart", {
    headers: buildAuthHeaders({ includeEmail: true }),
  });
  return normalizeCartItems(response.data);
}

export async function completeOrder(payload: { items: CartItem[]; total: number }): Promise<CompleteOrderResponse> {
  try {
    const normalizedPayload = {
      ...payload,
      items: normalizeCartItems(payload.items),
      total: toMoney(payload.total),
    };
    const response = await axios.post<CompleteOrderResponse>(
      "/api/cart/complete-order",
      normalizedPayload,
      {
        headers: buildAuthHeaders({ includeEmail: true }),
        timeout: COMPLETE_ORDER_TIMEOUT_MS,
      }
    );
    return response.data;
  } catch (error) {
    if (axios.isAxiosError(error)) {
      if (error.code === "ECONNABORTED") {
        throw new Error("Completing your order is taking longer than expected. Please try again.");
      }
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

export async function lockOrderQuote(payload: LockQuoteRequest): Promise<LockQuoteResponse> {
  const normalizedPayload = {
    ...payload,
    items: normalizeCartItems(payload.items),
    total: toMoney(payload.total),
  };

  const response = await axios.post<LockQuoteResponse>(
    "/api/cart/lock-quote",
    normalizedPayload,
    {
      headers: buildAuthHeaders({ includeEmail: true }),
    }
  );

  return {
    ...response.data,
    items: normalizeCartItems(response.data.items ?? []),
    total: toMoney(response.data.total),
    lockSeconds: Number.isFinite(response.data.lockSeconds) ? response.data.lockSeconds : 15,
  };
}

