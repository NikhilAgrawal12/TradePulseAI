export const MONEY_DECIMALS = 2;
const MONEY_FACTOR = 10 ** MONEY_DECIMALS;

// Centralized money/percentage precision policy for frontend state and UI rendering.

export function roundMoney(value: number): number {
  if (!Number.isFinite(value)) {
    return 0;
  }

  return Math.round((value + Number.EPSILON) * MONEY_FACTOR) / MONEY_FACTOR;
}

export function toMoney(value: unknown): number {
  const numeric = typeof value === "number" ? value : Number(value);
  return roundMoney(Number.isFinite(numeric) ? numeric : 0);
}

export function formatMoney(value: unknown): string {
  return toMoney(value).toFixed(MONEY_DECIMALS);
}

export function formatSignedMoney(value: unknown): string {
  const amount = toMoney(value);
  const sign = amount >= 0 ? "+" : "-";
  return `${sign}${Math.abs(amount).toFixed(MONEY_DECIMALS)}`;
}

export function formatSignedCurrency(value: unknown): string {
  const amount = toMoney(value);
  const sign = amount >= 0 ? "+" : "-";
  return `${sign}$${Math.abs(amount).toFixed(MONEY_DECIMALS)}`;
}

export function formatPercent(value: unknown, includePlusForPositive = true): string {
  const percentage = toMoney(value);
  if (percentage > 0 && includePlusForPositive) {
    return `+${percentage.toFixed(MONEY_DECIMALS)}`;
  }
  return percentage.toFixed(MONEY_DECIMALS);
}


