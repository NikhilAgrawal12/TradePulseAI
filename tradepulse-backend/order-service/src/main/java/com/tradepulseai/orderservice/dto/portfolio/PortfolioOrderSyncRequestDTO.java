package com.tradepulseai.orderservice.dto.portfolio;

import java.util.List;

public class PortfolioOrderSyncRequestDTO {

    private List<PortfolioOrderItemDTO> items;

    public List<PortfolioOrderItemDTO> getItems() {
        return items;
    }

    public void setItems(List<PortfolioOrderItemDTO> items) {
        this.items = items;
    }
}

