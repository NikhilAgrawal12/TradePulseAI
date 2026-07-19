export type CartItem = {
  userId?: number;
  stockId: string;
  symbol: string;
  price: number;
  quantity: number;
  lineTotal?: number;
};

export type AddCartItemRequest = {
  stockId: string;
  quantity: number;
};

export type UpdateCartItemRequest = {
  quantity: number;
};

export type CompleteOrderResponse = {
  orderId?: string;
  accountId: string;
  status: string;
};

export type LockQuoteRequest = {
  items: CartItem[];
  total: number;
};

export type LockQuoteResponse = {
  items: CartItem[];
  total: number;
  lockSeconds: number;
};

