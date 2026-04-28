package com.tradepulseai.stockservice.dto.stock;

public class StockRatingDTO {

    private double score;
    private int analysts;

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public int getAnalysts() {
        return analysts;
    }

    public void setAnalysts(int analysts) {
        this.analysts = analysts;
    }
}

