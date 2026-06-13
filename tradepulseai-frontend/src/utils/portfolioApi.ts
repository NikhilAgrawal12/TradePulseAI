import axios from "axios";
import type { PortfolioResponse, SellPortfolioItemRequest } from "../types/portfolio";
import { buildAuthHeaders } from "./auth";

function unwrapApiError(error: unknown, fallback: string): Error {
  if (axios.isAxiosError(error)) {
    const message = error.response?.data?.message;
    if (typeof message === "string" && message.trim().length > 0) {
      return new Error(message);
    }
  }
  return new Error(fallback);
}

export async function fetchPortfolio(): Promise<PortfolioResponse> {
  try {
    const response = await axios.get<PortfolioResponse>("/api/customers/portfolio", {
      headers: buildAuthHeaders(),
    });
    return response.data;
  } catch (error) {
    throw unwrapApiError(error, "Failed to load portfolio.");
  }
}

export async function sellPortfolioItem(stockId: string, payload: SellPortfolioItemRequest): Promise<PortfolioResponse> {
  try {
    const response = await axios.post<PortfolioResponse>(`/api/customers/portfolio/sell/${stockId}`, payload, {
      headers: buildAuthHeaders(),
    });
    return response.data;
  } catch (error) {
    throw unwrapApiError(error, "Failed to sell stock.");
  }
}

