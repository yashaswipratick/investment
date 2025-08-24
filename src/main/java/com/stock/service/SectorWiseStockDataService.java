package com.stock.service;

import com.stock.dto.SectorWiseStockDetails;
import com.stock.dto.StockHistory;
import com.stock.dto.StockHistoryRequest;
import com.stock.dto.key.SectorWiseStockKey;
import com.stock.repository.SectorWiseStockDataDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SectorWiseStockDataService {

    @Autowired
    private SectorWiseStockDataDataRepository repository;

    public Mono<SectorWiseStockDetails> save(SectorWiseStockDetails details) {
        if (details == null) {
            log.error("Provide sector wise stock info details are wrong. Update Skipped, details: {} ", details );
            return Mono.empty();
        }
        return repository.save(details);
    }

    public Flux<SectorWiseStockDetails> saveAll(List<SectorWiseStockDetails> details) {
        if (details == null) {
            log.error("Provide sector wise stock info details are wrong. Update Skipped, details: {} ", details );
            return Flux.empty();
        }
        return repository.saveAll(details);
    }

    public Mono<SectorWiseStockDetails> get(String key) {
        if (key == null) {
            log.error("Provide sector wise stock info key are wrong. Update Skipped, details: {} ", key );
            return Mono.empty();
        }
        return repository.findById(SectorWiseStockKey.builder().key(key).build());
    }

    public Mono<String> getAllStock() {
        return repository.findAll()
                .flatMap(details -> Flux.fromIterable(details.getStocks())) // flatten List<String> to Flux<String>
                .collect(Collectors.toSet()) // collect unique stock symbols
                .map(set -> {
                    log.info("Total Stock details fetched: count = {}", set.size());
                    return String.join(",", set);
                });
    }

    public Mono<List<String>> getAllSet() {
        return repository.findAll()
                .flatMap(details -> Flux.fromIterable(details.getStocks())) // flatten List<String> to Flux<String>
                .collect(Collectors.toList()); // collect unique stock symbols;
    }

    public Mono<List<SectorWiseStockDetails>> getAll() {
        return repository.findAll()
                .collect(Collectors.toList());
    }
}
