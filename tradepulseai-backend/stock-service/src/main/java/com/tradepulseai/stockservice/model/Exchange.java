package com.tradepulseai.stockservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "exchanges")
public class Exchange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "exchange_id")
    private Long exchangeId;

    @Column(name = "provider_exchange_id", unique = true)
    private Integer providerExchangeId;

    @Column(name = "acronym", length = 50)
    private String acronym;

    @Column(name = "asset_class", length = 50)
    private String assetClass;

    @Column(name = "locale", length = 20)
    private String locale;

    @Column(name = "mic", length = 20, unique = true)
    private String mic;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "operating_mic", length = 20)
    private String operatingMic;

    @Column(name = "participant_id", length = 20)
    private String participantId;

    @Column(name = "type", length = 50)
    private String type;

    @Column(name = "url", length = 500)
    private String url;

    public Long getExchangeId() {
        return exchangeId;
    }

    public void setExchangeId(Long exchangeId) {
        this.exchangeId = exchangeId;
    }

    public Integer getProviderExchangeId() {
        return providerExchangeId;
    }

    public void setProviderExchangeId(Integer providerExchangeId) {
        this.providerExchangeId = providerExchangeId;
    }

    public String getAcronym() {
        return acronym;
    }

    public void setAcronym(String acronym) {
        this.acronym = acronym;
    }

    public String getAssetClass() {
        return assetClass;
    }

    public void setAssetClass(String assetClass) {
        this.assetClass = assetClass;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getMic() {
        return mic;
    }

    public void setMic(String mic) {
        this.mic = mic;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOperatingMic() {
        return operatingMic;
    }

    public void setOperatingMic(String operatingMic) {
        this.operatingMic = operatingMic;
    }

    public String getParticipantId() {
        return participantId;
    }

    public void setParticipantId(String participantId) {
        this.participantId = participantId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}

