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
import com.stock.stock_analyser.engine.InvestmentSignalEngine;
import com.stock.stock_analyser.engine.TechnicalIndicatorEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
    private final OpenAiCommentaryService    openAiService;
    private final StockAnalysisResultPersistenceService analysisResultPersistenceService;

    public Mono<StockAnalysisResult> analyse(StockAnalysisRequest request) {
        if (request == null || request.getSymbol() == null || request.getSymbol().isBlank()) {
            return Mono.error(new IllegalArgumentException("Stock symbol must be provided."));
        }

        String symbol      = request.getSymbol().toUpperCase().trim();
        int lookbackDays   = request.getLookbackDays() > 0 ? request.getLookbackDays() : FULL_TRADING_DAYS;
        log.info("Starting analysis for {} — lookbackDays={}", symbol, lookbackDays);

        StockHistoryKey key = StockHistoryKey.builder().key(symbol).build();

        return stockHistoryDataService.get(key)
                .flatMap(stockHistory -> {
                    // Check if DB data already covers the requested lookback window
                    LocalDate requiredFrom = requiredFromDate(lookbackDays);
                    TreeMap<LocalDate, StockHistoryDetails> details = stockHistory.getStockHistoryDetails();
                    boolean hasFullCoverage = details != null
                            && !details.isEmpty()
                            && !details.firstKey().isAfter(requiredFrom);

                    if (hasFullCoverage) {
                        log.info("Cassandra already covers {}d window for {} (earliest: {}). Skipping fetch.",
                                lookbackDays, symbol, details.firstKey());
                        return Mono.just(stockHistory);
                    }

                    log.info("Cassandra data for {} does not cover {}d window (earliest: {}). Auto-fetching in 3-month chunks.",
                            symbol, lookbackDays,
                            details == null || details.isEmpty() ? "N/A" : details.firstKey());
                    return autoFetchMissingChunks(symbol, lookbackDays, details)
                            .doOnNext(sh -> log.info("Chunked fetch done for {}. total records: {}",
                                    symbol, sh.getStockHistoryDetails().size()));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("No data in Cassandra for {}. Auto-fetching in 3-month chunks.", symbol);
                    return autoFetchMissingChunks(symbol, lookbackDays, new TreeMap<>());
                }))
                .flatMap(stockHistory -> runAnalysis(stockHistory, symbol, request.isIncludeAiCommentary()))
                .flatMap(analysisResultPersistenceService::persist);
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

    private Mono<StockAnalysisResult> runAnalysis(StockHistory stockHistory, String symbol, boolean includeAi) {
        TreeMap<LocalDate, StockHistoryDetails> rawMap = stockHistory.getStockHistoryDetails();

        if (rawMap == null || rawMap.isEmpty()) {
            return Mono.just(StockAnalysisResult.builder()
                    .symbol(symbol)
                    .analysisDate(LocalDate.now())
                    .windowStatus("MISSING")
                    .windowMessage("No candles found in Cassandra for " + symbol +
                            ". Auto-fetch via NextApi was attempted but returned empty data.")
                    .requiredFrom(requiredFromDate(RECOMMENDED_TRADING_DAYS))
                    .build());
        }

        LocalDate today       = LocalDate.now();
        LocalDate dataFrom    = rawMap.firstKey();
        LocalDate dataTo      = rawMap.lastKey();

        // Required start dates for each tier (calculated backwards from today)
        LocalDate requiredFull        = requiredFromDate(FULL_TRADING_DAYS);
        LocalDate requiredRecommended = requiredFromDate(RECOMMENDED_TRADING_DAYS);
        LocalDate requiredMinimum     = requiredFromDate(MINIMUM_TRADING_DAYS);

        // ── Window status ────────────────────────────────────────────────────
        String windowStatus;
        String windowMessage;

        if (dataFrom.isAfter(requiredMinimum)) {
            // Even minimum is not covered — refuse analysis
            windowStatus  = "INSUFFICIENT";
            windowMessage = String.format(
                "ANALYSIS BLOCKED. Minimum %d trading days of data required (from %s). " +
                "Your data starts at %s — missing ~%d calendar days. " +
                "Fetch earlier history via /stockHistoryFromNextApi with from=%s.",
                MINIMUM_TRADING_DAYS, requiredMinimum,
                dataFrom, daysBetween(dataFrom, requiredMinimum),
                requiredMinimum.minusDays(30)  // suggest fetching slightly earlier
            );

            return Mono.just(StockAnalysisResult.builder()
                    .symbol(symbol)
                    .analysisDate(today)
                    .totalDataPoints(rawMap.size())
                    .dataFrom(dataFrom)
                    .dataTo(dataTo)
                    .requiredFrom(requiredMinimum)
                    .windowStatus(windowStatus)
                    .windowMessage(windowMessage)
                    .recommendation(InvestmentRecommendation.builder()
                            .action("NO_DATA")
                            .rationale("Insufficient data. " + windowMessage)
                            .build())
                    .build());

        } else if (dataFrom.isAfter(requiredRecommended)) {
            // Partial — between reliable (60d) and recommended (200d)
            windowStatus  = "PARTIAL";
            windowMessage = String.format(
                "PARTIAL data: SMA200 unavailable. " +
                "Data available from %s (%d candles). " +
                "For full SMA200 analysis, need data from %s (≈%d more calendar days). " +
                "Fetch earlier data to unlock all indicators.",
                dataFrom, rawMap.size(),
                requiredRecommended, daysBetween(dataFrom, requiredRecommended)
            );
        } else if (dataFrom.isAfter(requiredFull)) {
            windowStatus  = "FULL";
            windowMessage = String.format(
                "Good coverage: %d candles from %s to %s. " +
                "For extended 2-year analysis, fetch data from %s.",
                rawMap.size(), dataFrom, dataTo, requiredFull
            );
        } else {
            windowStatus  = "FULL";
            windowMessage = String.format(
                "Excellent coverage: %d candles from %s to %s. All indicators fully computed.",
                rawMap.size(), dataFrom, dataTo
            );
        }

        // ── Use ALL available candles ────────────────────────────────────────
        List<StockHistoryDetails> candles = new ArrayList<>(rawMap.values());
        int total = candles.size();
        log.info("Analysing {} — {} candles from {} to {} | windowStatus={}",
                 symbol, total, dataFrom, dataTo, windowStatus);

        // Compute technical indicators
        TechnicalSignals technical = technicalEngine.compute(candles);

        // Generate recommendation
        InvestmentRecommendation recommendation = signalEngine.recommend(technical);

        // Append window note to data note
        String dataNote = buildDataNote(total, dataFrom, dataTo);

        if (!includeAi) {
            return Mono.just(buildResult(symbol, total, dataFrom, dataTo,
                    requiredRecommended, windowStatus, windowMessage, technical, recommendation, dataNote));
        }

        // Fetch AI commentary and attach
        return openAiService.generateCommentary(symbol, technical, recommendation)
                .map(commentary -> {
                    recommendation.setAiCommentary(commentary);
                    return buildResult(symbol, total, dataFrom, dataTo,
                            requiredRecommended, windowStatus, windowMessage, technical, recommendation, dataNote);
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

    private StockAnalysisResult buildResult(String symbol, int total,
                                            LocalDate dataFrom, LocalDate dataTo,
                                            LocalDate requiredFrom,
                                            String windowStatus, String windowMessage,
                                            TechnicalSignals technical,
                                            InvestmentRecommendation recommendation,
                                            String dataNote) {
        return StockAnalysisResult.builder()
                .symbol(symbol)
                .analysisDate(LocalDate.now())
                .totalDataPoints(total)
                .dataFrom(dataFrom)
                .dataTo(dataTo)
                .requiredFrom(requiredFrom)
                .windowStatus(windowStatus)
                .windowMessage(windowMessage)
                .technical(technical)
                .recommendation(recommendation)
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

