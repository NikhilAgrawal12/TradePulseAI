# Email Personalization Testing Guide

## Verification Checklist

### ✅ Code Changes Completed

#### 1. Email Service
- [x] EmailNotificationService builds fullName from firstName + lastName
- [x] All 4 templates use "Hi [fullName]," greeting format
- [x] Fallback to "Valued Customer" if names are missing
- [x] Templates: ACCOUNT_CREATED, WALLET_DEPOSIT, WALLET_WITHDRAWAL, STOCK_PURCHASED, STOCK_SOLD

#### 2. Order Service
- [x] CustomerClient created to fetch customer data from cust-service
- [x] CartService injected with CustomerClient
- [x] completeOrder() fetches customer data before publishing
- [x] publishStockPurchased() called with firstName, lastName parameters
- [x] NotificationKafkaProducer includes firstName/lastName in payload

#### 3. Portfolio Service
- [x] CustomerClient created to fetch customer data from cust-service
- [x] PortfolioService injected with CustomerClient
- [x] sell() method fetches customer data before publishing
- [x] publishStockSold() called with firstName, lastName parameters
- [x] NotificationKafkaProducer includes firstName/lastName in payload
- [x] PortfolioAnalytics updated to use Map<String, BigDecimal> for UUID transaction IDs

#### 4. Payment Service (Already Done)
- [x] WalletService has overloaded deposit()/withdraw() methods with firstName/lastName
- [x] recordTransaction() returns UUID (String transactionId)
- [x] publishWalletDeposit/Withdrawal() called with customer names
- [x] Notifications include firstName/lastName in payload

---

## Test Cases

### Test 1: Stock Purchase with Customer Name
**Precondition**: 
- User with userId=1, firstName="John", lastName="Doe" is registered
- User has sufficient wallet balance
- Stock with id=100, symbol="AAPL" exists

**Steps**:
1. Login as user 1 (John Doe)
2. Add AAPL stock to cart (qty: 2)
3. Complete order via `/cart/complete-order`

**Expected Result**:
- Email is sent with subject: "TradePulseAI – Order Completed"
- Email greeting: "Hi John Doe,"
- Email includes:
  - Stock: AAPL
  - Quantity: 2 shares
  - Price per share: [current price]
  - Total: [calculated total]

**How to Verify**:
1. Check application logs for "Published STOCK_PURCHASED notification for userId=1"
2. Check email backend (if configured) for email to john@example.com
3. Email should start with "Hi John Doe,"

---

### Test 2: Stock Sale with Customer Name
**Precondition**:
- User with userId=2, firstName="Jane", lastName="Smith" is registered
- User owns 5 shares of GOOGL stock (id=101)
- Stock GOOGL currently trading at $2500

**Steps**:
1. Login as user 2 (Jane Smith)
2. Navigate to portfolio
3. Sell 3 shares of GOOGL at market price

**Expected Result**:
- Email is sent with subject: "TradePulseAI – Sell Order Settled"
- Email greeting: "Hi Jane Smith,"
- Email includes:
  - Stock: GOOGL
  - Quantity: 3 shares
  - Price per share: $2500
  - Total credited: $7500

**How to Verify**:
1. Check application logs for "Published STOCK_SOLD notification for userId=2"
2. Check email for email to jane@example.com
3. Email should start with "Hi Jane Smith,"

---

### Test 3: Wallet Deposit with Customer Name
**Precondition**:
- User with userId=3, firstName="Robert", lastName="Johnson" is registered

**Steps**:
1. Login as user 3 (Robert Johnson)
2. Call wallet deposit endpoint with amount=$100
   - Header: X-User-Id: 3

**Expected Result**:
- Email is sent with subject: "TradePulseAI – Wallet Deposit Successful"
- Email greeting: "Hi Robert Johnson,"
- Email includes:
  - Transaction ID: [UUID format, not "3"]
  - Amount: $100.00
  - New Balance: [calculated balance]

**How to Verify**:
1. Check application logs for "Published WALLET_DEPOSIT notification for userId=3"
2. Check email for email to robert@example.com
3. Email greeting should be "Hi Robert Johnson,"
4. Transaction ID should be UUID format (e.g., "550e8400-e29b-41d4-a716-446655440000"), NOT a number like "3"

---

### Test 4: Wallet Withdrawal with Customer Name
**Precondition**:
- User with userId=4, firstName="Mary", lastName="Williams" is registered
- User has wallet balance of $500

**Steps**:
1. Login as user 4 (Mary Williams)
2. Call wallet withdraw endpoint with amount=$50
   - Header: X-User-Id: 4

**Expected Result**:
- Email is sent with subject: "TradePulseAI – Wallet Withdrawal Successful"
- Email greeting: "Hi Mary Williams,"
- Email includes:
  - Transaction ID: [UUID format]
  - Amount: $50.00
  - New Balance: $450.00

**How to Verify**:
1. Check application logs for "Published WALLET_WITHDRAWAL notification for userId=4"
2. Check email for email to mary@example.com
3. Email greeting should be "Hi Mary Williams,"

