# Email Personalization Architecture

## System Flow Diagram

```
┌─────────────────┐
│   User Action   │
└────────┬────────┘
         │
    ┌────┴────┬──────────┐
    │          │          │
┌───▼──────────▼──┐   ┌──▼────────────┐
│ Cart Completion │   │ Stock Sale    │
│ (Order Service) │   │ (Portfolio    │
└───┬─────────────┘   └──┬───────────┘
    │                    │
    │ 1. Fetch Customer  │ 1. Fetch Customer
    │    Name via        │    Name via
    │    REST            │    REST
    │    ↓               │    ↓
┌───┴──────────────┐ ┌──┴──────────┐
│ CustomerClient   │ │ CustomerClient
│ (order-service)  │ │ (portfolio-service)
└────┬─────────────┘ └──┬──────────┘
     │                  │
     │ /customers/user/ │
     │ {userId}         │
     │                  │
     └────────┬─────────┘
              │
              ▼
    ┌─────────────────┐
    │  Cust-Service   │
    │ (REST Endpoint) │
    └────────┬────────┘
             │
          Returns:
          firstName
          lastName
             │
    ┌────────┴─────────┐
    │                  │
┌───▼──────────────┐ ┌─▼───────────────┐
│ 2. Call          │ │ 2. Call         │
│ publishStock     │ │ publishStock    │
│ Purchased()      │ │ Sold()          │
│ with names       │ │ with names      │
└───┬──────────────┘ └─┬───────────────┘
    │                 │
    │ Publishes to Kafka Topic: tradepulse.notifications
    │ Payload includes:
    │   - firstName
    │   - lastName
    │   - Other order details
    │
    └────────┬────────┘
             │
             ▼
    ┌─────────────────┐
    │  Kafka Topic    │
    │  (notifications)│
    └────────┬────────┘
             │
             ▼
    ┌─────────────────────────────┐
    │  Notification Service       │
    │  Kafka Listener             │
    └────────┬────────────────────┘
             │
             ▼
    ┌─────────────────────────────┐
    │ EmailNotificationService    │
    │ buildBody(event)            │
    │                             │
    │ ┌─────────────────────────┐ │
    │ │ Extract:                │ │
    │ │ - firstName from data   │ │
    │ │ - lastName from data    │ │
    │ │ - Combine to fullName   │ │
    │ │ - Fallback if empty     │ │
    │ └─────────────────────────┘ │
    └────────┬────────────────────┘
             │
    ┌────────▼────────────────────┐
    │ Email Template:             │
    │ "Hi [fullName],"            │
    │                             │
    │ Example Output:             │
    │ \"Hi John Doe,              │
    │  Your stock purchase order  │
    │  has been completed..."     │
    └────────┬────────────────────┘
             │
             ▼
    ┌──────────────────┐
    │  Send Email via  │
    │  JavaMailSender  │
    └──────────────────┘
```

---

## Data Flow: Complete Purchase

```
User Action: Buy 2 shares of AAPL
    ↓
CartController receives request
    ├─ Header: X-User-Id: 1
    └─ Body: { items: [{stockId: "100", quantity: 2}] }
    ↓
CartService.completeOrder(userId=1, request)
    ├─ 1. Save order to database
    ├─ 2. Process payment via gRPC (payment-service)
    ├─ 3. Sync portfolio via gRPC (portfolio-service)
    ├─ 4. Fetch customer data:
    │   └─ customerClient.getCustomer(1)
    │       └─ REST call: GET /customers/user/1
    │           └─ Response: { firstName: "John", lastName: "Doe", ... }
    ├─ 5. Call publishStockPurchased(1, "John", "Doe", order)
    │   └─ NotificationKafkaProducer.publishStockPurchased()
    │       └─ Publishes to Kafka:
    │           {
    │             "eventType": "STOCK_PURCHASED",
    │             "userId": 1,
    │             "timestamp": "2026-07-16T...",
    │             "data": {
    │               "firstName": "John",
    │               "lastName": "Doe",
    │               "orderId": "uuid-123",
    │               "symbol": "AAPL",
    │               "quantity": "2.00",
    │               "price": "150.50",
    │               "total": "301.00"
    │             }
    │           }
    └─ 6. Return success response
        ↓
Kafka Listener in Notification Service
    ├─ Receives event
    ├─ Calls buildBody(event)
    ├─ Extracts firstName="John", lastName="Doe"
    ├─ Creates fullName="John Doe"
    ├─ Formats email body:
    │   "Hi John Doe,
    │    Your stock purchase order has been completed successfully.
    │    Stock: AAPL
    │    Quantity: 2 shares
    │    Price: $150.50 per share
    │    Total: $301.00"
    └─ Sends email to john@example.com
```

