package com.stock.controller;

import com.stock.dto.NiftyIndexStockDetails;
import com.stock.service.FetchNiftyFiftyIndexSTockDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(FetchNiftyFiftyIndexStockData.ENDPOINT)
public class FetchNiftyFiftyIndexStockData {

    public static final String ENDPOINT = "/stock/investment/v1.0";

    @Autowired
    private FetchNiftyFiftyIndexSTockDetailsService integrator;

    @GetMapping(value = "/niftyFiftyIndex", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<NiftyIndexStockDetails>>> get() throws Exception {

        return Mono.justOrEmpty(ResponseEntity.ok(integrator.getNiftyIndexStockDetails()));
    }
}
