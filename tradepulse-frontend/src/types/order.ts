export type OrderItem = {
  stockId: string;
  symbol: string;
  price: number;
  quantity: number;
  lineTotal: number;
};

export type OrderHistoryEntry = {
  id: string;
  orderNumber: number;
  userId: number;
  status: string;
  createdAtIso: string;
  total: number;
  items: OrderItem[];
};

