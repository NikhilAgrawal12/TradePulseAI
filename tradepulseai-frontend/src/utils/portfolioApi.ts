import axios from "axios";
import type { PortfolioResponse, SellPortfolioItemRequest } from "../types/portfolio";
import { buildAuthHeaders } from "./auth";

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

