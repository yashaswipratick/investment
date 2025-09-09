package com.stock.service;

import com.stock.dto.SectorWiseStockDetails;
import com.stock.dto.key.SectorWiseStockKey;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SectorWiseStockDataIntegrator {

    @Autowired
    private StockDescriptionIntegrator stockDescriptionIntegrator;

    @Autowired
    private StockDetailsIntegrator stockDetailsIntegrator;

    @Autowired
    private SectorWiseStockDataService sectorWiseStockDataService;

    /*public Mono<List<SectorWiseStockDetails>> upsert() {

        return stockDescriptionIntegrator.getAll()
                .flatMapMany(stockDescriptionDetails -> Flux.fromIterable(stockDescriptionDetails.keySet()))
                .flatMap(symbol -> stockDetailsIntegrator.getStockDetailForProvidedSymbol(symbol)
                        .map(stockInfoDetails -> {
                            String industry = stockInfoDetails.getStockInfo()
                                    .get(symbol)
                                    .getIndustryInfo()
                                    .getIndustry();
                            return Map.entry(industry, symbol); // industry → symbol
                        })
                )
                .collectMultimap(Map.Entry::getKey, Map.Entry::getValue) // industry → symbols
                .map(multimap -> multimap.entrySet()
                        .stream()
                        .map(entry -> SectorWiseStockDetails.builder()
                                .key(SectorWiseStockKey.builder()
                                        .key(entry.getKey()) // industry name
                                        .build())
                                .stocks(new ArrayList<>(entry.getValue())) // list of stock symbols
                                .build())
                        .collect(Collectors.toList()) // return List<SectorWiseStockDetails>
                )
                .flatMap(sectorWiseStockDetails -> sectorWiseStockDataService.saveAll(sectorWiseStockDetails).collectList());
    }*/

    public Mono<List<SectorWiseStockDetails>> upsert() {
        return stockDescriptionIntegrator.getAll()
                .flatMapMany(stockDescriptionDetails -> Flux.fromIterable(stockDescriptionDetails.keySet()))
                .window(20) // ✅ process in batches of 20 symbols
                .concatMap(batch -> batch
                        .flatMap(symbol -> stockDetailsIntegrator.getStockDetailForProvidedSymbol(symbol)
                                .map(stockInfoDetails -> {
                                    String industry = stockInfoDetails.getStockInfo()
                                            .get(symbol)
                                            .getIndustryInfo()
                                            .getIndustry();
                                    return Map.entry(industry, symbol); // industry → symbol
                                })
                        )
                        .collectMultimap(Map.Entry::getKey, Map.Entry::getValue)
                        .map(multimap -> multimap.entrySet()
                                .stream()
                                .map(entry -> SectorWiseStockDetails.builder()
                                        .key(SectorWiseStockKey.builder()
                                                .key(entry.getKey()) // industry name
                                                .build())
                                        .stocks(new ArrayList<>(entry.getValue()))
                                        .build())
                                .collect(Collectors.toList())
                        )
                        .flatMapMany(sectorWiseStockDataService::saveAll) // ✅ persist batch
                )
                .collectList(); // ✅ combine all persisted batches into final List<SectorWiseStockDetails>
    }


    public Mono<SectorWiseStockDetails> get(String sector) {
        if (sector == null) {
            log.error("Provided sector key is invalid. Get Skipped, details: {}", sector);
            return Mono.empty();
        }

        return sectorWiseStockDataService.get(sector)
                .switchIfEmpty(
                        Mono.defer(() ->
                                upsert()
                                        .flatMapMany(Flux::fromIterable)
                                        .filter(detail -> sector.equals(detail.getKey().getKey()))
                                        .next() // take the first matching
                        )
                );
    }

    public Mono<String> getAllStocks() {

        return sectorWiseStockDataService.getAllStock();
    }

    public Mono<List<SectorWiseStockDetails>> getAll() {

        return sectorWiseStockDataService.getAll();
    }

    public Mono<String> getAllStock() {
        return sectorWiseStockDataService.getAllStock();
    }

    public Mono<List<String>> getAllSet() {
        return sectorWiseStockDataService.getAllSet();
    }

    public Mono<SectorWiseStockDetails> save(SectorWiseStockDetails details) {
        return sectorWiseStockDataService.save(details);
    }

    public Mono<Map<String, List<String>>> getSectorWiseStock() {

        return sectorWiseStockDataService.getAll()
                .flatMapMany(Flux::fromIterable)
                .collect(Collectors.toMap(
                        s -> s.getKey().getKey(),
                        SectorWiseStockDetails::getStocks,
                        (existing, replacement) -> {
                            existing.addAll(replacement); // merge stock lists
                            return existing;
                        }
                ));
    }

    public Mono<List<String>> getAllSector() {
        return sectorWiseStockDataService.getAllSectors();
    }
}
