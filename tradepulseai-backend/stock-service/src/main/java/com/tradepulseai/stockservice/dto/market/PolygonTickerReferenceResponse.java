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
            @JsonProperty("active") boolean active,
            String type,
            @JsonProperty("currency_name") String currencyName,
            String cik,
            @JsonProperty("composite_figi") String compositeFigi,
            @JsonProperty("share_class_figi") String shareClassFigi
    ) {
    }
}

