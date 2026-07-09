package com.tradepulseai.stockservice.service;

import com.tradepulseai.stockservice.model.Stock;
import com.tradepulseai.stockservice.model.StockMarketData;
import com.tradepulseai.stockservice.repository.StockMarketDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);

    private final RestClient restClient;
    private final StockMarketDataRepository stockMarketDataRepository;
    private final String apiKey;
    private final String apiBaseUrl;
    private final boolean newsIntegrationEnabled;

    public NewsService(
            @Value("${massive.api.base-url}") String apiBaseUrl,
            @Value("${massive.api.key:}") String apiKey,
            @Value("${massive.news.integration-enabled:false}") boolean newsIntegrationEnabled,
            StockMarketDataRepository stockMarketDataRepository) {
        this.restClient = RestClient.create();
        this.stockMarketDataRepository = stockMarketDataRepository;
        this.apiKey = apiKey;
        this.apiBaseUrl = apiBaseUrl;
        this.newsIntegrationEnabled = newsIntegrationEnabled;
    }

    /**
     * Fetch daily news for a stock and update sentiment data
     */
    public void fetchAndUpdateNewsSentiment(Stock stock, LocalDate tradingDate) {
        if (!newsIntegrationEnabled || apiKey == null || apiKey.isBlank()) {
            log.debug("News integration disabled or API key not configured");
            return;
        }

        try {
            Optional<StockMarketData> marketDataOpt =
                stockMarketDataRepository.findByStockAndTradingDate(stock, tradingDate);

            if (marketDataOpt.isEmpty()) {
                log.debug("No market data found for stock {} on {}", stock.getSymbol(), tradingDate);
                return;
            }

            StockMarketData marketData = marketDataOpt.get();

            // Fetch news for this stock
            List<NewsArticle> articles = fetchNewsForStock(stock.getSymbol(), tradingDate);

            if (articles.isEmpty()) {
                log.debug("No news found for {} on {}", stock.getSymbol(), tradingDate);
                marketData.setNewsCount(0);
                marketData.setSentimentScore(BigDecimal.ZERO);
                marketData.setDailyNews(null);
                stockMarketDataRepository.save(marketData);
                return;
            }

            // Aggregate sentiment
            SentimentAggregation sentiment = aggregateSentiment(articles);

            marketData.setNewsCount(articles.size());
            marketData.setSentimentScore(new BigDecimal(sentiment.score).setScale(4, java.math.RoundingMode.HALF_UP));
            marketData.setDailyNews(buildDailyNewsSummary(articles));

            stockMarketDataRepository.save(marketData);
            log.info("Updated sentiment for {} on {} (score: {})",
                stock.getSymbol(), tradingDate, sentiment.score);

        } catch (Exception e) {
            log.error("Error fetching news for {} on {}", stock.getSymbol(), tradingDate, e);
        }
    }

    private List<NewsArticle> fetchNewsForStock(String ticker, LocalDate date) {
        List<NewsArticle> articles = new ArrayList<>();

        try {
            LocalDate startDate = date;
            LocalDate endDate = date.plusDays(1);

            String url = UriComponentsBuilder
                .fromHttpUrl(apiBaseUrl + "/v2/reference/news")
                .queryParam("ticker", ticker)
                .queryParam("published_utc.gte", startDate.format(DateTimeFormatter.ISO_DATE))
                .queryParam("published_utc.lt", endDate.format(DateTimeFormatter.ISO_DATE))
                .queryParam("limit", 100)
                .queryParam("sort", "published_utc")
                .queryParam("apiKey", apiKey)
                .toUriString();

            String response = restClient.get()
                .uri(url)
                .retrieve()
                .body(String.class);

            if (response == null) {
                return articles;
            }

            JsonNode root = new tools.jackson.databind.ObjectMapper().readTree(response);
            JsonNode results = root.get("results");

            if (results != null && results.isArray()) {
                for (JsonNode result : results) {
                    NewsArticle article = parseNewsArticle(result);
                    if (article != null) {
                        articles.add(article);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error fetching news from Polygon.io for {}", ticker, e);
        }

        return articles;
    }

    private NewsArticle parseNewsArticle(JsonNode node) {
        try {
            NewsArticle article = new NewsArticle();
            article.title = node.get("title") != null ? node.get("title").asText() : "";

            // Extract sentiment from insights
            JsonNode insights = node.get("insights");
            if (insights != null && insights.isArray() && insights.size() > 0) {
                JsonNode firstInsight = insights.get(0);
                String sentiment = firstInsight.get("sentiment") != null ?
                    firstInsight.get("sentiment").asText() : "neutral";
                article.sentiment = sentiment;
            }

            return article;
        } catch (Exception e) {
            log.error("Error parsing news article", e);
            return null;
        }
    }

    private SentimentAggregation aggregateSentiment(List<NewsArticle> articles) {
        SentimentAggregation agg = new SentimentAggregation();

        int positive = 0, negative = 0;

        for (NewsArticle article : articles) {
            if ("positive".equalsIgnoreCase(article.sentiment)) {
                positive++;
            } else if ("negative".equalsIgnoreCase(article.sentiment)) {
                negative++;
            }
        }

        // Calculate composite sentiment score (-1.0 to 1.0)
        int total = articles.size();
        double score = (double)(positive - negative) / total;
        agg.score = score;

        return agg;
    }

    private String buildDailyNewsSummary(List<NewsArticle> articles) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (NewsArticle article : articles) {
            if (article.title == null || article.title.isBlank()) {
                continue;
            }
            if (count > 0) {
                sb.append(" | ");
            }
            sb.append(article.title.trim());
            count++;
            if (count >= 3 || sb.length() >= 700) {
                break;
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    static class NewsArticle {
        String title;
        String sentiment = "neutral";
    }

    static class SentimentAggregation {
        double score;
    }
}



