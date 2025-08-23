package com.stock.service;

import com.stock.dto.StockDescriptionDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class StockDescriptionIntegrator {

    @Autowired
    private StockDescriptionService stockDescriptionService;

    @Autowired
    private StockDescriptionHttpEntryLoader entryLoader;

    public Mono<Map<String, StockDescriptionDetails>> upsert() {
        return entryLoader.getStockDetails()
                .flatMap(stockDescriptionDetails -> stockDescriptionService.saveAll(stockDescriptionDetails).collectList())
                .map(stockList -> stockList.stream()
                        .collect(Collectors.toMap(
                                StockDescriptionDetails::getSymbol,    // key = symbol
                                description -> description,            // value = object
                                (existing, replacement) -> replacement // resolve duplicates
                        ))
                );
    }

    public Mono<Map<String, StockDescriptionDetails>> getAll() {
        return stockDescriptionService.getAll()
                .collectList()
                .map(stockList -> stockList.stream()
                        .collect(Collectors.toMap(
                                StockDescriptionDetails::getSymbol,    // key = symbol
                                description -> description,            // value = object
                                (existing, replacement) -> replacement // resolve duplicates
                        ))
                )
                .switchIfEmpty(Mono.defer(this::upsert));
    }

    public Mono<Map<String, StockDescriptionDetails>> get(String symbol) {
        return stockDescriptionService.get(symbol)
                .map(stockList -> {
                    Map<String, StockDescriptionDetails> map = new HashMap<>(1);
                    map.put(stockList.getSymbol(), stockList);
                    return map;
                });
    }
}