---

## Data Flow: Wallet Deposit

```
User Action: Deposit $100
    ↓
WalletController.deposit(userId=3, amount=100)
    ↓
WalletService.deposit(userId=3, firstName="Robert", lastName="Johnson", amount=100)
    ├─ 1. Update wallet balance
    ├─ 2. recordTransaction() → returns UUID transactionId
    ├─ 3. Call publishWalletDeposit(3, "Robert", "Johnson", transactionId, 100, newBalance)
    │   └─ NotificationKafkaProducer.publishWalletDeposit()
    │       └─ Publishes to Kafka:
    │           {
    │             "eventType": "WALLET_DEPOSIT",
    │             "userId": 3,
    │             "timestamp": "2026-07-16T...",
    │             "data": {
    │               "firstName": "Robert",
    │               "lastName": "Johnson",
    │               "transactionId": "550e8400-e29b-41d4-a716-446655440000",
    │               "amount": "100.00",
    │               "newBalance": "1100.00"
    │             }
    │           }
    └─ 4. Return wallet
        ↓
Kafka Listener in Notification Service
    ├─ Receives event
    ├─ Calls buildBody(event)
    ├─ Extracts firstName="Robert", lastName="Johnson"
    ├─ Creates fullName="Robert Johnson"
    ├─ Formats email body:
    │   "Hi Robert Johnson,
    │    Your deposit of $100.00 has been successfully processed.
    │    Transaction ID: 550e8400-e29b-41d4-a716-446655440000
    │    New Balance: $1100.00"
    └─ Sends email to robert@example.com
```

---

## Component Interactions

### CustomerClient Pattern (Fetch Customer Data)

#### Order Service
```
CartService
    ├─ Constructor: @Autowired CustomerClient
    └─ completeOrder():
        ├─ Create order
        ├─ Process payment
        ├─ Sync portfolio
        ├─ customerClient.getCustomer(userId)  ← REST call to cust-service
        └─ publishStockPurchased(userId, firstName, lastName, order)
```

#### Portfolio Service
```
PortfolioService
    ├─ Constructor: @Autowired CustomerClient
    └─ sell():
        ├─ Validate holding
        ├─ Process settlement
        ├─ customerClient.getCustomer(userId)  ← REST call to cust-service
        └─ publishStockSold(userId, firstName, lastName, ...)
```

#### Payment Service (Already Done)
```
WalletService
    ├─ Overloaded deposit/withdraw methods
    └─ Accepts firstName, lastName as parameters
        └─ publishWalletDeposit/Withdrawal(..., firstName, lastName, ...)
```

---

## Transaction ID Evolution

### Before (Sequential Numbers)
```
wallet_transaction table:
  transaction_id (BIGINT, auto-increment)
  
Examples:
  1, 2, 3, 4, 5, ...
  
Email:
  "Transaction ID: 44"
```

### After (UUID)
```
wallet_transaction table:
  transaction_id (VARCHAR(36), UUID)
  
Examples:
  550e8400-e29b-41d4-a716-446655440000
  6ba7b810-9dad-11d1-80b4-00c04fd430c8
  
Email:
  "Transaction ID: 550e8400-e29b-41d4-a716-446655440000"
```

---

## Stock Symbol Resolution

