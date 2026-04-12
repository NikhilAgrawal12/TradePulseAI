export type OrderItem = {
  stockId: string;
  symbol: string;
  price: number;
  quantity: number;
  lineTotal: number;
};

export type OrderHistoryEntry = {
  id: number;
  userId: number;
  status: string;
  createdAtIso: string;
  subtotal: number;
  tax: number;
  total: number;
  items: OrderItem[];
};

