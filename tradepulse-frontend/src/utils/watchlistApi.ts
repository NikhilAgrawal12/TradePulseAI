import axios from "axios";
import type { AddWatchlistItemRequest, WatchlistEntry } from "../types/watchlist";
import { buildAuthHeaders } from "./auth";

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
