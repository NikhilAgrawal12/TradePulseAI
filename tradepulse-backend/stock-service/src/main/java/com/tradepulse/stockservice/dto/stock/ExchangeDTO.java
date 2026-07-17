package com.tradepulse.stockservice.dto.stock;

public class ExchangeDTO {

    private Integer id;
    private String mic;
    private String operatingMic;
    private String participantId;
    private String name;
    private String acronym;
    private String assetClass;
    private String locale;
    private String type;
    private String url;
    private String status;

    public ExchangeDTO() {
    }

    public ExchangeDTO(Integer id, String mic, String name, String acronym) {
        this.id = id;
        this.mic = mic;
        this.name = name;
        this.acronym = acronym;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getMic() {
        return mic;
    }

    public void setMic(String mic) {
        this.mic = mic;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

