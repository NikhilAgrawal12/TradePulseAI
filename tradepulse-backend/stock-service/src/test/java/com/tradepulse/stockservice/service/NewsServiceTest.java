package com.tradepulse.stockservice.service;

import com.tradepulse.stockservice.model.Stock;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NewsServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void buildDailyNewsSummaryPrefersHeadlinesMentioningStockThenFallsBackToOthers() {
        Stock stock = new Stock();
        stock.setSymbol("NVDA");
        stock.setName("NVIDIA Corporation");

        NewsService.NewsArticle directOne = article("Nvidia launches next-gen AI chips");
        NewsService.NewsArticle fallbackOne = article("Semiconductor sector rallies on AI demand");
        NewsService.NewsArticle directTwo = article("Analysts raise price target for NVDA after earnings");
        NewsService.NewsArticle fallbackTwo = article("Wall Street closes higher amid tech momentum");

        String summary = NewsService.buildDailyNewsSummary(
                List.of(directOne, fallbackOne, directTwo, fallbackTwo),
                stock
        );

        List<Map<String, Object>> headlines = parseHeadlines(summary);
        assertThat(headlines).hasSize(3);
        assertThat(headlines.get(0).get("headline")).isEqualTo("Nvidia launches next-gen AI chips");
        assertThat(headlines.get(1).get("headline")).isEqualTo("Analysts raise price target for NVDA after earnings");
        assertThat(headlines.get(2).get("headline")).isEqualTo("Semiconductor sector rallies on AI demand");
    }

    @Test
    void buildDailyNewsSummaryFallsBackWhenFewerThanThreeDirectMatchesExist() {
        Stock stock = new Stock();
        stock.setSymbol("NVDA");
        stock.setName("NVIDIA Corporation");

        String summary = NewsService.buildDailyNewsSummary(
                List.of(
                        article("Broader market gains after inflation cools"),
                        article("Nvidia supplier ramps production"),
                        article("Chipmakers extend weekly rally")
                ),
                stock
        );

        List<Map<String, Object>> headlines = parseHeadlines(summary);
        assertThat(headlines).hasSize(3);
        assertThat(headlines.get(0).get("headline")).isEqualTo("Nvidia supplier ramps production");
        assertThat(headlines.get(1).get("headline")).isEqualTo("Broader market gains after inflation cools");
        assertThat(headlines.get(2).get("headline")).isEqualTo("Chipmakers extend weekly rally");
    }

    @Test
    void buildDailyNewsSummaryNeverExceedsThreeHeadlines() {
        Stock stock = new Stock();
        stock.setSymbol("NVDA");
        stock.setName("NVIDIA Corporation");

        String summary = NewsService.buildDailyNewsSummary(
                List.of(
                        article("NVDA opens higher"),
                        article("Nvidia expands enterprise partnerships"),
                        article("AI stocks continue momentum"),
                        article("Late-session buying lifts semiconductor names")
                ),
                stock
        );

        List<Map<String, Object>> headlines = parseHeadlines(summary);
        assertThat(headlines).hasSize(3);
        assertThat(headlines.get(0).get("headline")).isEqualTo("NVDA opens higher");
        assertThat(headlines.get(1).get("headline")).isEqualTo("Nvidia expands enterprise partnerships");
        assertThat(headlines.get(2).get("headline")).isEqualTo("AI stocks continue momentum");
    }

    @Test
    void buildDailyNewsSummaryIncludesLinkWhenAvailable() {
        Stock stock = new Stock();
        stock.setSymbol("NVDA");
        stock.setName("NVIDIA Corporation");

        NewsService.NewsArticle linked = article("Nvidia outlook improves", "https://example.com/nvda", "Example News");

        String summary = NewsService.buildDailyNewsSummary(List.of(linked), stock);

        List<Map<String, Object>> headlines = parseHeadlines(summary);
        assertThat(headlines).hasSize(1);
        assertThat(headlines.getFirst().get("url")).isEqualTo("https://example.com/nvda");
        assertThat(headlines.getFirst().get("publisher")).isEqualTo("Example News");
    }

    private static NewsService.NewsArticle article(String title) {
        NewsService.NewsArticle article = new NewsService.NewsArticle();
        article.title = title;
        return article;
    }

    private static NewsService.NewsArticle article(String title, String url, String publisher) {
        NewsService.NewsArticle article = new NewsService.NewsArticle();
        article.title = title;
        article.url = url;
        article.publisher = publisher;
        return article;
    }

    private static List<Map<String, Object>> parseHeadlines(String summary) {
        try {
            return OBJECT_MAPPER.readValue(summary, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new RuntimeException("Failed to parse headline summary JSON", exception);
        }
    }
}

