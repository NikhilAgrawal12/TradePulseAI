import axios from "axios";
import type { AddWatchlistItemRequest, UpdateWatchlistItemRequest, WatchlistEntry } from "../types/watchlist";
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

export async function fetchWatchlistItems(): Promise<WatchlistEntry[]> {
  const response = await axios.get<WatchlistEntry[]>("/api/watchlist", {
    headers: buildAuthHeaders(),
  });
  return response.data;
}

export async function addWatchlistItem(payload: AddWatchlistItemRequest): Promise<WatchlistEntry[]> {
  const response = await axios.post<WatchlistEntry[]>("/api/watchlist/items", payload, {
    headers: buildAuthHeaders(),
  });
  return response.data;
}

export async function updateWatchlistItem(stockId: string, payload: UpdateWatchlistItemRequest): Promise<WatchlistEntry[]> {
  const response = await axios.put<WatchlistEntry[]>(`/api/watchlist/items/${stockId}`, payload, {
    headers: buildAuthHeaders(),
  });
  return response.data;
}

export async function removeWatchlistItem(stockId: string): Promise<WatchlistEntry[]> {
  const response = await axios.delete<WatchlistEntry[]>(`/api/watchlist/items/${stockId}`, {
    headers: buildAuthHeaders(),
  });
  return response.data;
}

export async function clearWatchlistItems(): Promise<WatchlistEntry[]> {
  const response = await axios.delete<WatchlistEntry[]>("/api/watchlist", {
    headers: buildAuthHeaders(),
  });
  return response.data;
}
