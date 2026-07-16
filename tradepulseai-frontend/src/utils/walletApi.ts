import axios from "axios";
import { buildAuthHeaders } from "./auth";
import { toMoney } from "./money";

export interface PaginatedResponse<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

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
  transactionType: "DEPOSIT" | "WITHDRAWAL" | "PURCHASE" | "REFUND" | "SELL_CREDIT";
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

export async function fetchWalletTransactionsPage(page: number, size: number): Promise<PaginatedResponse<WalletTransactionItem>> {
  const response = await axios.get<PaginatedResponse<WalletTransactionItem>>("/api/wallet/transactions/paged", {
    headers: buildAuthHeaders(),
    params: { page, size },
  });

  return {
    ...response.data,
    content: normalizeWalletTransactions(response.data.content ?? []),
  };
}

