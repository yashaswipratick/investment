package com.stock.service;

import com.stock.dto.StockDescriptionDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class StockDescriptionIntegrator {

    @Autowired
    private StockDescriptionService stockDetailsService;

    @Autowired
    private StockDescriptionHttpEntryLoader entryLoader;

    public Mono<Map<String, StockDescriptionDetails>> getStockDetailForProvidedSymbol() {
        return entryLoader.getStockDetails()
                .map(stockList -> stockList.stream()
                        .collect(Collectors.toMap(
                                StockDescriptionDetails::getSymbol,    // key = symbol
                                description -> description,            // value = object
                                (existing, replacement) -> replacement // resolve duplicates
                        ))
                );
    }
}
