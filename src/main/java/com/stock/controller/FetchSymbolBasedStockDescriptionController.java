package com.stock.controller;

import com.stock.dto.StockDescriptionDetails;
import com.stock.service.StockDescriptionIntegrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping(FetchSymbolBasedStockDescriptionController.ENDPOINT)
public class FetchSymbolBasedStockDescriptionController {

    public static final String ENDPOINT = "/stock/investment/v1.0";

    @Autowired
    private StockDescriptionIntegrator integrator;

    @GetMapping(value = "/stockDescription", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, StockDescriptionDetails>>> getAll() {
        return integrator.getAll()
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("GET /stockDescription failed: {}", e.getMessage());
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.SERVICE_UNAVAILABLE)
                            .<Map<String, StockDescriptionDetails>>build());
                });
    }

    @GetMapping(value = "/stockDescriptionNew", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, StockDescriptionDetails>>> upsert() {
        return integrator.upsert()
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("GET /stockDescriptionNew failed: {}", e.getMessage());
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.SERVICE_UNAVAILABLE)
                            .<Map<String, StockDescriptionDetails>>build());
                });
    }

    @PostMapping(value = "/stockDescription/import", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> importFromList(@RequestBody List<StockDescriptionDetails> stockDescriptions) {
        int requestedCount = stockDescriptions == null ? 0 : stockDescriptions.size();
        return integrator.importFromList(stockDescriptions)
                .map(savedCount -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("requestedCount", requestedCount);
                    response.put("savedCount", savedCount);
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(e -> {
                    log.error("POST /stockDescription/import failed: {}", e.getMessage());
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.SERVICE_UNAVAILABLE)
                            .<Map<String, Object>>build());
                });
    }

    @GetMapping(value = "/stockDescription/{key}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, StockDescriptionDetails>>> getByKey(@PathVariable String key) {
        return integrator.get(key)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("GET /stockDescription/{} failed: {}", key, e.getMessage());
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.SERVICE_UNAVAILABLE)
                            .<Map<String, StockDescriptionDetails>>build());
                });
    }
}
