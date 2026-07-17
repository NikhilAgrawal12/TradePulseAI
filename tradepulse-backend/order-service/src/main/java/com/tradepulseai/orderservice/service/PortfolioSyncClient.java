package com.tradepulseai.orderservice.service;

import com.tradepulseai.orderservice.dto.portfolio.PortfolioOrderSyncRequestDTO;
import com.tradepulseai.orderservice.grpc.PortfolioSyncGrpcClient;
import org.springframework.stereotype.Service;

@Service
public class PortfolioSyncClient {

    private final PortfolioSyncGrpcClient portfolioSyncGrpcClient;

    public PortfolioSyncClient(
            PortfolioSyncGrpcClient portfolioSyncGrpcClient
    ) {
        this.portfolioSyncGrpcClient = portfolioSyncGrpcClient;
    }

    public void syncCompletedOrder(Long userId, PortfolioOrderSyncRequestDTO request) {
        portfolioSyncGrpcClient.syncCompletedOrder(userId, request);
    }
}

