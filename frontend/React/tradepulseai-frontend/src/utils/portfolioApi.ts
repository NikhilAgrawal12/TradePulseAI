import axios from "axios";
import type { PortfolioResponse, SellPortfolioItemRequest } from "../types/portfolio";
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

export async function fetchPortfolio(): Promise<PortfolioResponse> {
  const response = await axios.get<PortfolioResponse>("/api/customers/portfolio", {
    headers: buildAuthHeaders(),
  });
  return response.data;
}

export async function sellPortfolioItem(stockId: string, payload: SellPortfolioItemRequest): Promise<PortfolioResponse> {
  const response = await axios.post<PortfolioResponse>(`/api/customers/portfolio/sell/${stockId}`, payload, {
    headers: buildAuthHeaders(),
  });
  return response.data;
}

