package com.stock.controller;

import com.stock.dto.StockInvestmentCalculatorDetails;
import com.stock.dto.StockInvestmentRequestDetails;
import com.stock.service.StockInvestMentCalculatorIntegrator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping(StockInvestmentCalculatorController.ENDPOINT)
public class StockInvestmentCalculatorController {

    public static final String ENDPOINT = "/stock/investment/v1.0";

    @Autowired
    private StockInvestMentCalculatorIntegrator integrator;

    @PostMapping(value = "/stockInvestmentCalculator", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<List<StockInvestmentCalculatorDetails>>>> get(@RequestBody StockInvestmentRequestDetails stockInvestmentRequestDetails) {

        return Mono.justOrEmpty(ResponseEntity.ok(integrator.calculate(stockInvestmentRequestDetails)));
    }
}
