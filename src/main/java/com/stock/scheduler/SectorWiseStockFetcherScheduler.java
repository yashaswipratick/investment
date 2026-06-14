package com.stock.scheduler;

import ch.qos.logback.classic.Logger;
import com.stock.dto.SectorWiseStockDetails;
import com.stock.dto.StockInfoDTO;
import com.stock.dto.StockInfoDetails;
import com.stock.dto.key.SectorWiseStockKey;
import com.stock.service.*;
import com.stock.util.Test;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SectorWiseStockFetcherScheduler {

    @Autowired
    private StockInfoHttpEntryLoader stockInfoHttpEntryLoader;

    @Autowired
    private StockDescriptionIntegrator stockDescriptionIntegrator;

    @Autowired
    private SectorWiseStockDataService sectorWiseStockDataService;

    private static final Map<String, Boolean> stockSymbolCache = new ConcurrentHashMap<>();

    //@PostConstruct
    public void loadDataOnStartup() {
        stockDescriptionIntegrator.getAll()
                .flatMap(symbols -> Flux.fromIterable(symbols.keySet())
                        .collect(Collectors.toSet()))
                .flatMap(stockDetails -> sectorWiseStockDataService.getAllSet()
                        .map(existingFetchedDetails -> {
                            existingFetchedDetails.forEach(stockDetails::remove);
                            return stockDetails;
                        }))
                .flatMap(stockSymbols -> {
                    Map<String, Boolean> stockSymbol = stockSymbols.stream()
                            /*.filter(s -> !s.contains("LLOYDSE-RE"))
                            .filter(s -> !s.contains("M&MFIN"))
                            .filter(s -> !s.contains("SURANAT&P"))
                            .filter(s -> !s.contains("ARE&M"))
                            .filter(s -> !s.contains("M&M"))
                            .filter(s -> !s.contains("GMRP&UI"))
                            .filter(s -> !s.contains("J&KBANK"))*/
                            .filter(s -> !s.contains("HOVS"))
                            .filter(s -> !s.contains("SWANENERGY"))
                            .filter(s -> !s.contains("GEPIL"))
                            //.filter(s -> !s.contains("&"))
                            .map(s -> Pair.of(s, true))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    stockSymbolCache.putAll(stockSymbol);
                    if (stockSymbolCache.isEmpty()) {
                        return Mono.empty();
                    }
                    return Mono.justOrEmpty(stockSymbols);
                })
                .doOnNext(stockSymbols -> log.info("Active positioned stock symbol loaded to fetch the data from NSE. count: {} symbols: {}", stockSymbols.size(), stockSymbols))
                .block();
    }

    //@Scheduled(fixedRate = 30000)
    public void fetchStockDetailsList() {
        Flux.fromIterable(stockSymbolCache.keySet())
                .window(20)
                .concatMap(batch -> batch
                        .flatMap(symbol -> stockInfoHttpEntryLoader.getStockDetails(symbol)
                                .map(stockInfoDetails -> {
                                    String industry = "";
                                    if (MapUtils.isNotEmpty(stockInfoDetails.getStockInfo())) {
                                        industry = stockInfoDetails.getStockInfo()
                                                .get(symbol)
                                                .getIndustryInfo()
                                                .getIndustry();
                                    }
                                    return Map.entry(industry, symbol);
                                })
                        )
                        // filter out entries with null/empty industry
                        .filter(entry -> entry.getKey() != null && !entry.getKey().isEmpty())
                        .collectMultimap(Map.Entry::getKey, Map.Entry::getValue)
                        .flatMapMany(multimap -> Flux.fromIterable(multimap.entrySet()))
                        .concatMap(entry -> {
                            String industry = entry.getKey();
                            List<String> newSymbols = new ArrayList<>(entry.getValue());

                            return sectorWiseStockDataService.get(industry)
                                    .defaultIfEmpty(SectorWiseStockDetails.builder()
                                            .key(SectorWiseStockKey.builder().key(industry).build())
                                            .stocks(new ArrayList<>())
                                            .build()
                                    )
                                    .flatMap(existingSector -> {
                                        Set<String> combinedSymbols = new HashSet<>(existingSector.getStocks());
                                        combinedSymbols.addAll(newSymbols);
                                        existingSector.setStocks(new ArrayList<>(combinedSymbols));

                                        return sectorWiseStockDataService.save(existingSector)
                                                .doOnSuccess(savedSector -> {
                                                    newSymbols.forEach(stockSymbolCache::remove);
                                                    log.info("Updated sector: {} with {} stocks, removed {} symbols from cache",
                                                            savedSector.getKey().getKey(),
                                                            savedSector.getStocks().size(),
                                                            newSymbols.size());
                                                })
                                                .doOnNext(details -> log.info("List of stocks remained to be fetched. stockCount :{}", stockSymbolCache.size()));
                                    });
                        })
                )
                .subscribe();
    }


}
