import axios from "axios";
import type { AddCartItemRequest, CartItem, CompleteOrderResponse, UpdateCartItemRequest } from "../types/cart";
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

export async function fetchCartItems(): Promise<CartItem[]> {
  const response = await axios.get<CartItem[]>("/api/cart", {
    headers: buildAuthHeaders(),
  });
  return response.data;
}

export async function addCartItem(payload: AddCartItemRequest): Promise<CartItem[]> {
  const response = await axios.post<CartItem[]>("/api/cart/items", payload, {
    headers: buildAuthHeaders(),
  });
  return response.data;
}

export async function updateCartItemQuantity(stockId: string, payload: UpdateCartItemRequest): Promise<CartItem[]> {
  const response = await axios.put<CartItem[]>(`/api/cart/items/${stockId}`, payload, {
    headers: buildAuthHeaders(),
  });
  return response.data;
}

export async function removeCartItem(stockId: string): Promise<CartItem[]> {
  const response = await axios.delete<CartItem[]>(`/api/cart/items/${stockId}`, {
    headers: buildAuthHeaders(),
  });
  return response.data;
}

export async function clearCartItems(): Promise<CartItem[]> {
  const response = await axios.delete<CartItem[]>("/api/cart", {
    headers: buildAuthHeaders(),
  });
  return response.data;
}

export async function completeOrder(): Promise<CompleteOrderResponse> {
  const token = getStoredToken();
  const email = getEmailFromToken(token);

  if (!token || !email) {
    throw new Error("Missing valid authentication token. Please log in again.");
  }

  try {
    const response = await axios.post<CompleteOrderResponse>(
      "/api/cart/complete-order",
      null,
      {
        headers: {
          Authorization: `Bearer ${token}`,
          "X-User-Email": email,
        },
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
