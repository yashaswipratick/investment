package com.stock.controller;

import com.stock.dto.StockInfoDetails;
import com.stock.service.StockDetailsIntegrator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@CrossOrigin(origins = "http://localhost:3000")
@RequestMapping(FetchRealTimeStockData.ENDPOINT)
public class FetchRealTimeStockData {

    public static final String ENDPOINT = "/stock/investment/v1.0";

    @Autowired
    private StockDetailsIntegrator integrator;

    @GetMapping(value = "/stockDetail/{key}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<StockInfoDetails>>> get(@PathVariable String key) throws Exception {
        return Mono.justOrEmpty(ResponseEntity.ok(integrator.getStockDetailForProvidedSymbol(key)));
    }

    @PutMapping(value = "/updateStockDetail", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<StockInfoDetails>>> update(@RequestBody StockInfoDetails stockInfoDetails) throws Exception {
        return Mono.justOrEmpty(ResponseEntity.ok(integrator.update(stockInfoDetails)));
    }
}
