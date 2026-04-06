import axios from "axios";
import type { AddCartItemRequest, CartItem, UpdateCartItemRequest } from "../types/cart";
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

