package com.stock.service;

import com.stock.dto.StockDescriptionDetails;
import com.stock.entryloader.StockDescriptionHttpEntryLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;
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
                                StockDescriptionDetails::getSymbol,
                                description -> description,
                                (existing, replacement) -> replacement
                        ))
                );
    }

    public Mono<Map<String, StockDescriptionDetails>> getAll() {
        return stockDescriptionService.getAll()
                .collectList()
                .flatMap(stockList -> {
                    if (stockList.isEmpty()) {
                        return upsert();
                    }
                    return Mono.just(stockList.stream()
                            .collect(Collectors.toMap(
                                    StockDescriptionDetails::getSymbol,
                                    description -> description,
                                    (existing, replacement) -> replacement
                            )));
                });
    }

    public Mono<Map<String, StockDescriptionDetails>> get(String symbol) {
        return stockDescriptionService.get(symbol)
                .map(stockList -> {
                    Map<String, StockDescriptionDetails> map = new HashMap<>(1);
                    map.put(stockList.getSymbol(), stockList);
                    return map;
                });
    }

    public Mono<Long> importFromList(List<StockDescriptionDetails> descriptionDetails) {
        if (descriptionDetails == null || descriptionDetails.isEmpty()) {
            return Mono.just(0L);
        }

        LocalDateTime now = LocalDateTime.now();
        Map<String, StockDescriptionDetails> dedupedBySymbol = new LinkedHashMap<>();

        for (StockDescriptionDetails detail : descriptionDetails) {
            if (detail == null || detail.getSymbol() == null || detail.getSymbol().isBlank()) {
                continue;
            }
            StockDescriptionDetails normalized = detail.toBuilder()
                    .symbol(detail.getSymbol().trim())
                    .companyName(detail.getCompanyName() == null ? null : detail.getCompanyName().trim())
                    .series(detail.getSeries() == null ? null : detail.getSeries().trim())
                    .isInNumber(detail.getIsInNumber() == null ? null : detail.getIsInNumber().trim())
                    .createdDate(detail.getCreatedDate() == null ? now : detail.getCreatedDate())
                    .build();
            dedupedBySymbol.put(normalized.getSymbol(), normalized);
        }

        if (dedupedBySymbol.isEmpty()) {
            return Mono.just(0L);
        }

        return stockDescriptionService.saveAll(new ArrayList<>(dedupedBySymbol.values())).count();
    }
}
