package com.tradepulseai.orderservice.service;

import com.tradepulseai.orderservice.dto.AddCartItemRequestDTO;
import com.tradepulseai.orderservice.dto.CartItemResponseDTO;
import com.tradepulseai.orderservice.dto.CompleteOrderResponseDTO;
import com.tradepulseai.orderservice.grpc.OrderPaymentGrpcClient;
import com.tradepulseai.orderservice.mapper.CartItemMapper;
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
import java.util.List;

@Service
public class CartService {

    private static final String PAYMENT_STATUS_COMPLETED = "COMPLETED";
    private static final String UNKNOWN_ACCOUNT_ID = "unknown";

    private final CartItemRepository cartItemRepository;
    private final OrderPaymentGrpcClient orderPaymentGrpcClient;
    private final PortfolioSyncClient portfolioSyncClient;
    private final OrderHistoryService orderHistoryService;

    public CartService(
            CartItemRepository cartItemRepository,
            OrderPaymentGrpcClient orderPaymentGrpcClient,
            PortfolioSyncClient portfolioSyncClient,
            OrderHistoryService orderHistoryService
    ) {
        this.cartItemRepository = cartItemRepository;
        this.orderPaymentGrpcClient = orderPaymentGrpcClient;
        this.portfolioSyncClient = portfolioSyncClient;
        this.orderHistoryService = orderHistoryService;
    }

    @Transactional(readOnly = true)
    public List<CartItemResponseDTO> getCart(Long userId) {
        return cartItemRepository.findByIdUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(CartItemMapper::toDTO)
                .toList();
    }

    @Transactional
    public List<CartItemResponseDTO> addToCart(Long userId, AddCartItemRequestDTO request) {
        Long stockId = parseStockId(request.getStockId());

        CartItem cartItem = cartItemRepository.findByIdUserIdAndIdStockId(userId, stockId)
                .map(existing -> {
                    existing.setSymbol(request.getSymbol());
                    existing.setPrice(request.getPrice());
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
    public CompleteOrderResponseDTO completeOrder(Long userId, String userEmail) {
        List<CartItem> cartItems = cartItemRepository.findByIdUserIdOrderByUpdatedAtDesc(userId);
        if (cartItems.isEmpty()) {
            throw new IllegalArgumentException("Cart is empty. Add items before completing order.");
        }

        TradeOrder savedOrder = orderHistoryService.saveCompletedOrder(
                OrderMapper.toModel(userId, PAYMENT_STATUS_COMPLETED, cartItems)
        );

        String accountId = UNKNOWN_ACCOUNT_ID;
        for (CartItem cartItem : cartItems) {
            OrderPaymentResponse response;
            try {
                response = orderPaymentGrpcClient.completePayment(savedOrder.getId(), cartItem, userEmail);
            } catch (StatusRuntimeException exception) {
                throw new IllegalStateException("Payment failed for stockId: " + cartItem.getStockId(), exception);
            }

            validateCompletedPaymentResponse(response, String.valueOf(cartItem.getStockId()));
            if (UNKNOWN_ACCOUNT_ID.equals(accountId) && !response.getAccountId().isBlank()) {
                accountId = response.getAccountId();
            }
        }

        portfolioSyncClient.syncCompletedOrder(userEmail, PortfolioOrderMapper.toSyncRequest(cartItems));


        cartItemRepository.deleteByIdUserId(userId);
        return new CompleteOrderResponseDTO(savedOrder.getId(), accountId, PAYMENT_STATUS_COMPLETED);
    }

    private CartItem newCartItem(Long userId, Long stockId, AddCartItemRequestDTO request) {
        CartItem cartItem = new CartItem();
        CartItemId id = new CartItemId();
        id.setUserId(userId);
        id.setStockId(stockId);
        cartItem.setId(id);
        cartItem.setSymbol(request.getSymbol());
        cartItem.setPrice(request.getPrice());
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
        if (quantity == null) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return quantity.setScale(4, RoundingMode.HALF_UP);
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
