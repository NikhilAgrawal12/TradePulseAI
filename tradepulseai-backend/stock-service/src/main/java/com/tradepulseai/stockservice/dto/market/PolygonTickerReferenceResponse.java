package com.tradepulseai.stockservice.dto.market;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PolygonTickerReferenceResponse(
        List<PolygonTickerReference> results,
        @JsonProperty("next_url") String nextUrl
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PolygonTickerReference(
            String ticker,
            String name,
            @JsonProperty("primary_exchange") String primaryExchange,
            String locale,
            String market,
            @JsonProperty("active") boolean active
    ) {
    }
}

