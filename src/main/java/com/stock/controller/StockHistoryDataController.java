package com.stock.controller;

import com.stock.dto.StockHistory;
import com.stock.dto.StockHistoryDetails;
import com.stock.dto.StockHistoryRequest;
import com.stock.dto.StockHistoryCsvRequest;
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

    @Autowired
    private com.stock.service.StockHistoryDataService stockHistoryDataService;

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

    /**
     * Fetches stock history from NSE NextApi (GetQuoteApi) using cookie from cookie.txt
     * merged with a fresh session cookie, then persists to Cassandra stock_history table.
     *
     * Request body example:
     * {
     *   "stockSymbol": "INFY",
     *   "series":      "EQ",
     *   "from":        "15-06-2025",
     *   "to":          "15-06-2026"
     * }
     *
     * Internally calls:
     *   GET https://www.nseindia.com/api/NextApi/apiClient/GetQuoteApi
     *       ?functionName=getHistoricalTradeData
     *       &symbol={stockSymbol}&series={series}
     *       &fromDate={from}&toDate={to}&csv=true
     */
    @PostMapping(value = "/stockHistoryFromNextApi", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Mono<StockHistory>>> fetchStockHistoryFromNextApi(
            @RequestBody StockHistoryRequest stockHistoryRequest) {
        return Mono.justOrEmpty(ResponseEntity.ok(integrator.fetchAndSaveFromNextApi(stockHistoryRequest)));
    }

    @PostMapping(value = "/stockHistoryCSV", produces = "text/csv")
    public Mono<ResponseEntity<byte[]>> downloadStockHistoryCsv(
            @RequestBody StockHistoryCsvRequest request) {
        if (request == null || request.getStockName() == null || request.getStockName().isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        String stockName = request.getStockName().trim().toUpperCase(java.util.Locale.ROOT);
        return stockHistoryDataService.getCsv(stockName)
                .map(csv -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("text/csv"))
                        .header("Content-Disposition", "attachment; filename=\"" + stockName + "_stock_history.csv\"")
                        .body(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .onErrorResume(java.util.NoSuchElementException.class,
                        error -> Mono.just(ResponseEntity.notFound().build()))
                .onErrorResume(IllegalArgumentException.class,
                        error -> Mono.just(ResponseEntity.badRequest().build()));
    }

    @PostMapping(value = "/stockHistoryCSV/bulk", produces = "application/zip")
    public Mono<ResponseEntity<byte[]>> downloadStockHistoryCsvBulk(
            @RequestBody StockHistoryCsvRequest request) {
        if (request == null || request.getStockNames() == null || request.getStockNames().isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return stockHistoryDataService.getCsvZip(request.getStockNames())
                .map(zip -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("application/zip"))
                        .header("Content-Disposition", "attachment; filename=\"stock_history_csv.zip\"")
                        .body(zip))
                .onErrorResume(java.util.NoSuchElementException.class,
                        error -> Mono.just(ResponseEntity.notFound().build()))
                .onErrorResume(IllegalArgumentException.class,
                        error -> Mono.just(ResponseEntity.badRequest().build()));
    }
}
