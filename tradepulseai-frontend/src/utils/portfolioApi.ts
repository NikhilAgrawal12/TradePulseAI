import axios from "axios";
import type { PortfolioResponse, SellPortfolioItemRequest } from "../types/portfolio";
import { buildAuthHeaders } from "./auth";
import { toMoney } from "./money";

function unwrapApiError(error: unknown, fallback: string): Error {
  if (axios.isAxiosError(error)) {
    const message = error.response?.data?.message;
    if (typeof message === "string" && message.trim().length > 0) {
      return new Error(message);
    }
  }
  return new Error(fallback);
}

function normalizePortfolio(data: PortfolioResponse): PortfolioResponse {
  return {
    ...data,
    summary: {
      ...data.summary,
      totalInvestedValue: toMoney(data.summary.totalInvestedValue),
      totalMarketValue: toMoney(data.summary.totalMarketValue),
      totalUnrealizedPnl: toMoney(data.summary.totalUnrealizedPnl),
      totalUnrealizedPnlPercent: toMoney(data.summary.totalUnrealizedPnlPercent),
      totalRealizedPnl: toMoney(data.summary.totalRealizedPnl),
    },
    holdings: data.holdings.map((holding) => ({
      ...holding,
      averageBuyPrice: toMoney(holding.averageBuyPrice),
      currentPrice: toMoney(holding.currentPrice),
      investedValue: toMoney(holding.investedValue),
      marketValue: toMoney(holding.marketValue),
      unrealizedPnl: toMoney(holding.unrealizedPnl),
      unrealizedPnlPercent: toMoney(holding.unrealizedPnlPercent),
      realizedPnl: toMoney(holding.realizedPnl),
    })),
    transactions: data.transactions.map((transaction) => ({
      ...transaction,
      price: toMoney(transaction.price),
      grossAmount: toMoney(transaction.grossAmount),
      realizedPnl: toMoney(transaction.realizedPnl),
    })),
  };
}

export async function fetchPortfolio(): Promise<PortfolioResponse> {
  try {
    const response = await axios.get<PortfolioResponse>("/api/portfolio", {
      headers: buildAuthHeaders(),
    });
    return normalizePortfolio(response.data);
  } catch (error) {
    throw unwrapApiError(error, "Failed to load portfolio.");
  }
}

export async function sellPortfolioItem(stockId: string, payload: SellPortfolioItemRequest): Promise<PortfolioResponse> {
  try {
    const response = await axios.post<PortfolioResponse>(`/api/portfolio/sell/${stockId}`, {
      ...payload,
      price: toMoney(payload.price),
    }, {
      headers: buildAuthHeaders(),
    });
    return normalizePortfolio(response.data);
  } catch (error) {
    throw unwrapApiError(error, "Failed to sell stock.");
  }
}
