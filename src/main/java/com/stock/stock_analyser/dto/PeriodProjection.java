package com.stock.stock_analyser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Expected return projection for a single time horizon.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeriodProjection {

    /** Time horizon label: 3M | 6M | 9M | 1Y | 2Y | 3Y | 5Y */
    private String horizon;

    /** Expected price target for this horizon */
    private Double targetPrice;

    /** Expected % return from current price (positive = gain, negative = loss) */
    private Double expectedReturnPct;

    /**
     * Scenario description explaining the basis for this projection.
     * e.g. "Mean-reversion bounce to mid-Bollinger Band (₹1,152)"
     */
    private String scenario;

    /**
     * Confidence level for this projection: HIGH | MEDIUM | LOW
     * Shorter horizons tend to have lower confidence (more noise).
     * Longer horizons depend on trend sustainability.
     */
    private String confidence;

    // ── Volatility-based range (±1σ) ─────────────────────────────────────────
    /**
     * Bull case return % (base + 1σ volatility for the horizon).
     * Probability: ~84% of outcomes are below this level.
     */
    private Double bullCasePct;

    /**
     * Bear case return % (base - 1σ volatility for the horizon).
     * Probability: ~16% of outcomes are below this level.
     */
    private Double bearCasePct;

    /** Bull case price target */
    private Double bullCasePrice;

    /** Bear case price target */
    private Double bearCasePrice;

    /**
     * Annualised historical volatility used for this projection (%).
     * e.g. 35.0 means the stock typically swings ±35% per year (1σ).
     */
    private Double annualizedVolatilityPct;
}
