package com.stock.controller;

import com.stock.dto.SectorWiseStockDetails;
import com.stock.dto.StockHistory;
import com.stock.dto.StockHistoryRequest;
import com.stock.service.SectorWiseStockDataIntegrator;
import com.stock.service.StockHistoryDataIntegrator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@CrossOrigin(origins = "http://localhost:3000")
@RequestMapping(StockWiseStockDataDetailsController.ENDPOINT)
public class StockWiseStockDataDetailsController {

    public static final String ENDPOINT = "/stock/investment/v1.0";

    @Autowired
    private SectorWiseStockDataIntegrator integrator;

    @GetMapping(value = "/sectorWiseStockDetails/{sector}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<SectorWiseStockDetails>>> get(@PathVariable String sector) throws Exception {
        return Mono.justOrEmpty(ResponseEntity.ok(integrator.get(sector)));
    }

    @GetMapping(value = "/sectorWiseStockDetailsList", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<String>>> getAllStocks() throws Exception {
        return Mono.justOrEmpty(ResponseEntity.ok(integrator.getAllStocks()));
    }
}