### Before
```
Email showed:
  "Stock: 8"  ← numeric ID instead of symbol
```

### After (Both Services Now Fetch Symbol)

#### Order Service
```
OrderService.NotificationKafkaProducer.publishStockPurchased()
    ├─ Extract stockId from order
    ├─ Fetch symbol via StockCatalogClient (gRPC to stock-service)
    ├─ Include in Kafka payload: "symbol": "AAPL"
    └─ Include in Kafka payload: "stockId": 100  (fallback)
```

#### Portfolio Service
```
PortfolioService.NotificationKafkaProducer.publishStockSold()
    ├─ Extract stockId from request
    ├─ Fetch symbol via REST to stock-service (/stocks/{id})
    ├─ Include in Kafka payload: "symbol": "GOOGL"
    └─ No redundant stockId in payload
```

#### Email Service
```
EmailNotificationService.buildBody()
    ├─ Try to read "symbol" from data (preferred)
    ├─ Fallback to "stockId" if symbol missing
    └─ Display in email: "Stock: AAPL"
```

---

## Quantity Pluralization

### Implementation
```java
private String shareUnit(String quantity) {
    if (quantity == null || quantity.isBlank()) {
        return "shares";
    }
    try {
        return new BigDecimal(quantity)
            .compareTo(BigDecimal.ONE) == 0 
            ? "share" 
            : "shares";
    } catch (NumberFormatException e) {
        return "shares";
    }
}
```

### Examples
```
Quantity: 1.00       → "1.00 share"
Quantity: 2.00       → "2.00 shares"
Quantity: 1.50       → "1.50 shares"
Quantity: 0.50       → "0.50 shares"
Quantity: null/empty → "shares"  (fallback)
```

---

## Configuration Requirements

### order-service/application.properties
```properties
# Customer Service for fetching customer names
cust.service.base-url=http://cust-service:4001

# Stock Service for fetching stock quotes
stock.service.grpc.address=stock-service
stock.service.grpc.port=9003
```

### portfolio-service/application.properties
```properties
# Customer Service for fetching customer names
cust.service.base-url=http://cust-service:4001

# Stock Service for fetching stock symbols
stock.service.base-url=http://stock-service:4003
```

### payment-service/application.properties
```properties
# Email configuration (already exists)
tradepulseai.mail.from=no-reply@tradepulseai.local
spring.mail.host=mail-server
spring.mail.port=587
```

---

## Error Handling & Resilience

### Customer Data Fetch Failures
```
If CustomerClient.getCustomer() fails:
    ├─ Logs warning: "Unable to fetch customer data for userId=X"
    └─ Returns: CustomerInfo("", "")
        └─ Email falls back to: "Hi Valued Customer,"
```

### Stock Symbol Fetch Failures
```
If symbol fetch fails:
    ├─ Order Service: Falls back to stockId number
    ├─ Portfolio Service: Returns "UNKNOWN"
    └─ Email displays: "Stock: UNKNOWN" or "Stock: 100"
```

### Kafka/Email Failures
```
If email send fails:
    ├─ Exception caught
    ├─ Logged: "Failed to send notification email for eventType=X"
    └─ No exception propagated (non-blocking)
```

---

## Performance Considerations

### REST Calls (Customer Data)
- **Latency**: ~50-100ms per REST call
- **Frequency**: 1 call per order/sale/deposit
- **Caching**: Optional (could cache customer data for 1-5 minutes)
- **Circuit Breaker**: Recommended (not yet implemented)

### Transaction ID Generation (UUID)
- **Latency**: Minimal (~0.1ms, generated by Hibernate)
- **Storage**: 36 bytes per UUID (vs 8 bytes for BIGINT)
- **No performance impact**

### Stock Symbol Fetch (gRPC/REST)
- **Order Service**: Uses gRPC (faster ~10-20ms)
- **Portfolio Service**: Uses REST (~50-100ms)
- **Frequency**: 1 call per order/sale
- **Already optimized with caching in StockCatalogClient**

