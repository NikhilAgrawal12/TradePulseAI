export type CartItem = {
  id?: string;
  stockId: string;
  symbol: string;
  price: number;
  quantity: number;
};

export type AddCartItemRequest = {
  stockId: string;
  symbol: string;
  price: number;
  quantity: number;
};

export type UpdateCartItemRequest = {
  quantity: number;
};

