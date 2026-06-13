package com.tradepulseai.stockservice.dto.market;

public class MarketStatusResponseDTO {

    private String session;
    private String label;
    private String cssClass;
    private String market;
    private String nyse;
    private String nasdaq;
    private boolean stale;
    private String serverTime;
    private String lastUpdated;

    public String getSession() {
        return session;
    }

    public void setSession(String session) {
        this.session = session;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getCssClass() {
        return cssClass;
    }

    public void setCssClass(String cssClass) {
        this.cssClass = cssClass;
    }

    public String getMarket() {
        return market;
    }

    public void setMarket(String market) {
        this.market = market;
    }

    public String getNyse() {
        return nyse;
    }

    public void setNyse(String nyse) {
        this.nyse = nyse;
    }

    public String getNasdaq() {
        return nasdaq;
    }

    public void setNasdaq(String nasdaq) {
        this.nasdaq = nasdaq;
    }

    public boolean isStale() {
        return stale;
    }

    public void setStale(boolean stale) {
        this.stale = stale;
    }

    public String getServerTime() {
        return serverTime;
    }

    public void setServerTime(String serverTime) {
        this.serverTime = serverTime;
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}

