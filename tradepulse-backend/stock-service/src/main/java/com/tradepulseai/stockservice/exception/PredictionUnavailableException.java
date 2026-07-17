package com.tradepulseai.stockservice.exception;

public class PredictionUnavailableException extends RuntimeException {
    public PredictionUnavailableException(String message) {
        super(message);
    }
}

