package com.stock.controller;

import com.stock.dto.StocksDailyChange;
import com.stock.service.StockDailyChangeIntegrator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping(StockDailyChangeController.ENDPOINT)
public class StockDailyChangeController {

    public static final String ENDPOINT = "/stock/investment/v1.0";

    @Autowired
    private StockDailyChangeIntegrator integrator;

    @GetMapping(value = "/stockPositionDailyChangeDetails/{key}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<StocksDailyChange>>> getDailyChangeById(
            @PathVariable String key,
            @RequestParam boolean forceUpdate) throws Exception {
        return Mono.justOrEmpty(ResponseEntity.ok(integrator.get(key, forceUpdate)));
    }

    @PutMapping(value = "/updateStockPositionDailyChangeDetails", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<StocksDailyChange>>> update(@RequestBody StocksDailyChange stocksDailyChange) throws Exception {
        return Mono.justOrEmpty(ResponseEntity.ok(integrator.update(stocksDailyChange)));
    }

    @PostMapping(value = "/saveStockPositionDailyChangeDetails", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<StocksDailyChange>>> save(@RequestBody StocksDailyChange stocksDailyChange) throws Exception {
        return Mono.justOrEmpty(ResponseEntity.ok(integrator.save(stocksDailyChange)));
    }

    @DeleteMapping(value = "/deleteStockPositionDailyChangeDetails", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<Void>>> deleteDailyChangeById(@PathVariable String key) throws Exception {
        if (StringUtils.isEmpty(key)) {
            log.error("key should not be null. key: {} ", key);
            return Mono.empty();
        }
        return Mono.justOrEmpty(ResponseEntity.ok(integrator.delete(key)));
    }
}
