package com.stock.scheduler;

import com.stock.dto.StockHistoryRequest;
import com.stock.service.*;
import com.stock.util.Utility;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SectorWiseStockHistoryDetailsFetcherScheduler {

    @Autowired
    private StockInfoHttpEntryLoader stockInfoHttpEntryLoader;

    @Autowired
    private StockDescriptionIntegrator stockDescriptionIntegrator;

    @Autowired
    private SectorWiseStockDataIntegrator sectorWiseStockDataIntegrator;

    @Autowired
    private StockHistoryDataHttpEntryLoader stockHistoryDataHttpEntryLoader;

    @Autowired
    private StockHistoryDataIntegrator stockHistoryDataIntegrator;

    private static final Map<String, List<String>> sectorWisestockSymbolCache = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> stockSymbolCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadDataOnStartup() {

        sectorWiseStockDataIntegrator.getSectorWiseStock()
                .flatMap(sectorWiseStockDetails -> {
                    // Cache all sector-wise stock details
                    sectorWisestockSymbolCache.putAll(sectorWiseStockDetails);
                    return Mono.justOrEmpty(sectorWiseStockDetails);
                })
                .flatMap(stringListMap ->
                        Flux.fromIterable(stringListMap.entrySet())
                                // flatten: each stock symbol gets paired with TRUE
                                .flatMap(entry -> Flux.fromIterable(entry.getValue())
                                        .map(symbol -> Map.entry(symbol, Boolean.TRUE)))
                                // collect into a concurrent map
                                .collect(Collectors.toConcurrentMap(
                                        Map.Entry::getKey,
                                        Map.Entry::getValue,
                                        (a, b) -> a, // merge function in case of duplicate keys
                                        ConcurrentHashMap::new
                                ))
                )
                .flatMap(stockSymbols -> {
                    stockSymbolCache.putAll(stockSymbols);
                    return Mono.justOrEmpty(stockSymbols);
                })
                .doOnNext(sectorWiseStockDetailsMap ->
                        log.info("Active sectors stock symbol loaded to fetch the data from NSE. count: {} symbols: {}",
                                sectorWiseStockDetailsMap.size(), sectorWiseStockDetailsMap))
                .block();

    }

    //@Scheduled(fixedRate = 60000)
    public void fetchStockDetailsList() {
        Flux.just(stockSymbolCache)
                .flatMap(symbols -> Flux.fromIterable(symbols.keySet())
                        .flatMap(stockSymbol -> {
                            List<StockHistoryRequest> stockHistoryRequest = stockHistoryDataIntegrator.getStockHistoryRequests(StockHistoryRequest.builder().stockSymbol(stockSymbol)
                                    .to(Utility.dateFormatterCurrentDay())
                                            .series("EQ")
                                    .numOfDays(250)
                                    .build());
                            return stockHistoryDataIntegrator.fetchStockHistoryDetailsFromNSE(stockHistoryRequest);
                        }, 10)
                        .collectList()
                        .map(stockHistoryList -> Map.entry(symbols, stockHistoryList))
                )
                .collect(Collectors.toMap(Map.Entry::getKey,
                        Map.Entry::getValue,
                        (existing, newList) -> {
                            existing.addAll(newList);
                            return existing;
                        }))
                .flatMapMany(details -> Flux.fromIterable(details.entrySet()))
                .flatMap(entry -> {
                    stockHistoryDataIntegrator.saveAll(entry.getValue());
                    stockSymbolCache.remove(entry.getKey());
                    return Mono.justOrEmpty(entry.getValue());
                }, 10)
                .doOnNext(stockHistories -> log.info("stockSymbolCache data count: {} ", stockSymbolCache.size()))
                .collect(Collectors.toList())
                .subscribe();
    }
}
