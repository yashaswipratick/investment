package com.stock.scheduler;

import com.stock.dto.StockInfoDTO;
import com.stock.dto.StockInfoDetails;
import com.stock.service.PositionsService;
import com.stock.service.StockDescriptionService;
import com.stock.service.StockDetailsService;
import com.stock.entryloader.StockInfoHttpEntryLoader;
import com.stock.util.Test;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class StockFetcherScheduler {

    @Autowired
    private StockDescriptionService stockDescriptionService;

    @Autowired
    private StockInfoHttpEntryLoader stockInfoHttpEntryLoader;

    @Autowired
    private StockDetailsService stockInfoService;

    @Autowired
    private PositionsService service;

    private static final Set<String> stockSymbolCache = new HashSet<>();

    // @PostConstruct - Disabled: Remove @Autowired bean initialization to prevent startup failures
    // This method will only be called explicitly when needed, not at application startup
    public void loadDataOnStartup() {
        service.getAllPositionStockSymbol()
                .flatMap(stockSymbols -> {
                    stockSymbolCache.addAll(stockSymbols);
                    if (stockSymbolCache.isEmpty()) {
                        return Mono.empty();
                    }
                    return Mono.justOrEmpty(stockSymbols);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    try {
                        stockSymbolCache.addAll(Test.readJsonFile());
                        return Mono.justOrEmpty(stockSymbolCache);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }))
                .doOnNext(stockSymbols -> log.info("Active positioned stock symbol loaded to fetch the data from NSE. symbols: {}", stockSymbols))
                .block();
    }

    public void fetchNiftyFiftyIndexStockSymbol() throws IOException {
        stockSymbolCache.addAll(Test.readJsonFile());
    }

    //@Scheduled(fixedRate = 120000)
    public void fetchStockDetailsList() {
        Flux.fromIterable(stockSymbolCache)
                .flatMap(stockSymbol -> {
                    Mono<StockInfoDetails> stockDetails = stockInfoHttpEntryLoader.getStockDetails(stockSymbol);
                    return stockDetails.flatMap(detail -> {
                        Map<String, StockInfoDTO> map = new HashMap<>(detail.getStockInfo());
                        log.info("Data collected from nse website. result: {} ", map);
                        return Mono.justOrEmpty(map);
                    });
                })
                .collect(() -> new HashMap<String, StockInfoDTO>(), HashMap::putAll)
                .doOnNext(details -> {
                    // Print details to console
                    log.info("Stock details fetched. details: {} ", details);
                    log.info("Stock details fetched Keys. keys: {} ", details.keySet());
                })
                .filter(MapUtils::isNotEmpty)
                .flatMap(details -> stockInfoService.save(StockInfoDetails.builder()
                        .key(LocalDate.now().toString())
                        .stockInfo(details)
                        .createdDate(LocalDateTime.now())
                        .build()))
                .doOnNext(details -> log.info("details saved for data. details: {} ", details)) // Collect results into a List
                .subscribe();
    }

    private Flux<String> getSymbolListToFetchStockInfo() {
        return stockDescriptionService.findDistinctSymbols()
                .flatMapMany(symbols -> {
                    // Print symbols fetched from DB
                    log.info("Symbols fetched from DB. symbols: {}", symbols);
                    // Example endpoint: https://www.nseindia.com/api/quote-equity?symbol=ADANIPORTS
                    return Flux.fromIterable(symbols)
                            .filter(symbol -> symbol != null && !symbol.isEmpty()) // Optionally filter out null or empty symbols
                            .distinct(); // Ensure symbols are distinct
                })
                .switchIfEmpty(Flux.empty()); // Return an empty Flux if no symbols are found
    }
}
