package com.tradepulseai.orderservice.service;

import com.tradepulseai.orderservice.dto.PortfolioOrderSyncRequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class PortfolioSyncClient {

    private static final Logger log = LoggerFactory.getLogger(PortfolioSyncClient.class);

    private final RestClient restClient;

    public PortfolioSyncClient(
            @Value("${portfolio.service.base-url:http://cust-service:4000}") String portfolioServiceBaseUrl
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(portfolioServiceBaseUrl)
                .build();
    }

    public void syncCompletedOrder(String userEmail, PortfolioOrderSyncRequestDTO request) {
        try {
            restClient.post()
                    .uri("/customers/portfolio/orders/complete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-User-Email", userEmail)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception exception) {
            log.error("Failed to sync completed order to portfolio for {}", userEmail, exception);
            throw new IllegalStateException("Payment completed, but portfolio update failed. Please retry.");
        }
    }
}

