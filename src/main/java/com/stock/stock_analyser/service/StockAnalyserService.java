package com.stock.stock_analyser.service;

import com.stock.dto.StockHistory;
import com.stock.dto.StockHistoryDetails;
import com.stock.dto.key.StockHistoryKey;
import com.stock.service.StockHistoryDataIntegrator;
import com.stock.service.StockHistoryDataService;
import com.stock.stock_analyser.dto.InvestmentRecommendation;
import com.stock.stock_analyser.dto.StockAnalysisRequest;
import com.stock.stock_analyser.dto.StockAnalysisResult;
import com.stock.stock_analyser.dto.TechnicalSignals;
import com.stock.stock_analyser.dto.EntryTiming;
import com.stock.stock_analyser.dto.PeriodProjection;
import com.stock.stock_analyser.dto.StopLossStrategy;
import com.stock.stock_analyser.engine.InvestmentSignalEngine;
import com.stock.stock_analyser.engine.ProjectionEngine;
import com.stock.stock_analyser.engine.TechnicalIndicatorEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Orchestrates the full stock analysis flow.
 *
 * ── Minimum data windows (trading days, calculated backwards from today) ──
 *
 *   INDICATOR           MIN TRADING DAYS   REASON
 *   ─────────────────────────────────────────────────────────────────────────
 *   RSI-14              15                 14 diffs + seed
 *   MACD (EMA12/26)     35                 EMA26 seed + EMA9 signal
 *   Bollinger Bands 20  20                 20-period SMA + std-dev
 *   SMA-50              50                 50 data points
 *   ADX-14              29                 2 × period + 1
 *   SMA-200             200                200 data points  ← full analysis
 *
 *   MINIMUM for any analysis : 35 trading days  (~50 calendar days)
 *   MINIMUM for reliable     : 60 trading days  (~85 calendar days)
 *   RECOMMENDED (all signals): 200 trading days (~280 calendar days)
 *   FULL window              : 365 trading days (~510 calendar days)  ← used here
 *
 * The service always uses ALL data available in Cassandra, validates coverage,
 * and reports exactly what window is present vs. required.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockAnalyserService {

    // Trading days to calendar day multipliers (NSE ≈ 252 trading days/year)
    // Using 1.4x as the calendar-day multiplier to be conservative
    private static final int FULL_TRADING_DAYS      = 365;   // ~2 years buffer
    private static final int RECOMMENDED_TRADING_DAYS = 200; // SMA200 requires this
    private static final int RELIABLE_TRADING_DAYS  = 60;    // All except SMA200
    private static final int MINIMUM_TRADING_DAYS   = 35;    // MACD + RSI at least

    // Calendar days = trading days × 1.4 (accounts for weekends + holidays)
    private static final double CALENDAR_MULTIPLIER = 1.4;


    private final StockHistoryDataService    stockHistoryDataService;
    private final StockHistoryDataIntegrator stockHistoryDataIntegrator;
    private final TechnicalIndicatorEngine   technicalEngine;
    private final InvestmentSignalEngine     signalEngine;
    private final ProjectionEngine           projectionEngine;
    private final OpenAiCommentaryService    openAiService;
    private final StockAnalysisResultPersistenceService analysisResultPersistenceService;

    /**
     * Analyses a stock and returns results for ALL applicable periods in one call.
     *
     * Period matrix based on lookbackDays in the request:
     *   lookbackDays ≥ 756  →  3Y + 2Y + 1Y + 6M  (4 results)
     *   lookbackDays ≥ 504  →  2Y + 1Y + 6M         (3 results)
     *   lookbackDays ≥ 252  →  1Y + 6M               (2 results)
     *   lookbackDays < 252  →  6M only                (1 result)
     *
     * Each period uses only its own candle window (e.g. 2Y analysis uses last 504 candles)
     * so indicators are computed on the relevant historical context, not the full dataset.
     *
     * Results are stored in Cassandra with primary key: (symbol, period_label, analysis_date).
     * The API returns a map of period → StockAnalysisResult.
     */
    public Mono<Map<String, StockAnalysisResult>> analyse(StockAnalysisRequest request) {
        if (request == null || request.getSymbol() == null || request.getSymbol().isBlank()) {
            return Mono.error(new IllegalArgumentException("Stock symbol must be provided."));
        }

        String symbol    = request.getSymbol().toUpperCase().trim();
        int lookbackDays = request.getLookbackDays() > 0 ? request.getLookbackDays() : FULL_TRADING_DAYS;
        log.info("Starting multi-period analysis for {} — lookbackDays={}", symbol, lookbackDays);

        StockHistoryKey key = StockHistoryKey.builder().key(symbol).build();

        return stockHistoryDataService.get(key)
                .flatMap(stockHistory -> {
                    LocalDate today        = LocalDate.now();
                    LocalDate requiredFrom = requiredFromDate(lookbackDays);
                    TreeMap<LocalDate, StockHistoryDetails> details = stockHistory.getStockHistoryDetails();

                    // ── Check 1: does the DB go back far enough? ──────────────
                    boolean coversHistory = details != null
                            && !details.isEmpty()
                            && !details.firstKey().isAfter(requiredFrom);

                    // ── Check 2: is the DB up-to-date? ────────────────────────
                    // NSE publishes data after market close (typically by 6 PM IST).
                    // Allow a 3-day tolerance to account for weekends/holidays.
                    // If the latest candle is older than 3 calendar days, the DB is stale
                    // and we must fetch the recent missing days from NSE.
                    boolean isRecent = details != null
                            && !details.isEmpty()
                            && !details.lastKey().isBefore(today.minusDays(3));

                    if (coversHistory && isRecent) {
                        log.info("Cassandra covers full window for {} (earliest: {}, latest: {}). Skipping NSE fetch.",
                                symbol, details.firstKey(), details.lastKey());
                        return Mono.just(stockHistory);
                    }

                    if (coversHistory && !isRecent) {
                        // History is sufficient but recent candles are missing — fetch only the recent gap
                        log.info("Cassandra data for {} is stale (latest: {}). Fetching recent missing days.",
                                symbol, details.lastKey());
                    } else {
                        log.info("Cassandra data for {} does not cover {}d window (earliest: {}). Fetching missing history.",
                                symbol, lookbackDays, details == null || details.isEmpty() ? "N/A" : details.firstKey());
                    }

                    return autoFetchMissingChunks(symbol, lookbackDays, details)
                            .doOnNext(sh -> log.info("Fetch done for {}. total records: {}",
                                    symbol, sh.getStockHistoryDetails().size()));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("No data in Cassandra for {}. Auto-fetching in 3-month chunks.", symbol);
                    return autoFetchMissingChunks(symbol, lookbackDays, new TreeMap<>());
                }))
                .flatMap(stockHistory ->
                        runMultiPeriodAnalysis(stockHistory, symbol, lookbackDays, request.isIncludeAiCommentary()));
    }

    /**
     * Determines which periods to analyse based on how much data was requested.
     * Always includes 6M. Adds 1Y/2Y/3Y if lookbackDays is large enough.
     *
     * Returns a map of periodLabel → tradingDays, in descending order (3Y first).
     */
    private Map<String, Integer> resolvePeriods(int lookbackDays) {
        Map<String, Integer> periods = new LinkedHashMap<>();
        if (lookbackDays >= BARS_3Y) periods.put("3Y", BARS_3Y);
        if (lookbackDays >= BARS_2Y) periods.put("2Y", BARS_2Y);
        if (lookbackDays >= BARS_1Y) periods.put("1Y", BARS_1Y);
        periods.put("6M", BARS_6M);   // always included
        return periods;
    }

    private static final int BARS_6M = 126;
    private static final int BARS_1Y = 252;
    private static final int BARS_2Y = 504;
    private static final int BARS_3Y = 756;

    /**
     * Runs analysis for each applicable period and persists all results.
     * Each period gets its own candle slice (last N candles), so indicators
     * reflect the relevant historical window, not the full dataset.
     */
    private Mono<Map<String, StockAnalysisResult>> runMultiPeriodAnalysis(
            StockHistory stockHistory, String symbol, int lookbackDays, boolean includeAi) {

        Map<String, Integer> periods = resolvePeriods(lookbackDays);
        log.info("Running {} period(s) for {}: {}", periods.size(), symbol, periods.keySet());

        List<StockHistoryDetails> allCandles = new ArrayList<>(
                stockHistory.getStockHistoryDetails() != null
                        ? stockHistory.getStockHistoryDetails().values()
                        : List.of());

        // Run each period sequentially (to avoid hammering OpenAI in parallel)
        Mono<Map<String, StockAnalysisResult>> chain = Mono.just(new LinkedHashMap<>());

        for (Map.Entry<String, Integer> entry : periods.entrySet()) {
            String periodLabel = entry.getKey();
            int tradingBars    = entry.getValue();

            chain = chain.flatMap(resultMap -> {
                // Take only the last `tradingBars` candles for this period's analysis
                int available = allCandles.size();
                List<StockHistoryDetails> candles = available <= tradingBars
                        ? allCandles
                        : allCandles.subList(available - tradingBars, available);

                log.info("[{}][{}] Analysing with {} candles (requested {} bars)",
                         symbol, periodLabel, candles.size(), tradingBars);

                return runAnalysis(candles, stockHistory, symbol, periodLabel, tradingBars, includeAi)
                        .flatMap(analysisResultPersistenceService::persist)
                        .doOnNext(r -> resultMap.put(periodLabel, r))
                        .thenReturn(resultMap);
            });
        }

        return chain;
    }

    /**
     * Converts lookbackDays → calendar fromDate and delegates to the chunked
     * NSE fetcher (3-month windows, 3-second inter-call delay).
     *
     * Example — lookbackDays=750:
     *   calendarDays = round(750 × 1.4) = 1050 days back from today
     *   chunks = 12 × 3-month windows  (Jan-Mar, Apr-Jun, …)
     *   Each chunk = 1 NSE call with a manageable date range
     */
    private Mono<StockHistory> autoFetchMissingChunks(String symbol,
                                                      int lookbackDays,
                                                      TreeMap<LocalDate, StockHistoryDetails> existingData) {
        LocalDate today    = LocalDate.now();
        LocalDate fromDate = today.minusDays(Math.round(lookbackDays * CALENDAR_MULTIPLIER));

        log.info("Auto-fetching chunked data for {} | lookbackDays={} | calendarFrom={} → {}",
                symbol, lookbackDays, fromDate, today);

        return stockHistoryDataIntegrator.fetchMissingChunkedFromNextApiAndSave(
                        symbol, "EQ", fromDate, today, 3,
                        existingData != null ? existingData : new TreeMap<>())
                .switchIfEmpty(Mono.error(new RuntimeException(
                        "NSE returned no data for: " + symbol +
                        ". Symbol may be invalid or NSE session cookie may have expired.")));
    }

    /**
     * Runs analysis for a specific period using the provided candle slice.
     * Each period gets its own subset of candles (e.g. 1Y uses last 252 candles)
     * so indicators reflect the relevant historical window.
     */
    private Mono<StockAnalysisResult> runAnalysis(List<StockHistoryDetails> candles,
                                                   StockHistory stockHistory,
                                                   String symbol,
                                                   String periodLabel,
                                                   int tradingBars,
                                                   boolean includeAi) {
        // Derive dates from the sliced candle list
        TreeMap<LocalDate, StockHistoryDetails> rawMap = stockHistory.getStockHistoryDetails();

        if (candles == null || candles.isEmpty()) {
            return Mono.just(StockAnalysisResult.builder()
                    .symbol(symbol).periodLabel(periodLabel)
                    .analysisDate(LocalDate.now())
                    .windowStatus("MISSING")
                    .windowMessage("No candles for period " + periodLabel)
                    .build());
        }

        int total          = candles.size();
        LocalDate today    = LocalDate.now();
        // dataFrom / dataTo from the full map so window message makes sense
        LocalDate dataFrom = rawMap != null && !rawMap.isEmpty() ? rawMap.firstKey() : today;
        LocalDate dataTo   = rawMap != null && !rawMap.isEmpty() ? rawMap.lastKey()  : today;

        LocalDate requiredRecommended = requiredFromDate(RECOMMENDED_TRADING_DAYS);
        LocalDate requiredMinimum     = requiredFromDate(MINIMUM_TRADING_DAYS);

        // ── Window status ────────────────────────────────────────────────────
        String windowStatus;
        String windowMessage;

        if (total < MINIMUM_TRADING_DAYS) {
            windowStatus  = "INSUFFICIENT";
            windowMessage = String.format(
                "[%s] ANALYSIS BLOCKED. Only %d candles available (minimum %d required).",
                periodLabel, total, MINIMUM_TRADING_DAYS);
            return Mono.just(StockAnalysisResult.builder()
                    .symbol(symbol).periodLabel(periodLabel).analysisDate(today)
                    .totalDataPoints(total).dataFrom(dataFrom).dataTo(dataTo)
                    .requiredFrom(requiredMinimum).windowStatus(windowStatus).windowMessage(windowMessage)
                    .recommendation(InvestmentRecommendation.builder()
                            .action("NO_DATA").rationale("Insufficient data. " + windowMessage).build())
                    .build());
        } else if (total < RECOMMENDED_TRADING_DAYS) {
            windowStatus  = "PARTIAL";
            windowMessage = String.format(
                "[%s] PARTIAL: %d candles available. SMA200 unavailable (needs %d candles).",
                periodLabel, total, RECOMMENDED_TRADING_DAYS);
        } else {
            windowStatus  = "FULL";
            windowMessage = String.format(
                "[%s] Full coverage: %d candles. All indicators computed.", periodLabel, total);
        }

        log.info("[{}][{}] Analysing {} candles | windowStatus={}", symbol, periodLabel, total, windowStatus);

        TechnicalSignals technical         = technicalEngine.compute(candles, tradingBars);
        InvestmentRecommendation recommendation = signalEngine.recommend(technical);

        // ── New: projections, entry timing, stop-loss strategy ───────────────
        java.util.List<PeriodProjection> projections = projectionEngine.computeProjections(technical, recommendation);
        EntryTiming     entryTiming      = projectionEngine.computeEntryTiming(technical, recommendation);
        StopLossStrategy stopLossStrategy = projectionEngine.computeStopLossStrategy(technical, recommendation);

        String dataNote = buildDataNote(total, dataFrom, dataTo);

        if (!includeAi) {
            return Mono.just(buildResult(symbol, periodLabel, total, dataFrom, dataTo,
                    requiredRecommended, windowStatus, windowMessage, technical, recommendation,
                    projections, entryTiming, stopLossStrategy, dataNote));
        }

        return openAiService.generateCommentary(symbol + " [" + periodLabel + "]", technical, recommendation, candles)
                .map(commentary -> {
                    recommendation.setAiCommentary(commentary);
                    return buildResult(symbol, periodLabel, total, dataFrom, dataTo,
                            requiredRecommended, windowStatus, windowMessage, technical, recommendation,
                            projections, entryTiming, stopLossStrategy, dataNote);
                });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Converts trading days to a calendar date by going back from today.
     * Uses 1.4× multiplier: 200 trading days ≈ 280 calendar days.
     */
    private LocalDate requiredFromDate(int tradingDays) {
        long calendarDays = Math.round(tradingDays * CALENDAR_MULTIPLIER);
        return LocalDate.now().minusDays(calendarDays);
    }

    private long daysBetween(LocalDate earlier, LocalDate later) {
        return java.time.temporal.ChronoUnit.DAYS.between(earlier, later);
    }

    private StockAnalysisResult buildResult(String symbol, String periodLabel, int total,
                                            LocalDate dataFrom, LocalDate dataTo,
                                            LocalDate requiredFrom,
                                            String windowStatus, String windowMessage,
                                            TechnicalSignals technical,
                                            InvestmentRecommendation recommendation,
                                            java.util.List<PeriodProjection> projections,
                                            EntryTiming entryTiming,
                                            StopLossStrategy stopLossStrategy,
                                            String dataNote) {
        return StockAnalysisResult.builder()
                .symbol(symbol)
                .periodLabel(periodLabel)
                .analysisDate(LocalDate.now())
                .totalDataPoints(total)
                .dataFrom(dataFrom)
                .dataTo(dataTo)
                .requiredFrom(requiredFrom)
                .windowStatus(windowStatus)
                .windowMessage(windowMessage)
                .technical(technical)
                .recommendation(recommendation)
                .projections(projections)
                .entryTiming(entryTiming)
                .stopLossStrategy(stopLossStrategy)
                .dataNote(dataNote)
                .build();
    }

    private String buildDataNote(int total, LocalDate from, LocalDate to) {
        if (total < RELIABLE_TRADING_DAYS) {
            return String.format(
                "⚠️ Only %d candles (%s to %s). " +
                "SMA50, SMA200, ADX unreliable. " +
                "Fetch %d+ days for better accuracy.",
                total, from, to, RECOMMENDED_TRADING_DAYS);
        }
        if (total < RECOMMENDED_TRADING_DAYS) {
            return String.format(
                "ℹ️ %d candles (%s to %s). " +
                "SMA200 not available. " +
                "Fetch from %s for full indicator set.",
                total, from, to, requiredFromDate(RECOMMENDED_TRADING_DAYS));
        }
        return String.format(
            "✅ %d candles (%s to %s). All indicators computed.",
            total, from, to);
    }
}

