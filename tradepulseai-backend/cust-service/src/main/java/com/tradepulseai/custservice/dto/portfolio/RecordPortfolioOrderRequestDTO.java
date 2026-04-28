package com.tradepulseai.custservice.dto.portfolio;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class RecordPortfolioOrderRequestDTO {

    @NotEmpty(message = "items are required")
    @Valid
    private List<PortfolioFillItemRequestDTO> items;

    public List<PortfolioFillItemRequestDTO> getItems() {
        return items;
    }

    public void setItems(List<PortfolioFillItemRequestDTO> items) {
        this.items = items;
    }
}


