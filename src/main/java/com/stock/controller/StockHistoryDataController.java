package com.stock.controller;

import com.stock.dto.StockHistory;
import com.stock.dto.StockHistoryDetails;
import com.stock.dto.StockHistoryRequest;
import com.stock.dto.StockInfoDetails;
import com.stock.service.StockDetailsIntegrator;
import com.stock.service.StockHistoryDataIntegrator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "http://localhost:3000")
@RequestMapping(StockHistoryDataController.ENDPOINT)
public class StockHistoryDataController {

    public static final String ENDPOINT = "/stock/investment/v1.0";

    @Autowired
    private StockHistoryDataIntegrator integrator;

    @PostMapping(value = "/stockHistoryDetail", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<StockHistory>>> get(@RequestBody StockHistoryRequest stockHistory) throws Exception {
        return Mono.justOrEmpty(ResponseEntity.ok(integrator.fetchAndSave(stockHistory)));
    }

    @GetMapping(value = "/stockHistoryDetail/{sector}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<Map<String, StockHistory>>>> fetchStockHistoryForGivenSector(@PathVariable String sector) throws Exception {
        return Mono.justOrEmpty(ResponseEntity.ok(integrator.fetchStockDetailsForGivenSector(sector)));
    }

    @PostMapping(value = "/stockHistoryDetailsFromListOfSectors", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<Map<String, StockHistory>>>> fetchStockHistoryForGivenSector(@RequestBody List<String> sector) throws Exception {
        return Mono.justOrEmpty(ResponseEntity.ok(integrator.fetchStockDetailsForGivenSectors(sector)));
    }

    @PostMapping(value = "/stockHistoryDetailCSV", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<StockHistory>>> getCSVData(@RequestBody StockHistoryRequest stockHistory) throws Exception {
        return Mono.justOrEmpty(ResponseEntity.ok(integrator.fetchCSVAndSave(stockHistory)));
    }
}
