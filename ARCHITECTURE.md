# Architecture - Real-Time Stock Data Pipeline

## Overview

TradePulseAI's data pipeline fetches stock prices from Polygon.io and delivers them through Kafka to both a PostgreSQL database (for analytics) and a WebSocket stream (for real-time frontend display).

## Complete Data Flow

```
┌─────────────────────────────────────┐
│       Polygon.io API                │
│  (Real-time stock prices)           │
└──────────────┬──────────────────────┘
               │
               ↓ Every 12 seconds
    ┌──────────────────────────────┐
    │ PolygonStockIngestionScheduler│
    │  - Fetches latest prices     │
    │  - Calculates change %       │
    │  - Creates market event      │
    └──────────────┬───────────────┘
                   │
                   ↓
    ┌──────────────────────────────┐
    │ StockMarketKafkaPublisher    │
    │ Publishes to Kafka           │
    └──────────────┬───────────────┘
                   │
        ┌──────────▼──────────┐
        │  Kafka Topic        │
        │  "stocks"           │
        └─────┬────────┬──────┘
              │        │
    ┌─────────▼──┐  ┌──▼──────────┐
    │ Consumer    │  │ Consumer    │
    │ Group 1:    │  │ Group 2:    │
    │ stock-      │  │ stock-      │
    │ service-db  │  │ service-ws  │
    └─────┬───────┘  └──┬──────────┘
          │             │
    ┌─────▼──────────┐ ┌─▼────────────────┐
    │ Persistence    │ │ WebSocket Bridge │
    │ Consumer       │ │ Consumer         │
    │                │ │                  │
    │ Saves to DB    │ │ Broadcasts to    │
    │                │ │ WebSocket        │
    └─────┬──────────┘ └──┬───────────────┘
          │               │
    ┌─────▼──────────┐ ┌──▼──────────────┐
    │ PostgreSQL     │ │ WebSocket       │
    │ Database       │ │ /topic/stocks   │
    │                │ │                 │
    │  ✅ Persistence│ │  ✅ Real-time   │
    │  ✅ Analytics  │ │  ✅ Display     │
    └────────────────┘ └──┬──────────────┘
                          │
                    ┌─────▼─────────┐
                    │  Frontend     │
                    │  (React)      │
                    │               │
                    │  Stock Prices │
                    │  Display      │
                    └───────────────┘
```

## Key Components

### 1. Polygon Ingestion
**Service**: `PolygonStockIngestionScheduler`

Runs every 12 seconds to fetch latest stock prices from Polygon.io API:
- Fetches previous close prices for each stock
- Calculates percentage change from current price in database
- Creates a `StockMarketEvent` with:
  - Symbol
  - Price
  - Change Percent
  - Volume
  - Market Timestamp
  - Source ("polygon")

### 2. Kafka Publishing
**Service**: `StockMarketKafkaPublisher`

Serializes the stock market event and publishes to the `stocks` Kafka topic:
- Uses the symbol as the partition key for consistency
- Messages are byte arrays of serialized StockMarketEvent objects
- All messages go to a single topic

### 3. Database Persistence (Parallel Path 1)
**Service**: `StockMarketKafkaPersistenceConsumer`
**Consumer Group**: `stock-service-db`

Listens to the Kafka topic and persists all data to PostgreSQL:
- Reads serialized events from Kafka
- Finds or creates the stock record
- Updates price, change percent, volume, timestamp, and source
- Saves to PostgreSQL `stocks` table

**Why?** All stock data is persisted for:
- Historical analysis
- Trend tracking
- Data recovery
- Audit trail

### 4. WebSocket Broadcasting (Parallel Path 2)
**Service**: `StockMarketKafkaWebSocketBridge`
**Consumer Group**: `stock-service-ws`

Listens to the Kafka topic and broadcasts to connected WebSocket clients:
- Reads serialized events from Kafka
- Deserializes to StockMarketEvent
- Broadcasts to `/topic/stocks` WebSocket endpoint
- All connected frontend clients receive updates in real-time

**Why?** Frontend clients need live price updates without polling.

### 5. Frontend Reception
**Component**: `connectStockLiveFeed()` in `stockLiveSocket.ts`

Establishes WebSocket connection to receive live updates:
- Connects via STOMP protocol over WebSocket
- Subscribes to `/topic/stocks` topic
- Receives price updates from StockMarketKafkaWebSocketBridge
- Updates React state to trigger UI re-render

## Parallel Processing with Kafka Consumer Groups

