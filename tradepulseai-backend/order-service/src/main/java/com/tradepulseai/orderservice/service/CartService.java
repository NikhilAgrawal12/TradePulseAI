package com.tradepulseai.orderservice.service;

import com.tradepulseai.orderservice.dto.cart.AddCartItemRequestDTO;
import com.tradepulseai.orderservice.dto.cart.CartItemResponseDTO;
import com.tradepulseai.orderservice.dto.order.CompleteOrderResponseDTO;
import com.tradepulseai.orderservice.dto.order.CompleteOrderItemRequestDTO;
import com.tradepulseai.orderservice.dto.order.CompleteOrderRequestDTO;
import com.tradepulseai.orderservice.dto.order.LockedOrderQuoteResponseDTO;
import com.tradepulseai.orderservice.grpc.OrderPaymentGrpcClient;
import com.tradepulseai.orderservice.grpc.PortfolioSyncGrpcClient;
import com.tradepulseai.orderservice.mapper.OrderItemMapper;
import com.tradepulseai.orderservice.mapper.OrderMapper;
import com.tradepulseai.orderservice.mapper.PortfolioOrderMapper;
import com.tradepulseai.orderservice.model.CartItem;
import com.tradepulseai.orderservice.model.CartItemId;
import com.tradepulseai.orderservice.model.TradeOrder;
import com.tradepulseai.orderservice.repository.CartItemRepository;
import io.grpc.StatusRuntimeException;
import order_payment.OrderPaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);
    private static final String PAYMENT_STATUS_COMPLETED = "COMPLETED";
    private static final int PRICE_LOCK_SECONDS = 15;

    private final CartItemRepository cartItemRepository;
    private final OrderPaymentGrpcClient orderPaymentGrpcClient;
    private final PortfolioSyncGrpcClient portfolioSyncGrpcClient;
    private final OrderHistoryService orderHistoryService;
    private final StockCatalogClient stockCatalogClient;

    public CartService(
            CartItemRepository cartItemRepository,
            OrderPaymentGrpcClient orderPaymentGrpcClient,
            PortfolioSyncGrpcClient portfolioSyncGrpcClient,
            OrderHistoryService orderHistoryService,
            StockCatalogClient stockCatalogClient
    ) {
        this.cartItemRepository = cartItemRepository;
        this.orderPaymentGrpcClient = orderPaymentGrpcClient;
        this.portfolioSyncGrpcClient = portfolioSyncGrpcClient;
        this.orderHistoryService = orderHistoryService;
        this.stockCatalogClient = stockCatalogClient;
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
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Cart is empty. Add items before completing order.");
        }

        CompleteOrderRequestDTO lockedRequest = buildLockedPriceRequest(request);

        TradeOrder savedOrder = orderHistoryService.saveCompletedOrder(
                OrderMapper.toModel(userId, PAYMENT_STATUS_COMPLETED, lockedRequest)
        );

        // Send payment record for the entire order total
        OrderPaymentResponse response;
        try {
            response = orderPaymentGrpcClient.completeOrderPayment(
                    savedOrder.getId(),
                    savedOrder.getTotal(),
                    userId
            );
        } catch (StatusRuntimeException exception) {
            throw new IllegalStateException("Payment failed for orderId: " + savedOrder.getId(), exception);
        }

        validateCompletedPaymentResponse(response, savedOrder.getId());

        var syncRequest = PortfolioOrderMapper.toSyncRequestFromOrder(savedOrder);
        if (syncRequest.getItems() == null || syncRequest.getItems().isEmpty()) {
            throw new IllegalStateException("Payment completed, but portfolio sync payload is empty for orderId: " + savedOrder.getId());
        }

        log.info("Dispatching portfolio sync for orderId={}, userId={}, items={}",
                savedOrder.getId(),
                userId,
                syncRequest.getItems().size());
        try {
            portfolioSyncGrpcClient.syncCompletedOrder(userId, syncRequest);
        } catch (Exception syncException) {
            compensatePaymentOnPortfolioSyncFailure(savedOrder.getId(), savedOrder.getTotal(), userId, syncException);
        }

        cartItemRepository.deleteByIdUserId(userId);
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
        LockedOrderQuoteResponseDTO response = new LockedOrderQuoteResponseDTO();
        response.setItems(quotedRequest.getItems());
        response.setSubtotal(quotedRequest.getSubtotal());
        response.setTotal(quotedRequest.getTotal());
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

    private CompleteOrderRequestDTO buildLockedPriceRequest(CompleteOrderRequestDTO request) {
        Map<Long, StockQuote> quotes = new LinkedHashMap<>();

        List<CompleteOrderItemRequestDTO> lockedItems = request.getItems().stream()
                .map(item -> {
                    Long stockId = parseStockId(item.getStockId());
                    BigDecimal quantity = scaleQuantity(item.getQuantity());
                    if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalArgumentException("Quantity must be greater than 0 for stockId: " + item.getStockId());
                    }

                    BigDecimal lockedPrice = OrderItemMapper.scaleMoney(item.getPrice());
                    if (lockedPrice.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalArgumentException("Locked price must be greater than 0 for stockId: " + item.getStockId());
                    }

                    // Resolve canonical symbol and verify stock exists via gRPC at submit time.
                    StockQuote quote = quotes.computeIfAbsent(stockId, stockCatalogClient::getRequiredStockQuote);
                    CompleteOrderItemRequestDTO locked = new CompleteOrderItemRequestDTO();
                    locked.setStockId(String.valueOf(stockId));
                    locked.setSymbol(quote.symbol());
                    locked.setPrice(lockedPrice);
                    locked.setQuantity(quantity);
                    return locked;
                })
                .toList();

        BigDecimal recalculatedSubtotal = OrderItemMapper.scaleMoney(
                lockedItems.stream()
                        .map(item -> item.getPrice().multiply(item.getQuantity()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );

        CompleteOrderRequestDTO lockedRequest = new CompleteOrderRequestDTO();
        lockedRequest.setItems(lockedItems);
        lockedRequest.setSubtotal(recalculatedSubtotal);
        lockedRequest.setTotal(recalculatedSubtotal);
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

        BigDecimal subtotal = OrderItemMapper.scaleMoney(
                quotedItems.stream()
                        .map(item -> item.getPrice().multiply(item.getQuantity()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );

        CompleteOrderRequestDTO quotedRequest = new CompleteOrderRequestDTO();
        quotedRequest.setItems(quotedItems);
        quotedRequest.setSubtotal(subtotal);
        quotedRequest.setTotal(subtotal);
        return quotedRequest;
    }

    private void validateCompletedPaymentResponse(OrderPaymentResponse response, String orderId) {
        if (response == null) {
            throw new IllegalStateException("Payment failed for orderId: " + orderId);
        }

        if (!PAYMENT_STATUS_COMPLETED.equalsIgnoreCase(response.getStatus())) {
            throw new IllegalStateException("Payment failed for orderId: " + orderId);
        }
    }

    private void compensatePaymentOnPortfolioSyncFailure(String orderId, BigDecimal totalAmount, Long userId, Exception syncException) {
        log.error("Portfolio sync failed for orderId={}, userId={}. Triggering payment compensation.",
                orderId, userId, syncException);

        try {
            var refundResponse = orderPaymentGrpcClient.refundOrderPayment(orderId, totalAmount, userId);
            throw new IllegalStateException("Portfolio sync failed after payment. Compensation applied with status: "
                    + refundResponse.getStatus(), syncException);
        } catch (StatusRuntimeException refundGrpcException) {
            throw new IllegalStateException(
                    "Portfolio sync failed after payment, and refund gRPC call failed for orderId: " + orderId,
                    refundGrpcException
            );
        }
    }

}
