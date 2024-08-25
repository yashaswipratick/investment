package com.stock.controller;

import com.stock.dto.PositionalAggregatedDailyCalculator;
import com.stock.dto.key.PositionalAggregatedDailyCalculatorKey;
import com.stock.service.PositionalAggregatedDailyCalculatorIntegrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@CrossOrigin(origins = "http://localhost:3000")
@RequestMapping(PositionalAggregatedDailyCalculatorController.ENDPOINT)
public class PositionalAggregatedDailyCalculatorController {

    public static final String ENDPOINT = "/stock/investment/v1.0";

    @Autowired
    private PositionalAggregatedDailyCalculatorIntegrator integrator;

    @PostMapping(value = "/positionalAggregatedDailyCalculator", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<PositionalAggregatedDailyCalculator>>> getDailyPositionalAggregatedPnl(
            @RequestBody PositionalAggregatedDailyCalculatorKey key,
            @RequestParam boolean forceUpdate) throws Exception {
        return Mono.justOrEmpty(ResponseEntity.ok(integrator.get(key, forceUpdate)));
    }

    @PutMapping(value = "/updatePositionalAggregatedDailyCalculator", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<PositionalAggregatedDailyCalculator>>> update(@RequestBody PositionalAggregatedDailyCalculator positionalAggregatedDailyCalculator) throws Exception {
        return Mono.justOrEmpty(ResponseEntity.ok(integrator.update(positionalAggregatedDailyCalculator)));
    }

    @PostMapping(value = "/savePositionalAggregatedDailyCalculator", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<PositionalAggregatedDailyCalculator>>> save(@RequestBody PositionalAggregatedDailyCalculator positionalAggregatedDailyCalculator) throws Exception {
        return Mono.justOrEmpty(ResponseEntity.ok(integrator.save(positionalAggregatedDailyCalculator)));
    }

   /* @DeleteMapping(value = "/deletePositionalDailyChangeNetIncome", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<Void>>> deleteDailyChangeById(@PathVariable String key) throws Exception {
        if (StringUtils.isEmpty(key)) {
            log.error("key should not be null. key: {} ", key);
            return Mono.empty();
        }
        return Mono.justOrEmpty(ResponseEntity.ok(integrator.delete(key)));
    }*/
}
