import axios from "axios";
import { buildAuthHeaders } from "./auth";

export interface WalletResponse {
  walletId: number;
  userId: number;
  balance: number;
  createdAt: string;
  updatedAt: string;
}

export interface WalletTransactionItem {
  transactionId: number;
  walletId: number;
  transactionType: "DEPOSIT" | "WITHDRAWAL" | "PURCHASE";
  amount: number;
  balanceAfter: number;
  createdAt: string;
}

export async function fetchWallet(): Promise<WalletResponse> {
  const response = await axios.get<WalletResponse>("/api/wallet/me", {
    headers: buildAuthHeaders(),
  });
  return response.data;
}

export async function depositToWallet(amount: number): Promise<WalletResponse> {
  const response = await axios.post<WalletResponse>(
    "/api/wallet/deposit",
    { amount },
    { headers: buildAuthHeaders() }
  );
  return response.data;
}

export async function withdrawFromWallet(amount: number): Promise<WalletResponse> {
  try {
    const response = await axios.post<WalletResponse>(
      "/api/wallet/withdraw",
      { amount },
      { headers: buildAuthHeaders() }
    );
    return response.data;
  } catch (error) {
    if (axios.isAxiosError(error)) {
      const message =
        error.response?.data?.message ||
        error.message ||
        "Withdrawal failed";
      throw new Error(message);
    }
    throw error;
  }
}

export async function fetchWalletTransactions(): Promise<WalletTransactionItem[]> {
  const response = await axios.get<WalletTransactionItem[]>("/api/wallet/transactions", {
    headers: buildAuthHeaders(),
  });
  return response.data;
}

