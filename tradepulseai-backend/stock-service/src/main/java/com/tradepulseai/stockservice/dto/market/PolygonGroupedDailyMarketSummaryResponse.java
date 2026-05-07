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
            Double o,
            Double h,
            Double l,
            Double c,
            Long t,
            Long n,
            Double v,
            Double vw
    ) {
    }
}

