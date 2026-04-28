package com.tradepulseai.orderservice.service;

import com.tradepulseai.orderservice.client.AuthServiceClient;
import com.tradepulseai.orderservice.dto.cart.AddCartItemRequestDTO;
import com.tradepulseai.orderservice.dto.cart.CartItemResponseDTO;
import com.tradepulseai.orderservice.dto.order.CompleteOrderResponseDTO;
import com.tradepulseai.orderservice.grpc.OrderPaymentGrpcClient;
import com.tradepulseai.orderservice.mapper.OrderItemMapper;
import com.tradepulseai.orderservice.mapper.OrderMapper;
import com.tradepulseai.orderservice.mapper.PortfolioOrderMapper;
import com.tradepulseai.orderservice.model.CartItem;
import com.tradepulseai.orderservice.model.CartItemId;
import com.tradepulseai.orderservice.model.TradeOrder;
import com.tradepulseai.orderservice.repository.CartItemRepository;
import io.grpc.StatusRuntimeException;
import order_payment.OrderPaymentResponse;
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

    private static final String PAYMENT_STATUS_COMPLETED = "COMPLETED";

    private final CartItemRepository cartItemRepository;
    private final OrderPaymentGrpcClient orderPaymentGrpcClient;
    private final PortfolioSyncClient portfolioSyncClient;
    private final OrderHistoryService orderHistoryService;
    private final StockCatalogClient stockCatalogClient;
    private final AuthServiceClient authServiceClient;

    public CartService(
            CartItemRepository cartItemRepository,
            OrderPaymentGrpcClient orderPaymentGrpcClient,
            PortfolioSyncClient portfolioSyncClient,
            OrderHistoryService orderHistoryService,
            StockCatalogClient stockCatalogClient,
            AuthServiceClient authServiceClient
    ) {
        this.cartItemRepository = cartItemRepository;
        this.orderPaymentGrpcClient = orderPaymentGrpcClient;
        this.portfolioSyncClient = portfolioSyncClient;
        this.orderHistoryService = orderHistoryService;
        this.stockCatalogClient = stockCatalogClient;
        this.authServiceClient = authServiceClient;
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
    public CompleteOrderResponseDTO completeOrder(Long userId) {
        List<CartItem> cartItems = cartItemRepository.findByIdUserIdOrderByUpdatedAtDesc(userId);
        if (cartItems.isEmpty()) {
            throw new IllegalArgumentException("Cart is empty. Add items before completing order.");
        }

        Map<Long, StockQuote> stockQuotes = loadStockQuotes(cartItems);

        TradeOrder savedOrder = orderHistoryService.saveCompletedOrder(
                OrderMapper.toModel(userId, PAYMENT_STATUS_COMPLETED, cartItems, stockQuotes)
        );

        // Send ONE payment record for the entire order total (subtotal + tax)
        String userEmail = authServiceClient.getUserById(userId).email();
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

        validateCompletedPaymentResponse(response, String.valueOf(savedOrder.getId()));

        portfolioSyncClient.syncCompletedOrder(userId, PortfolioOrderMapper.toSyncRequest(cartItems, stockQuotes));

        cartItemRepository.deleteByIdUserId(userId);
        return new CompleteOrderResponseDTO(savedOrder.getId(), response.getAccountId(), PAYMENT_STATUS_COMPLETED);
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

    private Map<Long, StockQuote> loadStockQuotes(List<CartItem> cartItems) {
        Map<Long, StockQuote> quotes = new LinkedHashMap<>();
        for (CartItem cartItem : cartItems) {
            quotes.computeIfAbsent(cartItem.getStockId(), stockCatalogClient::getStockQuote);
        }
        return quotes;
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
                .setScale(4, RoundingMode.HALF_UP);
    }

    private void validateCompletedPaymentResponse(OrderPaymentResponse response, String stockId) {
        if (response == null) {
            throw new IllegalStateException("Payment failed for stockId: " + stockId);
        }

        if (!PAYMENT_STATUS_COMPLETED.equalsIgnoreCase(response.getStatus())) {
            throw new IllegalStateException("Payment failed for stockId: " + stockId);
        }
    }
}
