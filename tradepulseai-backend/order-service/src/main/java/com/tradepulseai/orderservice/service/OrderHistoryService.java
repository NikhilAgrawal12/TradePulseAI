package com.tradepulseai.orderservice.service;

import com.tradepulseai.orderservice.dto.OrderResponseDTO;
import com.tradepulseai.orderservice.mapper.OrderMapper;
import com.tradepulseai.orderservice.model.TradeOrder;
import com.tradepulseai.orderservice.repository.TradeOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderHistoryService {

    private final TradeOrderRepository tradeOrderRepository;

    public OrderHistoryService(TradeOrderRepository tradeOrderRepository) {
        this.tradeOrderRepository = tradeOrderRepository;
    }

    @Transactional
    public TradeOrder saveCompletedOrder(TradeOrder order) {
        return tradeOrderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponseDTO> getOrders(Long userId) {
        return tradeOrderRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(OrderMapper::toDTO)
                .toList();
    }
}

