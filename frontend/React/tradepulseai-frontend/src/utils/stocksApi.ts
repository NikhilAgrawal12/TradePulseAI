import axios from "axios";
import type { Stock } from "../types/stock";

export async function fetchStocks(): Promise<Stock[]> {
  const response = await axios.get<Stock[]>("/api/stocks");
  return response.data;
}

