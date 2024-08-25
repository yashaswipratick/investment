package com.stock.controller;

import com.stock.service.InvestmentIntegrator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.math.BigInteger;
import java.util.Map;

@RestController
@RequestMapping(InvestmentCalculatorController.ENDPOINT)
public class InvestmentCalculatorController {

    public static final String ENDPOINT = "/stock/investment/v1.0";

    @Autowired
    private InvestmentIntegrator integrator;

    /*@Autowired
    private NSETools exchangeTools;*/

    @GetMapping(value = "/investment/{amount}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<Map<String, Double>>>> get(@PathVariable BigInteger amount) throws Exception {

        return Mono.justOrEmpty(ResponseEntity.ok(integrator.invest(amount)));
        //return ResponseEntity.ok(new HashMap<>());
    }
}
