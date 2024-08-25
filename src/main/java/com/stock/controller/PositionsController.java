package com.stock.controller;

import com.stock.dto.Positions;
import com.stock.dto.PositionsStockInfo;
import com.stock.dto.key.PositionsKey;
import com.stock.service.PositionsIntegrator;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping(PositionsController.ENDPOINT)
public class PositionsController {

    public static final String ENDPOINT = "/stock/investment/v1.0";

    @Autowired
    private PositionsIntegrator integrator;

    @PostMapping(value = "/getPositionDetailsById", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<Positions>>> getPositionsById(@RequestBody PositionsKey key) throws Exception {
        return Mono.justOrEmpty(ResponseEntity.ok(integrator.get(key)));
    }

    @GetMapping(value = "/getAllActivePositionDetails", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<List<Positions>>>> getAllPositions() throws Exception {
        return Mono.justOrEmpty(ResponseEntity.ok(integrator.getAllActivePositions()));
    }

    @PostMapping(value = "/savePositionDetailsById", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<Positions>>> save(@RequestBody Positions positions) throws Exception {
        return Mono.justOrEmpty(ResponseEntity.ok(integrator.save(positions)));
    }

    @PostMapping(value = "/savePositionDetailsWithoutBasketId", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<Positions>>> saveWithRandomBasketId(@RequestBody Positions positions) throws Exception {
        return Mono.justOrEmpty(ResponseEntity.ok(integrator.save(positions)));
    }

    @PutMapping(value = "/updatePositionDetailsById", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<Positions>>> update(@RequestBody Positions positions) throws Exception {
        return Mono.justOrEmpty(ResponseEntity.ok(integrator.update(positions)));
    }

    @PostMapping(value = "/saveAllPositions", produces = MediaType.APPLICATION_JSON_VALUE)
    @Hidden
    public Mono<ResponseEntity<Mono<Positions>>> saveAll(@RequestBody List<PositionsStockInfo> positionsStockInfos) throws Exception {
        return Mono.justOrEmpty(ResponseEntity.ok(integrator.saveAll(positionsStockInfos)));
    }
}