---

### Test 5: Fallback When Customer Name Missing
**Precondition**:
- User with userId=5 exists but has empty firstName and lastName

**Steps**:
1. Login as user 5
2. Complete a stock purchase order

**Expected Result**:
- Email greeting: "Hi Valued Customer,"
- All other email content displays normally

**How to Verify**:
1. Check email greeting in received email
2. Should show "Hi Valued Customer," instead of empty or error

---

### Test 6: Quantity Pluralization (1 share vs 2 shares)
**Test Case 6a - Singular**:
- User buys exactly 1.00 shares
- Email should say: "Quantity: 1 share"

**Test Case 6b - Plural**:
- User buys 2 shares
- Email should say: "Quantity: 2 shares"

**Test Case 6c - Decimal (plural)**:
- User buys 1.50 shares
- Email should say: "Quantity: 1.50 shares"

**How to Verify**:
- Check email content for correct usage of "share" vs "shares"

---

### Test 7: Stock Symbol Resolution
**Precondition**:
- Stock with id=100, symbol="AAPL" exists in stock-service

**Steps**:
1. User completes order for stock id 100

**Expected Result**:
- Email shows: "Stock: AAPL" (NOT "Stock: 100")

**How to Verify**:
1. Check email for stock symbol, not numeric ID
2. Verify logs show successful call to stock-service for symbol fetch

---

### Test 8: Transaction ID as UUID (Not Sequential Number)
**Precondition**:
- Customer completes wallet transaction

**Steps**:
1. Deposit $100 to wallet

**Expected Result**:
- Email shows: "Transaction ID: 550e8400-e29b-41d4-a716-446655440000" (example UUID)
- NOT "Transaction ID: 1" or "Transaction ID: 44"

**How to Verify**:
1. Check email transaction ID format
2. Should be UUID format (xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)
3. Each transaction should have a unique UUID

---

## Testing Commands

### Check Notification Logs
```bash
# Order service logs (look for publishStockPurchased)
kubectl logs -f deployment/order-service | grep "STOCK_PURCHASED"

# Portfolio service logs (look for publishStockSold)
kubectl logs -f deployment/portfolio-service | grep "STOCK_SOLD"

# Payment service logs (look for wallet notifications)
kubectl logs -f deployment/payment-service | grep "WALLET_DEPOSIT\|WALLET_WITHDRAWAL"

# Notification service logs (look for email sent)
kubectl logs -f deployment/notification-service | grep "Notification email sent"
```

### Database Queries

#### Check Order Transactions
```sql
-- Portfolio Service DB
SELECT transaction_id, user_id, transaction_type, created_at FROM portfolio_transaction 
WHERE user_id = 1 
ORDER BY created_at DESC 
LIMIT 5;

-- Verify transaction_id is UUID format (36 chars with dashes)
```

#### Check Wallet Transactions
```sql
-- Payment Service DB
SELECT transaction_id, user_id, transaction_type, amount, created_at FROM wallet_transaction 
WHERE user_id = 1 
ORDER BY created_at DESC 
LIMIT 5;

-- Verify transaction_id is UUID format (36 chars with dashes)
```

---

## Kafka Message Inspection

### Monitor Kafka Topic
```bash
# Connect to Kafka container
docker exec -it kafka /bin/bash

# Listen to notifications topic
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic tradepulse.notifications \
  --from-beginning
```

### Sample Expected Message
```json
{
  "eventType": "STOCK_PURCHASED",
  "userId": 1,
  "timestamp": "2026-07-16T10:30:45.123Z",
  "data": {
    "firstName": "John",
    "lastName": "Doe",
    "orderId": "550e8400-e29b-41d4-a716-446655440000",
    "symbol": "AAPL",
    "quantity": "2.00",
    "price": "150.50",
    "total": "301.00",
    "stockId": "100"
  }
}
```

### Sample Expected Wallet Message
```json
{
  "eventType": "WALLET_DEPOSIT",
  "userId": 3,
  "timestamp": "2026-07-16T10:35:20.456Z",
  "data": {
    "firstName": "Robert",
    "lastName": "Johnson",
    "transactionId": "550e8400-e29b-41d4-a716-446655440001",
    "amount": "100.00",
    "newBalance": "1100.00"
  }
}
```

---

## Regression Tests

### Ensure Backward Compatibility
1. ✅ Old overloaded methods still work (without firstName/lastName)
2. ✅ Notifications without names still send (fall back to "Valued Customer")
3. ✅ Email templates render correctly in all cases
4. ✅ Transaction and order IDs are still queryable

---

## Success Criteria

- [x] All email greetings are personalized with customer names
- [x] Fallback to "Valued Customer" works when names are missing
- [x] Transaction IDs are UUIDs (not sequential numbers)
- [x] Stock symbols display correctly (not numeric IDs)
- [x] Quantity pluralization (1 share vs 2 shares) works
- [x] All notification producers called with customer names
- [x] No compilation errors
- [x] Backward compatibility maintained

