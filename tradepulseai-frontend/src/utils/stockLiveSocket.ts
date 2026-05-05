import { Client } from "@stomp/stompjs";
import type { StockLiveEvent } from "../types/stockLive";

function createWsUrl() {
  const protocol = window.location.protocol === "https:" ? "wss" : "ws";
  return `${protocol}://${window.location.host}/ws/stocks`;
}

export function connectStockLiveFeed(onEvent: (event: StockLiveEvent) => void) {
  const client = new Client({
    brokerURL: createWsUrl(),
    reconnectDelay: 5000,
  });

  client.onConnect = () => {
    client.subscribe("/topic/stocks", (message) => {
      try {
        const parsed = JSON.parse(message.body) as StockLiveEvent;
        if (parsed && parsed.symbol) {
          onEvent(parsed);
        }
      } catch {
        // Ignore malformed payloads and keep websocket stream alive.
      }
    });
  };

  client.activate();

  return () => {
    void client.deactivate();
  };
}

