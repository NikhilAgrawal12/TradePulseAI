package com.tradepulseai.stockservice.service;

import com.tradepulseai.stockservice.dto.market.PolygonExchangeResponse;
import com.tradepulseai.stockservice.dto.market.PolygonTickerReferenceResponse;
import com.tradepulseai.stockservice.model.Exchange;
import com.tradepulseai.stockservice.model.Stock;
import com.tradepulseai.stockservice.repository.ExchangeRepository;
import com.tradepulseai.stockservice.repository.StockRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StockUniverseBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(StockUniverseBootstrapService.class);

    private final ExchangeRepository exchangeRepository;
    private final StockRepository stockRepository;
    private final PolygonMarketDataClient polygonMarketDataClient;
    private final boolean universeSyncEnabled;
    private final int targetSize;
    private final int pageSize;

    public StockUniverseBootstrapService(
            ExchangeRepository exchangeRepository,
            StockRepository stockRepository,
            PolygonMarketDataClient polygonMarketDataClient,
            @Value("${polygon.universe.sync-enabled:true}") boolean universeSyncEnabled,
            @Value("${polygon.universe.target-size:700}") int targetSize,
            @Value("${polygon.universe.page-size:1000}") int pageSize
    ) {
        this.exchangeRepository = exchangeRepository;
        this.stockRepository = stockRepository;
        this.polygonMarketDataClient = polygonMarketDataClient;
        this.universeSyncEnabled = universeSyncEnabled;
        this.targetSize = targetSize;
        this.pageSize = pageSize;
    }

    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapUniverse() {
        syncExchangeAndUniverseData();
    }

    @Transactional
    @Scheduled(cron = "${polygon.exchanges.sync-cron:0 0 3 1 * *}", zone = "UTC")
    public void scheduledExchangeSync() {
        if (!universeSyncEnabled || !polygonMarketDataClient.isConfigured()) {
            return;
        }

        int synced = syncExchanges().size();
        log.info("Polygon exchange sync completed. Synced {} exchange records.", synced);
    }

    @Transactional
    @Scheduled(cron = "${polygon.universe.sync-cron:0 0 4 * * SUN}", zone = "UTC")
    public void scheduledUniverseSync() {
        syncExchangeAndUniverseData();
    }

    private void syncExchangeAndUniverseData() {
        if (!universeSyncEnabled) {
            return;
        }

        if (!polygonMarketDataClient.isConfigured()) {
            log.warn("Skipping Polygon universe bootstrap because POLYGON_API_KEY is not configured.");
            return;
        }

        Map<String, Exchange> exchangesByMic = syncExchanges();

        List<PolygonTickerReferenceResponse.PolygonTickerReference> tickers =
                polygonMarketDataClient.fetchActiveUsTickers(targetSize, pageSize);

        if (tickers.isEmpty()) {
            log.warn("Polygon universe bootstrap returned no tickers.");
            return;
        }

        int created = 0;
        int updated = 0;
        for (PolygonTickerReferenceResponse.PolygonTickerReference ticker : tickers) {
            Stock stock = stockRepository.findBySymbol(ticker.ticker()).orElseGet(Stock::new);
            boolean isNew = stock.getStockId() == null;

            stock.setSymbol(ticker.ticker());
            stock.setName(ticker.name());
            stock.setExchange(exchangesByMic.get(ticker.primaryExchange()));
            stock.setMarket(ticker.market());
            stock.setLocale(ticker.locale());
            stock.setType(ticker.type());
            stock.setActive(ticker.active());
            stock.setCurrencyName(ticker.currencyName());
            stock.setCik(ticker.cik());
            stock.setCompositeFigi(ticker.compositeFigi());
            stock.setShareClassFigi(ticker.shareClassFigi());

            stockRepository.save(stock);
            if (isNew) {
                created++;
            } else {
                updated++;
            }
        }

        log.info("Polygon universe bootstrap completed. Created {} records, updated {} records. Current stock count={}",
                created, updated, stockRepository.count());
    }

    private Map<String, Exchange> syncExchanges() {
        List<PolygonExchangeResponse.PolygonExchange> exchanges = polygonMarketDataClient.fetchUsStockExchanges();
        Map<String, Exchange> exchangesByMic = new HashMap<>();

        for (PolygonExchangeResponse.PolygonExchange exchangeDto : exchanges) {
            if (exchangeDto == null || exchangeDto.mic() == null || exchangeDto.mic().isBlank()) {
                continue;
            }

            Exchange exchange = exchangeRepository.findByMic(exchangeDto.mic()).orElseGet(Exchange::new);
            exchange.setProviderExchangeId(exchangeDto.id());
            exchange.setAcronym(exchangeDto.acronym());
            exchange.setAssetClass(exchangeDto.assetClass());
            exchange.setLocale(exchangeDto.locale());
            exchange.setMic(exchangeDto.mic());
            exchange.setName(exchangeDto.name());
            exchange.setOperatingMic(exchangeDto.operatingMic());
            exchange.setParticipantId(exchangeDto.participantId());
            exchange.setType(exchangeDto.type());
            exchange.setUrl(exchangeDto.url());

            Exchange savedExchange = exchangeRepository.save(exchange);
            exchangesByMic.put(savedExchange.getMic(), savedExchange);
        }

        return exchangesByMic;
    }
}
