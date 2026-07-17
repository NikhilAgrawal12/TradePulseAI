package com.tradepulse.stockservice.service;

import com.tradepulse.stockservice.dto.stock.StockPredictionResponseDTO;
import com.tradepulse.stockservice.exception.PredictionUnavailableException;
import com.tradepulse.stockservice.exception.StockNotFoundException;
import com.tradepulse.stockservice.model.Stock;
import com.tradepulse.stockservice.repository.StockRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class MlPredictionService {

    private final StockRepository stockRepository;
    private final RestClient restClient;

    public MlPredictionService(
            StockRepository stockRepository,
            @Value("${ml.service.base-url:http://localhost:4010/v1}") String baseUrl
    ) {
        this.stockRepository = stockRepository;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public StockPredictionResponseDTO getPredictionByStockId(Long stockId) {
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new StockNotFoundException("Stock not found with id: " + stockId));

        try {
            StockPredictionResponseDTO response = restClient.get()
                    .uri("/predictions/{stockId}", stockId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(StockPredictionResponseDTO.class);

            if (response == null) {
                throw new PredictionUnavailableException("Prediction service returned an empty response.");
            }
            return response;
        } catch (HttpClientErrorException.NotFound exception) {
            throw new PredictionUnavailableException("Prediction data is not available for stock: " + stock.getSymbol());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
                throw new PredictionUnavailableException("Prediction model is not trained yet. Trigger training in ML service first.");
            }
            throw new PredictionUnavailableException("Prediction request failed with status: " + exception.getStatusCode());
        } catch (RestClientException exception) {
            throw new PredictionUnavailableException("Unable to reach prediction service right now.");
        }
    }
}

