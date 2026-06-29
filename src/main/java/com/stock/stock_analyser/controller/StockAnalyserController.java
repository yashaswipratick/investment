package com.stock.stock_analyser.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.dto.key.StockHistoryKey;
import com.stock.repository.StockAnalysisResultRepository;
import com.stock.service.StockHistoryDataService;
import com.stock.stock_analyser.dto.InvestmentRecommendation;
import com.stock.stock_analyser.dto.StockAnalysisRequest;
import com.stock.stock_analyser.dto.StockAnalysisResult;
import com.stock.stock_analyser.dto.TechnicalSignals;
import com.stock.stock_analyser.service.OpenAiCommentaryService;
import com.stock.stock_analyser.service.StockAnalyserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * REST endpoint for stock analysis.
 *
 * POST /stock/investment/v1.0/stockAnalyser/analyse
 * {
 *   "symbol": "INFY",
 *   "lookbackDays": 250,
 *   "includeAiCommentary": true
 * }
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/stock/investment/v1.0/stockAnalyser")
public class StockAnalyserController {

    private final StockAnalyserService       analyserService;
    private final OpenAiCommentaryService    openAiCommentaryService;
    private final StockHistoryDataService    stockHistoryDataService;
    private final StockAnalysisResultRepository resultRepository;
    private final ObjectMapper objectMapper;

