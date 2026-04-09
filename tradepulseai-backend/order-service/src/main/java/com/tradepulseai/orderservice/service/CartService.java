package com.tradepulseai.orderservice.service;

import com.tradepulseai.orderservice.dto.AddCartItemRequestDTO;
import com.tradepulseai.orderservice.dto.CartItemResponseDTO;
import com.tradepulseai.orderservice.dto.CompleteOrderResponseDTO;
import com.tradepulseai.orderservice.grpc.OrderPaymentGrpcClient;
import com.tradepulseai.orderservice.mapper.CartItemMapper;
import com.tradepulseai.orderservice.mapper.OrderMapper;
import com.tradepulseai.orderservice.mapper.PortfolioOrderMapper;
import com.tradepulseai.orderservice.model.CartItem;
import com.tradepulseai.orderservice.model.TradeOrder;
import com.tradepulseai.orderservice.repository.CartItemRepository;
import io.grpc.StatusRuntimeException;
import order_payment.OrderPaymentResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public List<CartItemResponseDTO> getCart(String userEmail) {
        return cartItemRepository.findByUserEmailOrderByUpdatedAtDesc(userEmail)
                .stream()
                .map(CartItemMapper::toDTO)
                .toList();
    }

    @Transactional
    public List<CartItemResponseDTO> addToCart(String userEmail, AddCartItemRequestDTO request) {
        CartItem cartItem = cartItemRepository.findByUserEmailAndStockId(userEmail, request.getStockId())
                .orElseGet(CartItem::new);

        if (cartItem.getId() == null) {
            cartItem.setUserEmail(userEmail);
            cartItem.setStockId(request.getStockId());
            cartItem.setSymbol(request.getSymbol());
            cartItem.setPrice(request.getPrice());
            cartItem.setQuantity(request.getQuantity());
        } else {
            cartItem.setSymbol(request.getSymbol());
            cartItem.setPrice(request.getPrice());
            cartItem.setQuantity(cartItem.getQuantity() + request.getQuantity());
        }

        cartItemRepository.save(cartItem);
        return getCart(userEmail);
    }

    @Transactional
    public List<CartItemResponseDTO> updateQuantity(String userEmail, String stockId, int quantity) {
        CartItem cartItem = cartItemRepository.findByUserEmailAndStockId(userEmail, stockId)
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found for stockId: " + stockId));

        cartItem.setQuantity(quantity);
        cartItemRepository.save(cartItem);
        return getCart(userEmail);
    }

    @Transactional
    public List<CartItemResponseDTO> removeFromCart(String userEmail, String stockId) {
        cartItemRepository.deleteByUserEmailAndStockId(userEmail, stockId);
        return getCart(userEmail);
    }

    @Transactional
    public List<CartItemResponseDTO> clearCart(String userEmail) {
        cartItemRepository.deleteByUserEmail(userEmail);
        return List.of();
    }

    @Transactional
    public CompleteOrderResponseDTO completeOrder(String userEmail) {
        List<CartItem> cartItems = cartItemRepository.findByUserEmailOrderByUpdatedAtDesc(userEmail);
        if (cartItems.isEmpty()) {
            throw new IllegalArgumentException("Cart is empty. Add items before completing order.");
        }

        String accountId = UNKNOWN_ACCOUNT_ID;
        for (CartItem cartItem : cartItems) {
            OrderPaymentResponse response;
            try {
                response = orderPaymentGrpcClient.completePayment(cartItem);
            } catch (StatusRuntimeException exception) {
                throw new IllegalStateException("Payment failed for stockId: " + cartItem.getStockId(), exception);
            }

            validateCompletedPaymentResponse(response, cartItem.getStockId());
            if (UNKNOWN_ACCOUNT_ID.equals(accountId) && !response.getAccountId().isBlank()) {
                accountId = response.getAccountId();
            }
        }

        portfolioSyncClient.syncCompletedOrder(userEmail, PortfolioOrderMapper.toSyncRequest(cartItems));

        TradeOrder savedOrder = orderHistoryService.saveCompletedOrder(
                OrderMapper.toModel(userEmail, accountId, PAYMENT_STATUS_COMPLETED, cartItems)
        );

        cartItemRepository.deleteByUserEmail(userEmail);
        return new CompleteOrderResponseDTO(savedOrder.getId(), accountId, PAYMENT_STATUS_COMPLETED);
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
