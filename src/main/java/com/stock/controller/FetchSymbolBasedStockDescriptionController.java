package com.stock.controller;

import com.stock.dto.StockDescriptionDetails;
import com.stock.service.StockDescriptionIntegrator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(FetchSymbolBasedStockDescriptionController.ENDPOINT)
public class FetchSymbolBasedStockDescriptionController {

    public static final String ENDPOINT = "/stock/investment/v1.0";

    @Autowired
    private StockDescriptionIntegrator integrator;

    @GetMapping(value = "/stockDescription", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<StockDescriptionDetails>>> get() throws Exception {

        return Mono.justOrEmpty(ResponseEntity.ok(integrator.getStockDetailForProvidedSymbol("symbol")));
    }
}
