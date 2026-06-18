export const MONEY_DECIMALS = 2;
const MONEY_FACTOR = 10 ** MONEY_DECIMALS;

export function roundMoney(value: number): number {
  if (!Number.isFinite(value)) {
    return 0;
  }

  return Math.round((value + Number.EPSILON) * MONEY_FACTOR) / MONEY_FACTOR;
}


