package com.tradepulseai.orderservice.service;

import com.tradepulseai.orderservice.dto.order.OrderResponseDTO;
import com.tradepulseai.orderservice.mapper.OrderMapper;
import com.tradepulseai.orderservice.model.TradeOrder;
import com.tradepulseai.orderservice.repository.TradeOrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OrderHistoryService {

    private static final int ORDER_NUMBER_MIN = 1_000_000;
    private static final int ORDER_NUMBER_MAX = 9_999_999;
    private static final int ORDER_NUMBER_GENERATION_ATTEMPTS = 200;

    private final TradeOrderRepository tradeOrderRepository;
    private final StockCatalogClient stockCatalogClient;

    public OrderHistoryService(TradeOrderRepository tradeOrderRepository, StockCatalogClient stockCatalogClient) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.stockCatalogClient = stockCatalogClient;
    }

    @Transactional
    public TradeOrder saveCompletedOrder(TradeOrder order) {
        if (order.getOrderNumber() == null) {
            order.setOrderNumber(generateUniqueOrderNumber());
        }
        return tradeOrderRepository.save(order);
    }

    @Transactional
    public List<OrderResponseDTO> getOrders(Long userId) {
        List<TradeOrder> userOrders = tradeOrderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        userOrders.forEach(this::ensureOrderNumber);

        List<OrderResponseDTO> orders = userOrders
                .stream()
                .map(OrderMapper::toDTO)
                .toList();

        Map<Long, StockQuote> quotes = new LinkedHashMap<>();
        for (OrderResponseDTO order : orders) {
            order.getItems().forEach(item -> {
                Long stockId = Long.parseLong(item.getStockId());
                StockQuote quote = quotes.computeIfAbsent(stockId, stockCatalogClient::getStockQuote);
                item.setSymbol(quote.symbol());
            });
        }

        return orders;
    }

    @Transactional
    public Page<OrderResponseDTO> getOrdersPage(Long userId, int page, int size) {
        Page<TradeOrder> userOrders = tradeOrderRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        userOrders.forEach(this::ensureOrderNumber);

        Page<OrderResponseDTO> ordersPage = userOrders.map(OrderMapper::toDTO);

        Map<Long, StockQuote> quotes = new LinkedHashMap<>();
        ordersPage.getContent().forEach(order -> order.getItems().forEach(item -> {
            Long stockId = Long.parseLong(item.getStockId());
            StockQuote quote = quotes.computeIfAbsent(stockId, stockCatalogClient::getStockQuote);
            item.setSymbol(quote.symbol());
        }));

        return ordersPage;
    }

    private void ensureOrderNumber(TradeOrder order) {
        if (order.getOrderNumber() != null) {
            return;
        }
        order.setOrderNumber(generateUniqueOrderNumber());
        tradeOrderRepository.save(order);
    }

    private Integer generateUniqueOrderNumber() {
        for (int attempt = 0; attempt < ORDER_NUMBER_GENERATION_ATTEMPTS; attempt++) {
            int candidate = ThreadLocalRandom.current().nextInt(ORDER_NUMBER_MIN, ORDER_NUMBER_MAX + 1);
            if (!tradeOrderRepository.existsByOrderNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to allocate a unique 7-digit order number.");
    }
}

