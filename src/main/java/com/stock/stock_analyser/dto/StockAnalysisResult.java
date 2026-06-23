package com.stock.stock_analyser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Full analysis result returned by the analyser endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAnalysisResult {

    private String symbol;
    /** Analysis period: 6M | 1Y | 2Y | 3Y */
    private String periodLabel;
    private LocalDate analysisDate;
    private int totalDataPoints;         // Total candles used for analysis

    // ── Data Window Info ────────────────────────────────────────────────────
    /** Earliest date present in Cassandra for this symbol */
    private LocalDate dataFrom;
    /** Latest date present in Cassandra for this symbol */
    private LocalDate dataTo;
    /** The date from which data is needed for full SMA200 analysis (today - 200 trading days ≈ today - 280 calendar days) */
    private LocalDate requiredFrom;
    /**
     * FULL       — data covers the required window completely
     * PARTIAL    — data exists but doesn't reach back far enough (missing earlier candles)
     * MISSING    — no data at all
     */
    private String windowStatus;
    /** Human-readable message about the data coverage gap, if any */
    private String windowMessage;

    private TechnicalSignals technical;
    private InvestmentRecommendation recommendation;

    /** Period-wise profit projections: 3M, 6M, 9M, 1Y, 2Y, 3Y, 5Y */
    private java.util.List<PeriodProjection> projections;

    /** Answers: is NOW a good time to invest? What trigger to watch? */
    private EntryTiming entryTiming;

    /** Stop-loss level, action if hit, and recovery/re-entry plan */
    private StopLossStrategy stopLossStrategy;

    /** Any additional warnings or data quality notes */
    private String dataNote;
}
