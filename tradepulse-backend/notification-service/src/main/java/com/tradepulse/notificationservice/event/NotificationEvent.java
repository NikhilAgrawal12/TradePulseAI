package com.tradepulse.notificationservice.event;

import java.util.Map;

public class NotificationEvent {

    private String eventType;
    private Long userId;
    private String timestamp;
    private Map<String, Object> data;

    public NotificationEvent() {}

    public NotificationEvent(String eventType, Long userId, String timestamp, Map<String, Object> data) {
        this.eventType = eventType;
        this.userId = userId;
        this.timestamp = timestamp;
        this.data = data;
    }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }

    @Override
    public String toString() {
        return "NotificationEvent{eventType=" + eventType + ", userId=" + userId + ", timestamp=" + timestamp + "}";
    }
}

