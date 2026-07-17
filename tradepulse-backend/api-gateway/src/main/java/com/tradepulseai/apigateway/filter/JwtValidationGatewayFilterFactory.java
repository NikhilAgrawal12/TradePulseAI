package com.tradepulseai.apigateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class JwtValidationGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String AUTHENTICATED_USER_ID_HEADER = "X-Authenticated-User-Id";
    private static final String FIRST_NAME_HEADER = "X-First-Name";
    private static final String LAST_NAME_HEADER = "X-Last-Name";

    private final WebClient webClient;

    public JwtValidationGatewayFilterFactory(WebClient.Builder webClientBuilder, @Value("${auth.service.url}")  String authServiceUrl) {
        this.webClient = webClientBuilder.baseUrl(authServiceUrl).build();
    }

    @Override
    public GatewayFilter apply(Object config) {
        return(exchange,chain) -> {
            String token = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if(token==null || !token.startsWith("Bearer ")) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            return webClient.get()
                    .uri("/validate")
                    .header(HttpHeaders.AUTHORIZATION,token)
                    .retrieve()
                    .toBodilessEntity()
                    .flatMap(response -> {
                        String authenticatedUserId = response.getHeaders().getFirst(AUTHENTICATED_USER_ID_HEADER);
                        String firstName = response.getHeaders().getFirst(FIRST_NAME_HEADER);
                        String lastName = response.getHeaders().getFirst(LAST_NAME_HEADER);

                        if (authenticatedUserId == null || authenticatedUserId.isBlank()) {
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        }

                        var mutatedRequest = exchange.getRequest().mutate()
                                .headers(headers -> {
                                    headers.remove(USER_ID_HEADER);
                                    headers.set(USER_ID_HEADER, authenticatedUserId);
                                    if (firstName != null && !firstName.isBlank()) {
                                        headers.set(FIRST_NAME_HEADER, firstName);
                                    }
                                    if (lastName != null && !lastName.isBlank()) {
                                        headers.set(LAST_NAME_HEADER, lastName);
                                    }
                                })
                                .build();

                        return chain.filter(exchange.mutate().request(mutatedRequest).build());
                    });
        };
    }
}
