package com.tradepulseai.stockservice.dto.market;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PolygonExchangeResponse(
        List<PolygonExchange> results,
        @JsonProperty("next_url") String nextUrl
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PolygonExchange(
            Integer id,
            String acronym,
            @JsonProperty("asset_class") String assetClass,
            String locale,
            String mic,
            String name,
            @JsonProperty("operating_mic") String operatingMic,
            @JsonProperty("participant_id") String participantId,
            String type,
            String url
    ) {
    }
}

