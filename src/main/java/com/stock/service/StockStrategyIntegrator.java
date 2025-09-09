package com.stock.service;

import com.stock.calculator.StockStrategyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class StockStrategyIntegrator {

    @Autowired
    private StockStrategyService stockStrategyService;

    public Mono<List<String>> stocksDailyBreakoutWithVolume(Integer breakoutDays) {
        return stockStrategyService.stocksDailyBreakoutWithVolume(breakoutDays);
    }

    public Mono<List<String>> detectUptrendStocksAdaptive() {
        return stockStrategyService.detectUptrendStocksAdaptive();
    }

    public Mono<List<String>> stockCrossing200SMA() {
        return stockStrategyService.stocksCrossing200SMA();
    }

    public Mono<List<String>> getCommonStocks(Integer breakoutDays) {
        return stockStrategyService.getCommonStocks(breakoutDays);
    }

}
