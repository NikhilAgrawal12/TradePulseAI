# OrderPayment gRPC Flow - Complete Verification

## Flow Summary
When a user clicks **Pay** on the frontend payment page:

```
Frontend (PaymentPage.tsx)
    ↓ completeOrder() HTTP POST
Order Service (POST /api/cart/complete-order)
    ↓ CartService.completeOrder(userEmail)
    ├─ Loads user cart items
    └─ For each cart item:
       ↓ OrderPaymentGrpcClient.completePayment(cartItem)
       ↓ gRPC call over port 9002
Payment Service (OrderPaymentGrpcService)
    ↓ Receives OrderPaymentRequest
    ├─ PaymentProcessingService.processPayment()
    │  └─ Saves Payment entity to PostgreSQL
    │
    └─ Returns OrderPaymentResponse (accountId + status=COMPLETED)
       ↓ gRPC response back to order-service
Order Service
    ├─ Validates all payments completed
    ├─ Clears cart from database
    └─ Returns CompleteOrderResponseDTO to frontend
Frontend
    ├─ Clears local cart context
    ├─ Adds order to local orders storage
    └─ Shows success modal
```

## Backend Components Created

### 1. Payment-Service Database Layer

#### Model: `Payment.java`
- **Table:** `payments`
- **Fields:**
  - `id` (UUID, PK)
  - `cart_item_id` (String, FK reference)
  - `user_email` (String, indexed)
  - `stock_id` (String, indexed)
  - `symbol` (String)
  - `price` (BigDecimal)
  - `quantity` (Integer)
  - `total_amount` (BigDecimal) - calculated as price × quantity
  - `status` (String, default="COMPLETED")
  - `created_at` (Instant, auto-set)

#### Repository: `PaymentRepository.java`
- Extends `JpaRepository<Payment, UUID>`
- Methods:
  - `findByUserEmail(String)` - retrieve payments by user
  - `findByStockId(String)` - retrieve payments by stock
  - Inherited `findAll()`, `save()`, etc.

#### Service: `PaymentProcessingService.java`
- `processPayment(...)` - transactional method that:
  1. Converts price to BigDecimal
  2. Calculates `totalAmount = price × quantity`
  3. Creates Payment entity with all cart item fields
  4. Saves to database via PaymentRepository
  5. Returns saved Payment object
- `generateAccountId(userEmail)` - creates unique account identifier

### 2. Payment-Service gRPC Layer

#### Enhanced: `OrderPaymentGrpcService.java`
Now:
- Injects `PaymentProcessingService`
- In `completePayment(OrderPaymentRequest, ...)`:
  1. Logs incoming request with cart details
  2. Calls `paymentProcessingService.processPayment(...)` to persist
  3. Generates unique `accountId` from user email
  4. Returns `OrderPaymentResponse` with:
     - `accountId` - unique identifier for the payment account
     - `status` - "COMPLETED" on success
  5. Has error handling: logs failures and returns gRPC error

### 3. Payment-Service REST API (for verification)

#### Controller: `PaymentController.java`
Endpoints to verify payments were saved:
- `GET /payments` - retrieve all payments
- `GET /payments/user/{userEmail}` - get payments for a specific user
- `GET /payments/stock/{stockId}` - get payments for a specific stock

### 4. Configuration Updates

#### `application.properties`
```ini
spring.datasource.url=jdbc:postgresql://host.docker.internal:5432/tradepulse_payment
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.jpa.hibernate.ddl-auto=update
grpc.server.port=9002
```

- Auto-creates `payments` table on startup (Hibernate `ddl-auto=update`)
- Database URL points to `tradepulse_payment` database
- gRPC server listens on port 9002

#### `pom.xml`
Added dependencies:
- `spring-boot-starter-data-jpa` - for ORM
- `postgresql` - JDBC driver
- `springdoc-openapi-starter-webmvc-ui` - Swagger documentation

## Order-Service Integration

#### `CartService.completeOrder(userEmail)`
1. Loads all cart items for user
2. Validates cart is not empty
3. **For each cart item:**
   - Calls `OrderPaymentGrpcClient.completePayment(cartItem)`
   - Validates response status is "COMPLETED"
   - Throws exception if payment fails
4. On all payments success:
   - Clears cart from database
   - Returns `CompleteOrderResponseDTO` with accountId + COMPLETED status

#### `OrderPaymentGrpcClient.completePayment(CartItem)`
Builds `OrderPaymentRequest` from cart item:
- `cart_item_id` → cartItem.getId()
- `user_email` → cartItem.getUserEmail()
- `stock_id` → cartItem.getStockId()
- `symbol` → cartItem.getSymbol()
- `price` → cartItem.getPrice().doubleValue()
- `quantity` → cartItem.getQuantity()

Calls blocking stub: `blockingStub.completePayment(request)`

## Frontend Integration

#### `PaymentPage.tsx` handlePayNow()
1. Calls `completeOrder()` API
2. Receives `CompleteOrderResponse`
3. If status != "COMPLETED", shows error message
4. On success:
   - Adds order to `OrdersContext`
   - Clears cart via `clearCart()`
   - Shows success modal
   - User navigates to /orders

## Data Persistence Verification

### How to verify payment was saved:

**Option 1: REST API**
```bash
curl http://localhost:4001/payments
curl http://localhost:4001/payments/user/user%40example.com
curl http://localhost:4001/payments/stock/AAPL
```

**Option 2: PostgreSQL Direct Query**
```sql
SELECT * FROM payments WHERE user_email = 'user@example.com';
SELECT * FROM payments WHERE stock_id = 'AAPL';
SELECT SUM(total_amount) FROM payments WHERE user_email = 'user@example.com';
```

**Option 3: Application Logs**
```
INFO OrderPaymentGrpcService - CompletePayment request received for cartItemId=..., userEmail=..., stockId=..., qty=...
INFO PaymentProcessingService - Processing payment for cartItemId=...
INFO PaymentProcessingService - Payment saved successfully with id=..., status=COMPLETED, totalAmount=...
INFO OrderPaymentGrpcService - CompletePayment response sent: accountId=acct-user%40example.com-xyz123, status=COMPLETED
```

## Error Handling

**Order-Service Side:**
- Empty cart → throws `IllegalArgumentException`
- Payment status not "COMPLETED" → throws `IllegalStateException`
- gRPC call fails → exception propagates to PaymentPage error state

**Payment-Service Side:**
- Database error → logged, gRPC error sent back
- Request validation → implicit (Protobuf message structure)

## Next Steps

1. **Test the flow end-to-end:**
   - Start Docker containers (postgres, payment-service, order-service, frontend)
   - Add item to cart → checkout → payment
   - Verify payment saved in `payments` table

2. **Monitor logs for:**
   - "Payment saved successfully" messages
   - Account ID generation
   - gRPC request/response timing

3. **Query database to confirm:**
   - Payment records created
   - User email, stock, and quantity persisted correctly
   - Total amount calculated correctly (price × quantity)


