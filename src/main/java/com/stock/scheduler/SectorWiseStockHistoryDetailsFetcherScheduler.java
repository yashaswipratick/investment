package com.stock.scheduler;

import com.stock.dto.StockHistoryRequest;
import com.stock.service.SectorWiseStockDataIntegrator;
import com.stock.service.StockHistoryDataIntegrator;
import com.stock.util.Utility;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SectorWiseStockHistoryDetailsFetcherScheduler {

    @Autowired
    private SectorWiseStockDataIntegrator sectorWiseStockDataIntegrator;

    @Autowired
    private StockHistoryDataIntegrator stockHistoryDataIntegrator;

    private static final Map<String, List<String>> sectorWisestockSymbolCache = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> stockSymbolCache = new ConcurrentHashMap<>();
    // In-memory lock to avoid overlapping executions in a single JVM.
    private final AtomicBoolean fetchJobRunning = new AtomicBoolean(false);

    //@PostConstruct
    public void loadDataOnStartup() {
        sectorWiseStockDataIntegrator.getSectorWiseStock()
                .doOnNext(sectorWisestockSymbolCache::putAll)
                .flatMap(stringListMap ->
                        Flux.fromIterable(stringListMap.entrySet())
                                .flatMap(entry -> Flux.fromIterable(entry.getValue())
                                        .map(symbol -> Map.entry(symbol, Boolean.TRUE)))
                                .collect(Collectors.toConcurrentMap(
                                        Map.Entry::getKey,
                                        Map.Entry::getValue,
                                        (a, b) -> a,
                                        ConcurrentHashMap::new
                                ))
                )
                .doOnNext(stockSymbolCache::putAll)
                .doOnNext(stockSymbols ->
                        log.info("Active sector stock symbols loaded. count: {}", stockSymbols.size()))
                .block();
    }

    /*@Scheduled(
            fixedDelayString = "${scheduler.sector-stock-history.fixed-delay-ms:60000}",
            initialDelayString = "${scheduler.sector-stock-history.initial-delay-ms:15000}"
    )*/
    public void fetchStockDetailsList() {
        if (!fetchJobRunning.compareAndSet(false, true)) {
            log.info("Skipping scheduled run: previous sector stock history fetch is still in progress");
            return;
        }

        // Snapshot keys to avoid concurrent modification while we remove successfully processed symbols.
        Flux.fromIterable(new ArrayList<>(stockSymbolCache.keySet()))
                .flatMap(stockSymbol -> {
                    List<StockHistoryRequest> stockHistoryRequests = stockHistoryDataIntegrator.getStockHistoryRequests(
                            StockHistoryRequest.builder()
                                    .stockSymbol(stockSymbol)
                                    .to(Utility.dateFormatterCurrentDay())
                                    .series("EQ")
                                    .numOfDays(250)
                                    .build()
                    );

                    return stockHistoryDataIntegrator.fetchStockHistoryDetailsFromNSE(stockHistoryRequests)
                            .flatMap(stockHistoryDataIntegrator::save)
                            .doOnNext(saved -> {
                                stockSymbolCache.remove(stockSymbol);
                                log.info("Saved history for symbol: {}. Remaining cache size: {}", stockSymbol, stockSymbolCache.size());
                            })
                            // Keep scheduler alive per symbol failure; failed symbols stay in cache for next run.
                            .onErrorResume(error -> {
                                log.error("Failed to fetch/save stock history for symbol: {}. Error: {}", stockSymbol, error.getMessage());
                                return Mono.empty();
                            });
                }, 10)
                .collectList()
                .doOnNext(savedList -> log.info("SectorWise history fetch completed. Saved symbols count: {}, pending: {}",
                        savedList.size(), stockSymbolCache.size()))
                .doFinally(signalType -> {
                    fetchJobRunning.set(false);
                    log.info("SectorWise history scheduler run finished with signal: {}", signalType);
                })
                .subscribe();
    }
}
