package com.tradepulse.portfolioservice.kafka;

import java.util.List;

public class OrderCompletedEvent {

    private String eventType;
    private String eventId;
    private String orderId;
    private Long userId;
    private String timestamp;
    private OrderCompletedData data;

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public OrderCompletedData getData() {
        return data;
    }

    public void setData(OrderCompletedData data) {
        this.data = data;
    }

    public static class OrderCompletedData {
        private String total;
        private List<OrderCompletedItem> items;

        public String getTotal() {
            return total;
        }

        public void setTotal(String total) {
            this.total = total;
        }

        public List<OrderCompletedItem> getItems() {
            return items;
        }

        public void setItems(List<OrderCompletedItem> items) {
            this.items = items;
        }
    }

    public static class OrderCompletedItem {
        private String stockId;
        private String price;
        private Integer quantity;

        public String getStockId() {
            return stockId;
        }

        public void setStockId(String stockId) {
            this.stockId = stockId;
        }

        public String getPrice() {
            return price;
        }

        public void setPrice(String price) {
            this.price = price;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
    }
}

