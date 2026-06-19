import axios from "axios";
import { buildAuthHeaders } from "./auth";
import { toMoney } from "./money";

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

function normalizeWallet(wallet: WalletResponse): WalletResponse {
  return {
    ...wallet,
    balance: toMoney(wallet.balance),
  };
}

function normalizeWalletTransactions(transactions: WalletTransactionItem[]): WalletTransactionItem[] {
  return transactions.map((transaction) => ({
    ...transaction,
    amount: toMoney(transaction.amount),
    balanceAfter: toMoney(transaction.balanceAfter),
  }));
}

export async function fetchWallet(): Promise<WalletResponse> {
  const response = await axios.get<WalletResponse>("/api/wallet/me", {
    headers: buildAuthHeaders(),
  });
  return normalizeWallet(response.data);
}

export async function depositToWallet(amount: number): Promise<WalletResponse> {
  const normalizedAmount = toMoney(amount);
  const response = await axios.post<WalletResponse>(
    "/api/wallet/deposit",
    { amount: normalizedAmount },
    { headers: buildAuthHeaders() }
  );
  return normalizeWallet(response.data);
}

export async function withdrawFromWallet(amount: number): Promise<WalletResponse> {
  try {
    const normalizedAmount = toMoney(amount);
    const response = await axios.post<WalletResponse>(
      "/api/wallet/withdraw",
      { amount: normalizedAmount },
      { headers: buildAuthHeaders() }
    );
    return normalizeWallet(response.data);
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
  return normalizeWalletTransactions(response.data);
}

