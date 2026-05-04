package com.tradepulseai.stockservice.dto.market;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PolygonPreviousCloseResponse(
        String ticker,
        List<PolygonAggregate> results
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PolygonAggregate(
            double c,
            long v,
            long t
    ) {
    }
}

