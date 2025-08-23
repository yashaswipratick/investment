package com.stock.controller;

import com.stock.dto.StockDescriptionDetails;
import com.stock.service.StockDescriptionIntegrator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping(FetchSymbolBasedStockDescriptionController.ENDPOINT)
public class FetchSymbolBasedStockDescriptionController {

    public static final String ENDPOINT = "/stock/investment/v1.0";

    @Autowired
    private StockDescriptionIntegrator integrator;

    @GetMapping(value = "/stockDescription", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<Map<String, StockDescriptionDetails>>>> getAll() throws Exception {

        return Mono.justOrEmpty(ResponseEntity.ok(integrator.getAll()));
    }

    @GetMapping(value = "/stockDescriptionNew", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<Map<String, StockDescriptionDetails>>>> upsert() throws Exception {

        return Mono.justOrEmpty(ResponseEntity.ok(integrator.upsert()));
    }

    @GetMapping(value = "/stockDescription/{key}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<Map<String, StockDescriptionDetails>>>> getAll(@PathVariable String key) throws Exception {

        return Mono.justOrEmpty(ResponseEntity.ok(integrator.get(key)));
    }
}
