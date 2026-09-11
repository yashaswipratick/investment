package com.stock.controller;

import com.stock.dto.BackfillRequest;
import com.stock.dto.BackfillResult;
import com.stock.service.HistoricalDataBackfillService;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * REST endpoints for chunked historical-data backfill.
 *
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │  Single stock                                                        │
 * │  POST /stock/investment/v1.0/history/backfill                        │
 * │  Body: { "stockSymbol":"INFY", "series":"EQ",                        │
 * │          "yearsBack":3, "chunkMonths":6 }                            │
 * ├─────────────────────────────────────────────────────────────────────┤
 * │  Entire sector                                                       │
 * │  POST /stock/investment/v1.0/history/backfill/sector/{sector}        │
 * │  Body: { "series":"EQ", "yearsBack":3, "chunkMonths":6 }             │
 * ├─────────────────────────────────────────────────────────────────────┤
 * │  Multiple sectors                                                    │
 * │  POST /stock/investment/v1.0/history/backfill/sectors                │
 * │  Body: { "sectors":["IT","BANKING"], "yearsBack":3, "chunkMonths":6} │
 * └─────────────────────────────────────────────────────────────────────┘
 */
@RestController
@CrossOrigin(origins = "http://localhost:3000")
@RequestMapping(HistoricalDataBackfillController.ENDPOINT)
@Slf4j
public class HistoricalDataBackfillController {

    public static final String ENDPOINT = "/stock/investment/v1.0/history";

    @Autowired
    private HistoricalDataBackfillService backfillService;

    // ──────────────────────────────────────────────────────────────────────────
    // Single stock
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Backfill history for a single stock symbol.
     *
     * Example body:
     * {
     *   "stockSymbol": "INFY",
     *   "series":      "EQ",
     *   "yearsBack":   3,
     *   "chunkMonths": 6,
     *   "delaySecondsBetweenChunks": 3
     * }
     *
     * The service will:
     *  1. Check existing DB data for this symbol.
     *  2. Determine how many months/years are missing.
     *  3. Fetch the gap in 6-month chunks sequentially (with 3-s delay each).
     *  4. Validate data reliability and merge into DB.
     *  5. Return a full audit report with per-chunk status.
     */
    @PostMapping(value = "/backfill", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<BackfillResult>> backfillSingleStock(
            @RequestBody BackfillRequest request) {

        log.info("[API] Backfill single stock: symbol={}", request.getStockSymbol());
        return backfillService.backfillStock(request)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.noContent().build());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // All stocks in one sector
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Backfill history for every stock in a sector.
     * Stocks are processed sequentially (one at a time) with a configurable
     * per-stock delay to avoid NSE rate-limiting.
     *
     * Example:
     *   POST /stock/investment/v1.0/history/backfill/sector/IT
     *   Body: { "series":"EQ", "yearsBack":3, "chunkMonths":6,
     *           "delaySecondsBetweenStocks":5 }
     */
    @PostMapping(value = "/backfill/sector/{sector}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<BackfillResult> backfillSector(
            @PathVariable String sector,
            @RequestBody BackfillRequest request) {

        log.info("[API] Sector backfill: sector={}", sector);
        return backfillService.backfillSector(sector, request);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Multiple sectors in one call
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Backfill history for all stocks across multiple sectors.
     *
     * Example body:
     * {
     *   "sectors":     ["IT", "BANKING", "PHARMA"],
     *   "series":      "EQ",
     *   "yearsBack":   3,
     *   "chunkMonths": 6,
     *   "delaySecondsBetweenChunks": 3,
     *   "delaySecondsBetweenStocks": 5
     * }
     */
    @PostMapping(value = "/backfill/sectors", produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<BackfillResult> backfillMultipleSectors(
            @RequestBody BackfillSectorsRequest req) {

        log.info("[API] Multi-sector backfill: sectors={}", req.getSectors());

        BackfillRequest base = BackfillRequest.builder()
                .series(req.getSeries())
                .yearsBack(req.getYearsBack()   > 0 ? req.getYearsBack()   : 3)
                .chunkMonths(req.getChunkMonths() > 0 ? req.getChunkMonths() : 6)
                .delaySecondsBetweenChunks(req.getDelaySecondsBetweenChunks() > 0 ? req.getDelaySecondsBetweenChunks() : 3)
                .delaySecondsBetweenStocks(req.getDelaySecondsBetweenStocks() > 0 ? req.getDelaySecondsBetweenStocks() : 5)
                .build();

        return Flux.fromIterable(req.getSectors())
                .concatMap(sector -> backfillService.backfillSector(sector, base));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Inner DTO for multi-sector request
    // ──────────────────────────────────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BackfillSectorsRequest {
        private List<String> sectors;
        private String series;
        @Builder.Default private int yearsBack                   = 3;
        @Builder.Default private int chunkMonths                 = 6;
        @Builder.Default private int delaySecondsBetweenChunks   = 3;
        @Builder.Default private int delaySecondsBetweenStocks   = 5;
    }
}

