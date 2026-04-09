# Mapper Pattern Implementation Summary

## Overview
All backend services now follow a centralized mapper pattern for DTO ↔ Model conversions, following the `cust-service` example.

## Implementation Across Services

### 1. **Cust-Service** (Reference Pattern)
```
mapper/
├── CustomerMapper.java
    ├── toDTO(Customer) → CustomerResponseDTO
    └── toModel(CustomerRequestDTO) → Customer
```
- **Usage:** CustomerService uses `CustomerMapper.toDTO()` and `CustomerMapper.toModel()`
- **Pattern:** Static utility methods, stateless

---

### 2. **Order-Service** (New)
```
mapper/
├── CartItemMapper.java
    ├── toDTO(CartItem) → CartItemResponseDTO
    └── toModel(CartItemResponseDTO) → CartItem
```
- **File:** `tradepulseai-backend/order-service/src/main/java/com/tradepulseai/orderservice/mapper/CartItemMapper.java`
- **Usage:** CartService uses `CartItemMapper::toDTO` in stream().map()
- **Location:** Centralized in `mapper/` package
- **Service Code:**
```java
return cartItemRepository.findByUserEmailOrderByUpdatedAtDesc(userEmail)
        .stream()
        .map(CartItemMapper::toDTO)  // Uses mapper
        .toList();
```

---

### 3. **Payment-Service** (New)
Two mapper classes for two conversion flows:

#### a. PaymentMapper (Entity Creation from gRPC Input)
```
mapper/
├── PaymentMapper.java
    └── toModel(cartItemId, userEmail, stockId, symbol, price, quantity) → Payment
```
- **File:** `tradepulseai-backend/payment-service/src/main/java/com/tradepulseai/paymentservice/mapper/PaymentMapper.java`
- **Usage:** PaymentProcessingService uses `PaymentMapper.toModel(...)` before saving
- **Service Code:**
```java
Payment payment = PaymentMapper.toModel(cartItemId, userEmail, stockId, symbol, price, quantity);
Payment savedPayment = paymentRepository.save(payment);
```

#### b. PaymentMapperDTO (Entity to Response DTO)
```
mapper/
├── PaymentMapperDTO.java
    └── toDTO(Payment) → PaymentResponseDTO
```
- **File:** `tradepulseai-backend/payment-service/src/main/java/com/tradepulseai/paymentservice/mapper/PaymentMapperDTO.java`
- **Usage:** PaymentController uses `PaymentMapperDTO::toDTO` for REST responses
- **Controller Code:**
```java
return ResponseEntity.ok(
    paymentRepository.findByUserEmail(userEmail.toLowerCase())
            .stream()
            .map(PaymentMapperDTO::toDTO)  // Uses mapper
            .toList()
);
```

#### c. PaymentResponseDTO (New DTO for REST API)
```
dto/
├── PaymentResponseDTO.java
    ├── id (UUID)
    ├── cartItemId (String)
    ├── userEmail (String)
    ├── stockId (String)
    ├── symbol (String)
    ├── price (BigDecimal)
    ├── quantity (int)
    ├── totalAmount (BigDecimal)
    ├── status (String)
    └── createdAt (Instant)
```

---

### 4. **Stock-Service** (Reference Implementation)
```
mapper/
├── StockMapper.java
    └── toDTO(Stock) → StockResponseDTO
```
- **Pattern:** Same as cust-service
- **Usage:** StockService uses `StockMapper::toDTO`

---

## Benefits of This Pattern

1. **Separation of Concerns**
   - Conversion logic isolated from business logic
   - Easy to test mappers independently

2. **Consistency**
   - All services follow same structure
   - Predictable file locations (`mapper/` package)

3. **Maintainability**
   - Single place to change conversion rules
   - Reduces boilerplate in services

4. **Reusability**
   - Mappers can be used in multiple layers (service, controller, gRPC)
   - No duplicate conversion code

5. **Scalability**
   - Easy to add new mapper methods
   - Clear naming convention: `toDTO()`, `toModel()`, `toResponseDTO()`

---

## File Organization Template (For New Services)

```
src/main/java/com/tradepulseai/{service}/
├── controller/
│   └── {Entity}Controller.java
├── dto/
│   ├── {Entity}RequestDTO.java
│   └── {Entity}ResponseDTO.java
├── mapper/
│   ├── {Entity}Mapper.java           (Model ↔ DTO)
│   └── {Entity}MapperDTO.java        (Optional: additional conversions)
├── model/
│   └── {Entity}.java
├── repository/
│   └── {Entity}Repository.java
├── service/
│   └── {Entity}Service.java
└── {ServiceName}Application.java
```

---

## Conversion Flow by Service

### Cust-Service Flow
```
POST /customers/register
    ↓
CustomerController receives request
    ↓
CustomerService.createCustomer(CustomerRequestDTO)
    ↓
CustomerMapper.toModel(CustomerRequestDTO) → Customer
    ↓
customerRepository.save(Customer)
    ↓
CustomerMapper.toDTO(savedCustomer) → CustomerResponseDTO
    ↓
Return CustomerResponseDTO to client
```

### Order-Service Flow
```
GET /cart
    ↓
CartController.getCart(userEmail)
    ↓
CartService.getCart(userEmail)
    ↓
cartItemRepository.findByUserEmail()
    ↓
CartItemMapper::toDTO (in stream.map)
    ↓
Return List<CartItemResponseDTO>
```

### Payment-Service gRPC Flow
```
OrderPaymentGrpcService.completePayment(OrderPaymentRequest)
    ↓
PaymentProcessingService.processPayment(...)
    ↓
PaymentMapper.toModel(...) → Payment entity
    ↓
paymentRepository.save(Payment)
    ↓
Return saved Payment to gRPC handler
    ↓
gRPC response sent back to order-service
```

### Payment-Service REST Flow
```
GET /payments/user/{userEmail}
    ↓
PaymentController.getPaymentsByUserEmail()
    ↓
paymentRepository.findByUserEmail()
    ↓
PaymentMapperDTO::toDTO (in stream.map)
    ↓
Return List<PaymentResponseDTO>
```

---

## Commit Reference
```
Commit: 5750ef9
Message: Refactor services to use centralized mapper classes for DTO/Model conversions

Changes:
+ CartItemMapper.java (order-service)
+ PaymentMapper.java (payment-service)
+ PaymentMapperDTO.java (payment-service)
+ PaymentResponseDTO.java (payment-service)
~ CartService.java (updated to use CartItemMapper)
~ PaymentProcessingService.java (updated to use PaymentMapper)
~ PaymentController.java (updated to use PaymentMapperDTO)
```


