package com.stock.service;

import com.stock.dto.StockDescriptionDetails;
import com.stock.dto.StockInfoDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class StockDescriptionIntegrator {

    @Autowired
    private StockDescriptionService stockDetailsService;

    @Autowired
    private StockDescriptionHttpEntryLoader entryLoader;

    public Mono<StockDescriptionDetails> getStockDetailForProvidedSymbol(String symbol) {
        return stockDetailsService.get(symbol)
                .switchIfEmpty(Mono.defer(() -> entryLoader.getStockDetails()
                        .collect(Collectors.toList())
                        .flatMap(detail -> stockDetailsService.saveAll(detail)
                                .collect(Collectors.groupingBy(StockDescriptionDetails::getSymbol))
                                .flatMap(stockDescriptionDetailsMap -> Mono.justOrEmpty(stockDescriptionDetailsMap.get(symbol).get(0))))));
    }

    public Flux<StockDescriptionDetails> upsert() {
        return entryLoader.getStockDetails()
                .flatMap(stockInfoDetails -> stockDetailsService.save(stockInfoDetails))
                .switchIfEmpty(Flux.empty());
    }

    public Mono<Void> deleteStock(String symbol) {
        return stockDetailsService.delete(symbol);
    }
}
