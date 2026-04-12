package com.tradepulseai.orderservice.service;

import com.tradepulseai.orderservice.dto.OrderResponseDTO;
import com.tradepulseai.orderservice.mapper.OrderMapper;
import com.tradepulseai.orderservice.model.TradeOrder;
import com.tradepulseai.orderservice.repository.TradeOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderHistoryService {

    private final TradeOrderRepository tradeOrderRepository;
    private final StockCatalogClient stockCatalogClient;

    public OrderHistoryService(TradeOrderRepository tradeOrderRepository, StockCatalogClient stockCatalogClient) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.stockCatalogClient = stockCatalogClient;
    }

    @Transactional
    public TradeOrder saveCompletedOrder(TradeOrder order) {
        return tradeOrderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponseDTO> getOrders(Long userId) {
        List<OrderResponseDTO> orders = tradeOrderRepository.findByUserIdOrderByCreatedAtDesc(userId)
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
}

