package com.tradepulseai.stockservice.dto.market;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PolygonGroupedDailyMarketSummaryResponse(
        boolean adjusted,
        @JsonProperty("queryCount") Integer queryCount,
        @JsonProperty("resultsCount") Integer resultsCount,
        List<PolygonGroupedDailyAggregate> results,
        String status,
        @JsonProperty("request_id") String requestId
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PolygonGroupedDailyAggregate(
            @JsonProperty("T") String ticker,
            double o,
            double h,
            double l,
            double c,
            long t,
            long n,
            double v,
            double vw
    ) {
    }
}

