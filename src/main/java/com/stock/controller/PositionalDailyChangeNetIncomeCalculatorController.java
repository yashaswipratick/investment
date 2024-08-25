package com.stock.controller;

import com.stock.dto.PositionalDailyNetIncome;
import com.stock.service.PositionalDailyChangeNetIncomeCalculatorIntegrator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping(PositionalDailyChangeNetIncomeCalculatorController.ENDPOINT)
public class PositionalDailyChangeNetIncomeCalculatorController {

    public static final String ENDPOINT = "/stock/investment/v1.0";

    @Autowired
    private PositionalDailyChangeNetIncomeCalculatorIntegrator integrator;

    @GetMapping(value = "/stockPositionalDailyChangeNetIncome/{key}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<PositionalDailyNetIncome>>> getDailyChangeById(
            @PathVariable String key,
            @RequestParam boolean forceUpdate) throws Exception {
        return Mono.justOrEmpty(ResponseEntity.ok(integrator.get(key, forceUpdate)));
    }

    @PutMapping(value = "/updatePositionalDailyChangeNetIncome", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<PositionalDailyNetIncome>>> update(@RequestBody PositionalDailyNetIncome positionalDailyNetIncome) throws Exception {
        return Mono.justOrEmpty(ResponseEntity.ok(integrator.update(positionalDailyNetIncome)));
    }

    @PostMapping(value = "/savePositionalDailyChangeNetIncome", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<PositionalDailyNetIncome>>> save(@RequestBody PositionalDailyNetIncome positionalDailyNetIncome) throws Exception {
        return Mono.justOrEmpty(ResponseEntity.ok(integrator.save(positionalDailyNetIncome)));
    }

    @DeleteMapping(value = "/deletePositionalDailyChangeNetIncome", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<Void>>> deleteDailyChangeById(@PathVariable String key) throws Exception {
        if (StringUtils.isEmpty(key)) {
            log.error("key should not be null. key: {} ", key);
            return Mono.empty();
        }
        return Mono.justOrEmpty(ResponseEntity.ok(integrator.delete(key)));
    }
}