    /**
     * Runs full technical analysis for ALL applicable periods in one call.
     *
     * Period matrix (based on lookbackDays in the request):
     *   lookbackDays ≥ 756  →  3Y + 2Y + 1Y + 6M
     *   lookbackDays ≥ 504  →  2Y + 1Y + 6M
     *   lookbackDays ≥ 252  →  1Y + 6M
     *   lookbackDays < 252  →  6M only
     *
     * Response: map of period → StockAnalysisResult
     * {
     *   "3Y": { ... },
     *   "2Y": { ... },
     *   "1Y": { ... },
     *   "6M": { ... }
     * }
     */
    @PostMapping(value = "/analyse", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, StockAnalysisResult>>> analyse(
            @RequestBody StockAnalysisRequest request) {

        return analyserService.analyse(request)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity
                        .badRequest()
                        .body(Collections.singletonMap("error",
                                StockAnalysisResult.builder()
                                        .symbol(request.getSymbol())
                                        .dataNote("Error: " + e.getMessage())
                                        .build()))));
    }

    /**
     * Returns current OpenAI API key validation status.
     *
     * GET /stock/investment/v1.0/stockAnalyser/openai/key-status
     *
     * Example response:
     * {
     *   "keyValid": true,
     *   "validationMessage": "VALID",
     *   "aiCommentaryEnabled": true,
     *   "checkedAt": "2026-06-15T14:30:00Z"
     * }
     *
     * Possible validationMessage values:
     *   VALID                    – key authenticated successfully against OpenAI
     *   INVALID_HTTP_401         – key rejected (unauthorized)
     *   INVALID_HTTP_429         – key valid but rate-limited
     *   VALIDATION_ERROR         – network/runtime error during validation
     *   MISSING_OR_EMPTY_KEY_FILE – file path missing, file not found, or file is empty
     *   NOT_VALIDATED            – service has not started yet
     */
    /**
     * Screener — ranks all analysed stocks by best investment opportunity.
     *
     * GET /stock/investment/v1.0/stockAnalyser/screener?period=1Y&topN=10
     *
     * period: 6M | 1Y | 2Y | 3Y  (default: 1Y)
     * topN:   number of top stocks to return (default: 10)
     *
     * Returns stocks ranked by:
     *   1. Action priority: BUY > HOLD > SELL > AVOID
     *   2. Confidence score (highest first)
     *   3. Risk/reward ratio (highest first)
     *
     * Response includes for each stock:
     *   - symbol, action, confidence, trend, entry zone, target, stop-loss
     *   - entryTiming.goodTimeToInvest
     *   - 1Y projected return %
     */
    /**
     * Fetch latest analysis for a specific symbol across all stored periods.
     * GET /stock/investment/v1.0/stockAnalyser/result/{symbol}
     * Returns: { "1Y": {...}, "2Y": {...}, "6M": {...}, "3Y": {...} }
     */
    @GetMapping(value = "/result/{symbol}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> getResult(@PathVariable String symbol) {
        // Cassandra clustering is (period_label ASC, analysis_date DESC) so the FIRST
        // row per period is always the latest. We collect all rows, then keep only the
        // one with the MAX analysis_date per period to avoid the stale-data bug where
        // collectMap overwrites newer data with older data.
        return resultRepository.findAllByPeriodLabel("1Y")
                .filter(e -> e.getKey().getSymbol().equalsIgnoreCase(symbol))
                .mergeWith(resultRepository.findAllByPeriodLabel("2Y")
                        .filter(e -> e.getKey().getSymbol().equalsIgnoreCase(symbol)))
                .mergeWith(resultRepository.findAllByPeriodLabel("3Y")
                        .filter(e -> e.getKey().getSymbol().equalsIgnoreCase(symbol)))
                .mergeWith(resultRepository.findAllByPeriodLabel("6M")
                        .filter(e -> e.getKey().getSymbol().equalsIgnoreCase(symbol)))
                // Group by periodLabel and keep only the row with the latest analysisDate
                .collectMultimap(e -> e.getKey().getPeriodLabel())
                .map(multimap -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    multimap.forEach((period, rows) -> {
                        // Pick the row with the highest (most recent) analysis_date
                        rows.stream()
                                .max(java.util.Comparator.comparing(e -> e.getKey().getAnalysisDate()))
                                .ifPresent(e -> {
                                    Map<String, Object> m = new LinkedHashMap<>();
                                    m.put("symbol",          e.getKey().getSymbol());
                                    m.put("periodLabel",     e.getKey().getPeriodLabel());
                                    m.put("analysisDate",    e.getKey().getAnalysisDate());
                                    m.put("windowStatus",    e.getWindowStatus());
                                    m.put("windowMessage",   e.getWindowMessage());
                                    m.put("totalDataPoints", e.getTotalDataPoints());
                                    m.put("dataNote",        e.getDataNote());
                                    m.put("dataFrom",        e.getDataFrom());
                                    m.put("dataTo",          e.getDataTo());
                                    try {
                                        if (e.getRecommendationJson() != null)
                                            m.put("recommendation", objectMapper.readValue(e.getRecommendationJson(), Object.class));
                                        if (e.getTechnicalJson() != null)
                                            m.put("technical", objectMapper.readValue(e.getTechnicalJson(), Object.class));
                                        if (e.getProjectionsJson() != null)
                                            m.put("projections", objectMapper.readValue(e.getProjectionsJson(), Object.class));
                                        if (e.getEntryTimingJson() != null)
                                            m.put("entryTiming", objectMapper.readValue(e.getEntryTimingJson(), Object.class));
                                        if (e.getStopLossStrategyJson() != null)
                                            m.put("stopLossStrategy", objectMapper.readValue(e.getStopLossStrategyJson(), Object.class));
                                    } catch (Exception ex) {
                                        log.warn("Failed to parse JSON for {} [{}]: {}", symbol, period, ex.getMessage());
                                    }
                                    result.put(period, m);
                                });
                    });
                    return result;
                })
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError().build()));
    }

    /**
     * Returns all distinct symbols that have been analysed.
     * GET /stock/investment/v1.0/stockAnalyser/symbols
     */
    @GetMapping(value = "/symbols", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<List<String>>> getSymbols() {
        return resultRepository.findAllByPeriodLabel("1Y")
                .map(e -> e.getKey().getSymbol())
                .distinct()
                .sort()
                .collectList()
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.ok(List.of())));
    }

    @GetMapping(value = "/screener", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<List<Map<String, Object>>>> screener(
            @RequestParam(defaultValue = "1Y") String period,
            @RequestParam(defaultValue = "10") int topN) {

        // Cassandra clustering is (period_label, analysis_date DESC) so the first row
        // per symbol is always the latest. We deduplicate here to avoid showing the
        // same symbol twice when it was analysed on multiple days.
        return resultRepository.findAllByPeriodLabel(period)
                // Deduplicate: keep only the LATEST analysis_date per symbol
                .collectMultimap(e -> e.getKey().getSymbol())   // group by symbol
                .flatMapMany(multimap -> {
                    List<com.stock.stock_analyser.dto.StockAnalysisResultEntity> latest = new ArrayList<>();
                    for (var entries : multimap.values()) {
                        entries.stream()
                                .max(Comparator.comparing(e -> e.getKey().getAnalysisDate()))
                                .ifPresent(latest::add);
                    }
                    return reactor.core.publisher.Flux.fromIterable(latest);
                })
                .map(entity -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("symbol",      entity.getKey().getSymbol());
                    row.put("periodLabel", entity.getKey().getPeriodLabel());
                    row.put("analysisDate", entity.getKey().getAnalysisDate());
                    row.put("windowStatus", entity.getWindowStatus());

                    try {
                        if (entity.getRecommendationJson() != null && !entity.getRecommendationJson().isBlank()) {
                            InvestmentRecommendation rec = objectMapper.readValue(
                                    entity.getRecommendationJson(), InvestmentRecommendation.class);
                            row.put("action",           rec.getAction());
                            row.put("confidenceScore",  rec.getConfidenceScore());
                            row.put("entryPriceLow",    rec.getEntryPriceLow());
                            row.put("entryPriceHigh",   rec.getEntryPriceHigh());
                            row.put("targetPrice",      rec.getTargetPrice());
                            row.put("stopLossPrice",    rec.getStopLossPrice());
                            row.put("upsidePct",        rec.getPotentialUpsidePct());
                            row.put("riskReward",       rec.getRiskRewardRatio());
                            row.put("timeframe",        rec.getTimeframe());
                        }
                        if (entity.getTechnicalJson() != null && !entity.getTechnicalJson().isBlank()) {
                            TechnicalSignals tech = objectMapper.readValue(
                                    entity.getTechnicalJson(), TechnicalSignals.class);
                            row.put("currentPrice",   tech.getCurrentPrice());
                            row.put("trendDirection", tech.getTrendDirection());
                            row.put("rsi14",          tech.getRsi14());
                            row.put("macdSignal",     tech.getMacdSignalType());
                            row.put("priceChangePct", tech.getPriceChangePct());
                        }
                        if (entity.getEntryTimingJson() != null && !entity.getEntryTimingJson().isBlank()) {
                            com.stock.stock_analyser.dto.EntryTiming et = objectMapper.readValue(
                                    entity.getEntryTimingJson(), com.stock.stock_analyser.dto.EntryTiming.class);
                            row.put("goodTimeToInvest",  et.isGoodTimeToInvest());
                            row.put("entryTimingSignal", et.getSignal());
                            row.put("entryTrigger",      et.getEntryTrigger());
                        }
                    } catch (Exception e) {
                        log.warn("Screener: failed to parse JSON for {}: {}", entity.getKey().getSymbol(), e.getMessage());
                    }
                    return row;
                })
                .sort(Comparator
                        .comparingInt((Map<String, Object> m) -> {
                            Object a = m.get("action");
                            return switch (a != null ? a.toString() : "") {
                                case "BUY"   -> 0;
                                case "HOLD"  -> 1;
                                case "SELL"  -> 2;
                                case "AVOID" -> 3;
                                default      -> 4;
                            };
                        })
                        .thenComparingInt((Map<String, Object> m) -> {
                            Object c = m.get("confidenceScore");
                            return c instanceof Number n ? -n.intValue() : 0;   // descending
                        })
                        .thenComparingDouble((Map<String, Object> m) -> {
                            Object r = m.get("riskReward");
                            return r instanceof Number n ? -n.doubleValue() : 0; // descending
                        })
                )
                .take(topN)
                .collectList()
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Screener failed: {}", e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    /**
     * Latest close price from Cassandra stock_history table — no external API needed.
     * GET /stock/investment/v1.0/stockAnalyser/latest-price/{symbol}
     */
    @GetMapping(value = "/latest-price/{symbol}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> getLatestPrice(@PathVariable String symbol) {
        StockHistoryKey key = StockHistoryKey.builder().key(symbol.toUpperCase().trim()).build();
        Mono<ResponseEntity<Map<String, Object>>> fetch = stockHistoryDataService.get(key)
                .map(sh -> buildLatestPriceResponse(symbol, sh));
        Mono<ResponseEntity<Map<String, Object>>> empty = Mono.just(
                ResponseEntity.<Map<String, Object>>status(404).build());
        return fetch.switchIfEmpty(empty)
                .onErrorResume(e -> {
                    log.warn("latest-price failed for {}: {}", symbol, e.getMessage());
                    Map<String, Object> err = new LinkedHashMap<>();
                    err.put("error", e.getMessage());
                    return Mono.just(ResponseEntity.<Map<String, Object>>status(502).body(err));
                });
    }

    private ResponseEntity<Map<String, Object>> buildLatestPriceResponse(
            String symbol, com.stock.dto.StockHistory sh) {
        var details = sh.getStockHistoryDetails();
        if (details == null || details.isEmpty()) {
            return ResponseEntity.<Map<String, Object>>status(404).build();
        }
        var latestDate   = details.lastKey();
        var latestCandle = details.get(latestDate);
        Double close     = latestCandle.getClose();
        var prevEntry    = details.lowerEntry(latestDate);
        Double prev      = prevEntry != null ? prevEntry.getValue().getClose() : null;
        double chg       = (close != null && prev != null && prev > 0)
                           ? (close - prev) / prev * 100 : 0;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("symbol",        symbol.toUpperCase());
        m.put("latestDate",    latestDate.toString());
        m.put("closePrice",    close != null ? Math.round(close * 100.0) / 100.0 : null);
        m.put("prevClose",     prev  != null ? Math.round(prev  * 100.0) / 100.0 : null);
        m.put("changePercent", Math.round(chg * 100.0) / 100.0);
        return ResponseEntity.ok(m);
    }

    /**
     * Returns current OpenAI API key validation status.
     *
     * GET /stock/investment/v1.0/stockAnalyser/openai/key-status
     */
    @GetMapping(value = "/openai/key-status", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> openAiKeyStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("keyValid", openAiCommentaryService.isApiKeyValid());
        status.put("validationMessage", openAiCommentaryService.getApiKeyValidationMessage());
        status.put("aiCommentaryEnabled", openAiCommentaryService.isApiKeyValid());
        status.put("checkedAt", Instant.now().toString());
        return Mono.just(ResponseEntity.ok(status));
    }
}
