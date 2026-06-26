import { useEffect, useState, type FormEvent } from "react";
import { Header } from "../../components/Header.tsx";
import { useWallet } from "../../context/WalletContext";
import { formatEasternDateTime } from "../../utils/dateTime";
import { formatMoney, formatSignedCurrency, toMoney } from "../../utils/money";
import { depositToWallet, fetchWalletTransactions, withdrawFromWallet } from "../../utils/walletApi";
import type { WalletTransactionItem } from "../../utils/walletApi";
import "./WalletPage.css";

export function WalletPage() {
  useEffect(() => {
    document.title = "Wallet | TradePulseAI";
  }, []);

  const { balance, isLoading, refreshWallet } = useWallet();
  const [transactions, setTransactions] = useState<WalletTransactionItem[]>([]);
  const [txLoading, setTxLoading] = useState(true);

  // Deposit state
  const [depositAmount, setDepositAmount] = useState("");
  const [depositMsg, setDepositMsg] = useState<{ type: "success" | "error"; text: string } | null>(null);
  const [depositLoading, setDepositLoading] = useState(false);

  // Withdraw state
  const [withdrawAmount, setWithdrawAmount] = useState("");
  const [withdrawMsg, setWithdrawMsg] = useState<{ type: "success" | "error"; text: string } | null>(null);
  const [withdrawLoading, setWithdrawLoading] = useState(false);

  const loadTransactions = async () => {
    setTxLoading(true);
    try {
      const data = await fetchWalletTransactions();
      setTransactions(data);
    } catch {
      setTransactions([]);
    } finally {
      setTxLoading(false);
    }
  };

  useEffect(() => {
    void loadTransactions();
  }, []);

  const handleDeposit = async (e: FormEvent) => {
    e.preventDefault();
    const amount = toMoney(parseFloat(depositAmount));
    if (!depositAmount || isNaN(amount) || amount <= 0) {
      setDepositMsg({ type: "error", text: "Please enter a valid amount." });
      return;
    }
    setDepositLoading(true);
    setDepositMsg(null);
    try {
      await depositToWallet(amount);
      await refreshWallet();
      await loadTransactions();
      setDepositAmount("");
      setDepositMsg({ type: "success", text: `$${formatMoney(amount)} added to your wallet!` });
    } catch {
      setDepositMsg({ type: "error", text: "Deposit failed. Please try again." });
    } finally {
      setDepositLoading(false);
    }
  };

  const handleWithdraw = async (e: FormEvent) => {
    e.preventDefault();
    const amount = toMoney(parseFloat(withdrawAmount));
    if (!withdrawAmount || isNaN(amount) || amount <= 0) {
      setWithdrawMsg({ type: "error", text: "Please enter a valid amount." });
      return;
    }
    if (amount > balance) {
      setWithdrawMsg({ type: "error", text: "Insufficient wallet balance." });
      return;
    }
    setWithdrawLoading(true);
    setWithdrawMsg(null);
    try {
      await withdrawFromWallet(amount);
      await refreshWallet();
      await loadTransactions();
      setWithdrawAmount("");
      setWithdrawMsg({ type: "success", text: `$${formatMoney(amount)} withdrawn successfully!` });
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Withdrawal failed. Please try again.";
      setWithdrawMsg({ type: "error", text: msg });
    } finally {
      setWithdrawLoading(false);
    }
  };

  const typeBadgeClass = (type: string) => {
    if (type === "DEPOSIT") return "wallet-type-badge wallet-type-deposit";
    if (type === "WITHDRAWAL") return "wallet-type-badge wallet-type-withdrawal";
    return "wallet-type-badge wallet-type-purchase";
  };

  return (
    <>
      <Header />
      <main className="wallet-page">
        <h1 className="wallet-page-title">My Wallet</h1>

        {/* Balance Card */}
        <div className="wallet-balance-card">
          <div>
            <p className="wallet-balance-label">Available Balance</p>
            <p className="wallet-balance-amount">
              {isLoading ? "—" : `$${formatMoney(balance)}`}
            </p>
          </div>
          <div className="wallet-balance-icon">💰</div>
        </div>

        {/* Add Funds / Withdraw */}
        <div className="wallet-actions-grid">
          <div className="wallet-action-card">
            <h2>Add Funds</h2>
            <form onSubmit={handleDeposit} className="wallet-input-group">
              <label htmlFor="deposit-amount">Amount (USD)</label>
              <input
                id="deposit-amount"
                type="number"
                min="1"
                step="0.01"
                placeholder="e.g. 500.00"
                value={depositAmount}
                onChange={(e) => setDepositAmount(e.target.value)}
                required
              />
              <button type="submit" className="wallet-btn wallet-btn-deposit" disabled={depositLoading}>
                {depositLoading ? "Processing…" : "Add Money"}
              </button>
              {depositMsg && (
                <p className={`wallet-action-feedback ${depositMsg.type === "success" ? "wallet-feedback-success" : "wallet-feedback-error"}`}>
                  {depositMsg.text}
                </p>
              )}
            </form>
          </div>

          <div className="wallet-action-card">
            <h2>Withdraw Funds</h2>
            <form onSubmit={handleWithdraw} className="wallet-input-group">
              <label htmlFor="withdraw-amount">Amount (USD)</label>
              <input
                id="withdraw-amount"
                type="number"
                min="1"
                step="0.01"
                placeholder="e.g. 200.00"
                value={withdrawAmount}
                onChange={(e) => setWithdrawAmount(e.target.value)}
                required
              />
              <button type="submit" className="wallet-btn wallet-btn-withdraw" disabled={withdrawLoading}>
                {withdrawLoading ? "Processing…" : "Withdraw"}
              </button>
              {withdrawMsg && (
                <p className={`wallet-action-feedback ${withdrawMsg.type === "success" ? "wallet-feedback-success" : "wallet-feedback-error"}`}>
                  {withdrawMsg.text}
                </p>
              )}
            </form>
          </div>
        </div>

        {/* Transaction History */}
        <div className="wallet-history-card">
          <h2>Transaction History</h2>
          {txLoading ? (
            <p className="wallet-empty-history">Loading transactions…</p>
          ) : transactions.length === 0 ? (
            <p className="wallet-empty-history">No transactions yet. Add funds to get started!</p>
          ) : (
            <div className="wallet-table-wrap">
              <table className="wallet-table">
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Type</th>
                    <th>Amount</th>
                    <th>Balance After</th>
                  </tr>
                </thead>
                <tbody>
                  {transactions.map((tx) => {
                    const isCredit = tx.transactionType === "DEPOSIT";
                    return (
                      <tr key={tx.transactionId}>
                        <td>{formatEasternDateTime(tx.createdAt)}</td>
                        <td><span className={typeBadgeClass(tx.transactionType)}>{tx.transactionType}</span></td>
                        <td className={isCredit ? "wallet-amount-positive" : "wallet-amount-negative"}>
                          {formatSignedCurrency(isCredit ? tx.amount : -tx.amount)}
                        </td>
                        <td>${formatMoney(tx.balanceAfter)}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </main>
    </>
  );
}
