package com.stock.controller;

import com.stock.service.StockStrategyIntegrator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@CrossOrigin(origins = "http://localhost:3000")
@RequestMapping(StockStrategyController.ENDPOINT)
public class StockStrategyController {

    public static final String ENDPOINT = "/stock/investment/v1.0";

    @Autowired
    private StockStrategyIntegrator stockStrategyIntegrator;

    @GetMapping(value = "/daily-breakout-stocks/{breakoutDays}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<List<String>>>> getStocksDailyBreakoutWithVolume(@PathVariable Integer breakoutDays) {
        return Mono.justOrEmpty(ResponseEntity.ok(stockStrategyIntegrator.stocksDailyBreakoutWithVolume(breakoutDays)));
    }

    @GetMapping(value = "/detect-uptrend-stocks", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<List<String>>>> detectUptrendStocksAdaptive() {
        return Mono.justOrEmpty(ResponseEntity.ok(stockStrategyIntegrator.detectUptrendStocksAdaptive()));
    }

    @GetMapping(value = "/stock-crossing-200-SMA", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<List<String>>>> stockCrossing200SMA() {
        return Mono.justOrEmpty(ResponseEntity.ok(stockStrategyIntegrator.stockCrossing200SMA()));
    }

    @GetMapping(value = "/stock-satisfying-strategies/{breakoutDays}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<List<String>>>> stockSatisfyingStrategies(@PathVariable Integer breakoutDays) {
        return Mono.justOrEmpty(ResponseEntity.ok(stockStrategyIntegrator.getCommonStocks(breakoutDays)));
    }
}
