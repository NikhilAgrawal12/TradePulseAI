package com.tradepulseai.stockservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "stocks")
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_id")
    private Long stockId;

    @Column(name = "symbol", nullable = false, length = 50, unique = true)
    private String symbol;

    @Column(name = "name", length = 255)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exchange_id")
    private Exchange exchange;

    @Column(name = "market", length = 50)
    private String market;

    @Column(name = "locale", length = 20)
    private String locale;

    @Column(name = "type", length = 50)
    private String type;

    @Column(name = "active")
    private Boolean active;

    @Column(name = "currency_name", length = 20)
    private String currencyName;

    @Column(name = "cik", length = 30)
    private String cik;

    @Column(name = "composite_figi", length = 50)
    private String compositeFigi;

    @Column(name = "share_class_figi", length = 50)
    private String shareClassFigi;

    public Long getStockId() {
        return stockId;
    }

    public void setStockId(Long stockId) {
        this.stockId = stockId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Exchange getExchange() {
        return exchange;
    }

    public void setExchange(Exchange exchange) {
        this.exchange = exchange;
    }

    public String getMarket() {
        return market;
    }

    public void setMarket(String market) {
        this.market = market;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getCurrencyName() {
        return currencyName;
    }

    public void setCurrencyName(String currencyName) {
        this.currencyName = currencyName;
    }

    public String getCik() {
        return cik;
    }

    public void setCik(String cik) {
        this.cik = cik;
    }

    public String getCompositeFigi() {
        return compositeFigi;
    }

    public void setCompositeFigi(String compositeFigi) {
        this.compositeFigi = compositeFigi;
    }

    public String getShareClassFigi() {
        return shareClassFigi;
    }

    public void setShareClassFigi(String shareClassFigi) {
        this.shareClassFigi = shareClassFigi;
    }
}