Both consumers process the **same** Kafka messages independently:

```
One Kafka Message (AAPL price update)
    ↓
    ├─→ Received by Consumer Group "stock-service-db"
    │   └─→ PersistenceConsumer saves to PostgreSQL
    │       (Database updated)
    │
    └─→ Received by Consumer Group "stock-service-ws"
        └─→ WebSocketBridgeConsumer broadcasts to frontend
            (Frontend updated)

Result: Both happen simultaneously!
Consumer Groups are independent - if one fails, the other continues.
```

## Data Types

### StockMarketEvent (Kafka Message)
```java
public record StockMarketEvent(
    String symbol,           // "AAPL"
    BigDecimal price,        // 180.25
    BigDecimal changePercent, // 2.45
    long volume,            // 50000000
    Instant marketTimestamp, // 2026-05-05T14:30:00Z
    String source           // "polygon" or "test-endpoint"
) {}
```

### Stock (Database Model)
```java
@Entity
@Table(name = "stocks")
public class Stock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long stockId;
    
    @Column(unique = true)
    private String symbol;
    
    private String name;
    private String exchange;
    private String market;
    private String locale;
    private Boolean active;
    private BigDecimal price;        // Updated by consumers
    private BigDecimal changePercent; // Updated by consumers
    private Long volume;             // Updated by consumers
    private Instant lastUpdated;     // Updated by consumers
    private String source;           // "polygon", "mock-data", etc
}
```

## Data Flow Timing

```
T+0ms:    Polygon API publishes AAPL price
          
T+10ms:   PolygonStockIngestionScheduler receives

T+50ms:   StockMarketKafkaPublisher sends to Kafka

T+55ms:   Both consumer groups receive from Kafka
          ├─ PersistenceConsumer saves to DB
          └─ WebSocketBridge broadcasts to frontend

T+60ms:   Frontend receives WebSocket message

T+65ms:   React state updates

T+70ms:   UI re-renders with new price

Total: ~70ms from API to user display
```

## Configuration

### Backend (application.properties)

```properties
# Polygon API
polygon.api.base-url=https://api.polygon.io
polygon.api.key=${POLYGON_API_KEY:}
polygon.fetch.enabled=${POLYGON_FETCH_ENABLED:false}
polygon.fetch.fixed-delay-ms=${POLYGON_FETCH_DELAY_MS:12000}

# Kafka
spring.kafka.bootstrap-servers=localhost:9092
stock.kafka.topic=stocks
stock.kafka.consumer.persistence.group-id=stock-service-db
stock.kafka.consumer.websocket.group-id=stock-service-ws

# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/tradepulse_stock
spring.jpa.hibernate.ddl-auto=update
```

### Frontend

WebSocket URL is auto-detected from the current location:
```typescript
const protocol = window.location.protocol === "https:" ? "wss" : "ws";
const url = `${protocol}://${window.location.host}/ws/stocks`;
```

## Features

- ✅ **Real-time Updates**: WebSocket broadcasts with ~70ms latency
- ✅ **Data Persistence**: All prices stored in PostgreSQL
- ✅ **Parallel Processing**: Database and WebSocket consumers work independently
- ✅ **Data Recovery**: Can restore from PostgreSQL on restart
- ✅ **Scalability**: Easy to add new consumers for alerts, analytics, etc.
- ✅ **Reliability**: Kafka ensures no messages are lost
- ✅ **Development Support**: Mock data seeder works without API key

## Testing

### Manual Test
```bash
# Publish test event
curl -X POST http://localhost:4003/test/kafka/publish-stock-event \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "AAPL",
    "price": 200,
    "changePercent": 10,
    "volume": 5000000
  }'
```

Expected results:
- Backend logs show publishing to Kafka
- Database is updated with new price
- Frontend WebSocket receives update
- Stock card on frontend updates instantly

### Verify Consumer Groups
```bash
docker exec -it tradepulse_kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --list

# Should show:
# stock-service-db
# stock-service-ws
```

Check if messages are being processed (LAG should be 0):
```bash
docker exec -it tradepulse_kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group stock-service-db \
  --describe
```

## Production Considerations

- Use managed Kafka service (AWS MSK, Confluent Cloud)
- Use external PostgreSQL database
- Enable SSL/TLS for security
- Monitor Kafka consumer lag to ensure messages are processed
- Set up database backups
- Configure alerts for consumer lag
- Use separate Kafka clusters for reliability
- Deploy multiple instances of both consumers for redundancy

