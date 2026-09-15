package com.tradepulse.customerservice.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AccountChecklistClient {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final Set<String> FINAL_ORDER_STATUSES = Set.of("COMPLETED", "CANCELLED", "FAILED", "REJECTED", "EXPIRED");
    private static final ParameterizedTypeReference<List<OrderSnapshot>> ORDER_LIST_TYPE = new ParameterizedTypeReference<>() {
    };

    private final RestClient walletServiceClient;
    private final RestClient portfolioServiceClient;
    private final RestClient orderServiceClient;

    public AccountChecklistClient(
            @Value("${wallet.service.base-url:http://payment-service:4001}") String walletServiceBaseUrl,
            @Value("${portfolio.service.base-url:http://portfolio-service:4007}") String portfolioServiceBaseUrl,
            @Value("${order.service.base-url:http://order-service:4006}") String orderServiceBaseUrl
    ) {
        this.walletServiceClient = RestClient.builder().baseUrl(walletServiceBaseUrl).build();
        this.portfolioServiceClient = RestClient.builder().baseUrl(portfolioServiceBaseUrl).build();
        this.orderServiceClient = RestClient.builder().baseUrl(orderServiceBaseUrl).build();
    }

    public ChecklistStatus fetchChecklistStatus(Long userId) {
        BigDecimal walletBalance = fetchWalletBalance(userId);
        int activeHoldings = fetchActiveHoldings(userId);
        int activeOrders = fetchActiveOrders(userId);
        return new ChecklistStatus(walletBalance, activeHoldings, activeOrders);
    }

    private BigDecimal fetchWalletBalance(Long userId) {
        WalletSnapshot response = walletServiceClient.get()
                .uri("/wallet/me")
                .header(USER_ID_HEADER, String.valueOf(userId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, serviceResponse) -> {
                    throw new IllegalStateException("Unable to verify wallet balance before account deletion.");
                })
                .body(WalletSnapshot.class);

        if (response == null || response.balance() == null) {
            return BigDecimal.ZERO;
        }

        return response.balance();
    }

    private int fetchActiveHoldings(Long userId) {
        PortfolioSnapshot response = portfolioServiceClient.get()
                .uri(uriBuilder -> uriBuilder.path("/portfolio").queryParam("page", 0).queryParam("size", 1).build())
                .header(USER_ID_HEADER, String.valueOf(userId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, serviceResponse) -> {
                    throw new IllegalStateException("Unable to verify portfolio holdings before account deletion.");
                })
                .body(PortfolioSnapshot.class);

        if (response == null) {
            return 0;
        }

        if (response.summary() != null && response.summary().totalPositions() != null) {
            return Math.max(response.summary().totalPositions(), 0);
        }

        return response.holdings() == null ? 0 : response.holdings().size();
    }

    private int fetchActiveOrders(Long userId) {
        List<OrderSnapshot> orders = orderServiceClient.get()
                .uri("/orders")
                .header(USER_ID_HEADER, String.valueOf(userId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, serviceResponse) -> {
                    throw new IllegalStateException("Unable to verify active orders before account deletion.");
                })
                .body(ORDER_LIST_TYPE);

        if (orders == null || orders.isEmpty()) {
            return 0;
        }

        return (int) orders.stream()
                .filter(order -> order != null)
                .filter(order -> order.status() == null || !FINAL_ORDER_STATUSES.contains(order.status().trim().toUpperCase(Locale.ROOT)))
                .count();
    }

    private record WalletSnapshot(BigDecimal balance) {
    }

    private record PortfolioSnapshot(PortfolioSummary summary, List<Object> holdings) {
    }

    private record PortfolioSummary(Integer totalPositions) {
    }

    private record OrderSnapshot(String status) {
    }

    public record ChecklistStatus(BigDecimal walletBalance, int activeHoldings, int activeOrders) {
    }
}

