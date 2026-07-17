package com.tradepulse.orderservice.service;

import com.tradepulse.orderservice.dto.portfolio.PortfolioOrderSyncRequestDTO;
import com.tradepulse.orderservice.grpc.PortfolioSyncGrpcClient;
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

