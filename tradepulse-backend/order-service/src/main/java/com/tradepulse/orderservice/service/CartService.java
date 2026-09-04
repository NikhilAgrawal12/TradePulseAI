package com.tradepulse.orderservice.service;

import com.tradepulse.orderservice.dto.cart.AddCartItemRequestDTO;
import com.tradepulse.orderservice.dto.cart.CartItemResponseDTO;
import com.tradepulse.orderservice.dto.order.CompleteOrderResponseDTO;
import com.tradepulse.orderservice.dto.order.CompleteOrderItemRequestDTO;
import com.tradepulse.orderservice.dto.order.CompleteOrderRequestDTO;
import com.tradepulse.orderservice.dto.order.LockedOrderQuoteResponseDTO;
import com.tradepulse.orderservice.grpc.OrderPaymentGrpcClient;
import com.tradepulse.orderservice.mapper.OrderItemMapper;
import com.tradepulse.orderservice.mapper.OrderMapper;
import com.tradepulse.orderservice.model.CartItem;
import com.tradepulse.orderservice.model.CartItemId;
import com.tradepulse.orderservice.model.QuoteLock;
import com.tradepulse.orderservice.model.QuoteLockItem;
import com.tradepulse.orderservice.model.TradeOrder;
import com.tradepulse.orderservice.repository.CartItemRepository;
import com.tradepulse.orderservice.repository.QuoteLockRepository;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import order_payment.OrderPaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);
    private static final String PAYMENT_STATUS_COMPLETED = "COMPLETED";
    private static final String PAYMENT_REFERENCE_PREFIX = "checkout-";
    private static final int PRICE_LOCK_SECONDS = 15;
    private static final String QUOTE_LOCK_STATUS_LOCKED = "LOCKED";
    private static final String QUOTE_LOCK_STATUS_USED = "USED";
    private static final String QUOTE_LOCK_STATUS_EXPIRED = "EXPIRED";

    private final CartItemRepository cartItemRepository;
    private final QuoteLockRepository quoteLockRepository;
    private final OrderPaymentGrpcClient orderPaymentGrpcClient;
    private final OrderHistoryService orderHistoryService;
    private final StockCatalogClient stockCatalogClient;
    private final OutboxEventEnqueuer outboxEventEnqueuer;

    public CartService(
            CartItemRepository cartItemRepository,
            QuoteLockRepository quoteLockRepository,
            OrderPaymentGrpcClient orderPaymentGrpcClient,
            OrderHistoryService orderHistoryService,
            StockCatalogClient stockCatalogClient,
            OutboxEventEnqueuer outboxEventEnqueuer
    ) {
        this.cartItemRepository = cartItemRepository;
        this.quoteLockRepository = quoteLockRepository;
        this.orderPaymentGrpcClient = orderPaymentGrpcClient;
        this.orderHistoryService = orderHistoryService;
        this.stockCatalogClient = stockCatalogClient;
        this.outboxEventEnqueuer = outboxEventEnqueuer;
    }

    @Transactional(readOnly = true)
    public List<CartItemResponseDTO> getCart(Long userId) {
        List<CartItem> cartItems = cartItemRepository.findByIdUserIdOrderByUpdatedAtDesc(userId);
        Map<Long, StockQuote> stockQuotes = loadStockQuotes(cartItems);
        return cartItems
                .stream()
                .map(item -> toCartResponse(item, stockQuotes.get(item.getStockId())))
                .toList();
    }

    @Transactional
    public List<CartItemResponseDTO> addToCart(Long userId, AddCartItemRequestDTO request) {
        Long stockId = parseStockId(request.getStockId());

        CartItem cartItem = cartItemRepository.findByIdUserIdAndIdStockId(userId, stockId)
                .map(existing -> {
                    existing.setQuantity(scaleQuantity(existing.getQuantity().add(request.getQuantity())));
                    return existing;
                })
                .orElseGet(() -> newCartItem(userId, stockId, request));

        cartItemRepository.save(cartItem);
        return getCart(userId);
    }

    @Transactional
    public List<CartItemResponseDTO> updateQuantity(Long userId, String stockId, BigDecimal quantity) {
        Long parsedStockId = parseStockId(stockId);
        CartItem cartItem = cartItemRepository.findByIdUserIdAndIdStockId(userId, parsedStockId)
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found for stockId: " + stockId));

        cartItem.setQuantity(scaleQuantity(quantity));
        cartItemRepository.save(cartItem);
        return getCart(userId);
    }

    @Transactional
    public List<CartItemResponseDTO> removeFromCart(Long userId, String stockId) {
        cartItemRepository.deleteByIdUserIdAndIdStockId(userId, parseStockId(stockId));
        return getCart(userId);
    }

    @Transactional
    public List<CartItemResponseDTO> clearCart(Long userId) {
        cartItemRepository.deleteByIdUserId(userId);
        return List.of();
    }

    @Transactional
    public CompleteOrderResponseDTO completeOrder(Long userId, CompleteOrderRequestDTO request) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("Valid userId is required.");
        }
        if (request == null || request.getQuoteLockId() == null || request.getQuoteLockId().isBlank()) {
            throw new IllegalArgumentException("A valid locked quote is required before completing order.");
        }

        QuoteLock quoteLock = quoteLockRepository.findByIdAndUserId(request.getQuoteLockId().trim(), userId)
                .orElseThrow(() -> new IllegalArgumentException("Locked quote not found. Please review your cart and try again."));

        validateQuoteLockForPayment(quoteLock);

        CompleteOrderRequestDTO lockedRequest = toLockedOrderRequest(quoteLock);
        String paymentReference = PAYMENT_REFERENCE_PREFIX + UUID.randomUUID();

        OrderPaymentResponse response;
        try {
            response = orderPaymentGrpcClient.completeOrderPayment(
                    paymentReference,
                    lockedRequest.getTotal(),
                    userId
            );
        } catch (StatusRuntimeException exception) {
            throw translatePaymentException(paymentReference, exception);
        }

        validateCompletedPaymentResponse(response, paymentReference);

        TradeOrder savedOrder;
        try {
            savedOrder = orderHistoryService.saveCompletedOrder(
                    OrderMapper.toModel(userId, PAYMENT_STATUS_COMPLETED, lockedRequest)
            );
        } catch (Exception persistException) {
            compensatePaymentOnOrderPersistFailure(paymentReference, lockedRequest.getTotal(), userId, persistException);
            throw new IllegalStateException("Order persistence failed after successful payment.", persistException);
        }

        quoteLock.setStatus(QUOTE_LOCK_STATUS_USED);
        quoteLockRepository.save(quoteLock);

        cartItemRepository.deleteByIdUserId(userId);

        // Write both outbox entries inside this transaction – the relay will publish them after commit.
        outboxEventEnqueuer.enqueue(savedOrder, lockedRequest);

        return new CompleteOrderResponseDTO(savedOrder.getId(), response.getAccountId(), PAYMENT_STATUS_COMPLETED);
    }

    @Transactional(readOnly = true)
    public LockedOrderQuoteResponseDTO lockOrderQuote(Long userId, CompleteOrderRequestDTO request) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("Valid userId is required.");
        }
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("At least one item is required to lock prices.");
        }

        CompleteOrderRequestDTO quotedRequest = buildFreshQuotedRequest(request);
        QuoteLock savedQuoteLock = quoteLockRepository.save(buildQuoteLock(userId, quotedRequest));

        LockedOrderQuoteResponseDTO response = new LockedOrderQuoteResponseDTO();
        response.setItems(quotedRequest.getItems());
        response.setTotal(quotedRequest.getTotal());
        response.setQuoteLockId(savedQuoteLock.getId());
        response.setLockSeconds(PRICE_LOCK_SECONDS);
        return response;
    }

    private Map<Long, StockQuote> loadStockQuotes(List<CartItem> cartItems) {
        Map<Long, StockQuote> quotes = new LinkedHashMap<>();
        for (CartItem cartItem : cartItems) {
            quotes.computeIfAbsent(cartItem.getStockId(), stockCatalogClient::getStockQuote);
        }
        return quotes;
    }

    private CartItemResponseDTO toCartResponse(CartItem cartItem, StockQuote stockQuote) {
        CartItemResponseDTO response = new CartItemResponseDTO();
        response.setUserId(cartItem.getUserId());
        response.setStockId(String.valueOf(cartItem.getStockId()));
        response.setSymbol(stockQuote.symbol());
        response.setPrice(stockQuote.unitPrice());
        BigDecimal quantity = scaleQuantity(cartItem.getQuantity());
        response.setQuantity(quantity);
        response.setLineTotal(OrderItemMapper.scaleMoney(stockQuote.unitPrice().multiply(quantity)));
        return response;
    }


    private CartItem newCartItem(Long userId, Long stockId, AddCartItemRequestDTO request) {
        CartItem cartItem = new CartItem();
        CartItemId id = new CartItemId();
        id.setUserId(userId);
        id.setStockId(stockId);
        cartItem.setId(id);
        cartItem.setQuantity(scaleQuantity(request.getQuantity()));
        return cartItem;
    }

    private Long parseStockId(String stockId) {
        try {
            return Long.parseLong(stockId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid stockId format: " + stockId);
        }
    }

    private BigDecimal scaleQuantity(BigDecimal quantity) {
        return Objects.requireNonNullElse(quantity, BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }


    private QuoteLock buildQuoteLock(Long userId, CompleteOrderRequestDTO quotedRequest) {
        QuoteLock quoteLock = new QuoteLock();
        quoteLock.setUserId(userId);
        quoteLock.setTotal(OrderItemMapper.scaleMoney(quotedRequest.getTotal()));
        quoteLock.setStatus(QUOTE_LOCK_STATUS_LOCKED);
        quoteLock.setExpiresAt(Instant.now().plusSeconds(PRICE_LOCK_SECONDS));
        quoteLock.setItems(
                quotedRequest.getItems().stream()
                        .map(item -> toQuoteLockItem(quoteLock, item))
                        .toList()
        );
        return quoteLock;
    }

    private QuoteLockItem toQuoteLockItem(QuoteLock quoteLock, CompleteOrderItemRequestDTO itemRequest) {
        QuoteLockItem item = new QuoteLockItem();
        item.setQuoteLock(quoteLock);
        item.setStockId(itemRequest.getStockId());
        item.setSymbol(itemRequest.getSymbol());
        item.setPrice(OrderItemMapper.scaleMoney(itemRequest.getPrice()));
        item.setQuantity(scaleQuantity(itemRequest.getQuantity()));
        return item;
    }

    private void validateQuoteLockForPayment(QuoteLock quoteLock) {
        if (!QUOTE_LOCK_STATUS_LOCKED.equalsIgnoreCase(quoteLock.getStatus())) {
            throw new IllegalStateException("This locked quote can no longer be used. Please review your cart and lock prices again.");
        }
        if (quoteLock.getExpiresAt() == null || !quoteLock.getExpiresAt().isAfter(Instant.now())) {
            quoteLock.setStatus(QUOTE_LOCK_STATUS_EXPIRED);
            quoteLockRepository.save(quoteLock);
            throw new IllegalStateException("Price lock expired. Please review your cart and try again.");
        }
        if (quoteLock.getItems() == null || quoteLock.getItems().isEmpty()) {
            throw new IllegalStateException("Locked quote is empty. Please review your cart and try again.");
        }
    }

    private CompleteOrderRequestDTO toLockedOrderRequest(QuoteLock quoteLock) {
        CompleteOrderRequestDTO lockedRequest = new CompleteOrderRequestDTO();
        lockedRequest.setQuoteLockId(quoteLock.getId());
        lockedRequest.setItems(
                quoteLock.getItems().stream()
                        .map(item -> {
                            CompleteOrderItemRequestDTO dto = new CompleteOrderItemRequestDTO();
                            dto.setStockId(item.getStockId());
                            dto.setSymbol(item.getSymbol());
                            dto.setPrice(OrderItemMapper.scaleMoney(item.getPrice()));
                            dto.setQuantity(scaleQuantity(item.getQuantity()));
                            return dto;
                        })
                        .toList()
        );
        lockedRequest.setTotal(OrderItemMapper.scaleMoney(quoteLock.getTotal()));
        return lockedRequest;
    }

    private CompleteOrderRequestDTO buildFreshQuotedRequest(CompleteOrderRequestDTO request) {
        Map<Long, StockQuote> quotes = new LinkedHashMap<>();

        List<CompleteOrderItemRequestDTO> quotedItems = request.getItems().stream()
                .map(item -> {
                    Long stockId = parseStockId(item.getStockId());
                    BigDecimal quantity = scaleQuantity(item.getQuantity());
                    if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalArgumentException("Quantity must be greater than 0 for stockId: " + item.getStockId());
                    }

                    StockQuote quote = quotes.computeIfAbsent(stockId, stockCatalogClient::getRequiredStockQuote);
                    CompleteOrderItemRequestDTO quoted = new CompleteOrderItemRequestDTO();
                    quoted.setStockId(String.valueOf(stockId));
                    quoted.setSymbol(quote.symbol());
                    quoted.setPrice(OrderItemMapper.scaleMoney(quote.unitPrice()));
                    quoted.setQuantity(quantity);
                    return quoted;
                })
                .toList();

        BigDecimal total = OrderItemMapper.scaleMoney(
                quotedItems.stream()
                        .map(item -> item.getPrice().multiply(item.getQuantity()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );

        CompleteOrderRequestDTO quotedRequest = new CompleteOrderRequestDTO();
        quotedRequest.setItems(quotedItems);
        quotedRequest.setTotal(total);
        return quotedRequest;
    }

    private void validateCompletedPaymentResponse(OrderPaymentResponse response, String paymentReference) {
        if (response == null) {
            throw new IllegalStateException("Payment failed for payment reference: " + paymentReference);
        }

        if (!PAYMENT_STATUS_COMPLETED.equalsIgnoreCase(response.getStatus())) {
            throw new IllegalStateException("Payment failed for payment reference: " + paymentReference);
        }
    }

    private void compensatePaymentOnOrderPersistFailure(String paymentReference, BigDecimal totalAmount, Long userId, Exception persistException) {
        log.error("Order persistence failed after payment for paymentReference={}, userId={}. Triggering refund.",
                paymentReference, userId, persistException);

        try {
            var refundResponse = orderPaymentGrpcClient.refundOrderPayment(paymentReference, totalAmount, userId);
            throw new IllegalStateException("Order persistence failed after payment. Compensation applied with status: "
                    + refundResponse.getStatus(), persistException);
        } catch (StatusRuntimeException refundGrpcException) {
            throw new IllegalStateException(
                    "Order persistence failed after payment, and refund gRPC call failed for payment reference: " + paymentReference,
                    refundGrpcException
            );
        }
    }

    private IllegalStateException translatePaymentException(String paymentReference, StatusRuntimeException exception) {
        Status.Code statusCode = exception.getStatus().getCode();
        if (statusCode == Status.Code.DEADLINE_EXCEEDED) {
            return new IllegalStateException("Payment service timed out while completing your order. Please try again.", exception);
        }
        if (statusCode == Status.Code.UNAVAILABLE) {
            return new IllegalStateException("Payment service is currently unavailable. Please try again in a moment.", exception);
        }
        return new IllegalStateException("Payment failed for payment reference: " + paymentReference, exception);
    }


}
