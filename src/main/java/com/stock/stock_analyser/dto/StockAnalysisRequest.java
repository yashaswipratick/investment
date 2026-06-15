package com.stock.stock_analyser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for stock analysis endpoint.
 *
 * The analysis window is always calculated backwards from today
 * based on the minimum data requirements for each technical indicator.
 * You do NOT need to provide a date range — the service validates
 * whether enough data exists in Cassandra and tells you exactly
 * what is missing if the window is incomplete.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAnalysisRequest {

    /** NSE stock symbol, e.g. INFY, TCS, RELIANCE */
    private String symbol;

    /**
     * Number of historical trading days to load for analysis.
     * The service converts this to calendar days (×1.4) and fetches from NSE in 3-month chunks.
     *
     *   250 = ~1 year  (minimum — SMA200 barely covered, not recommended)
     *   500 = ~2 years (acceptable)
     *   750 = ~3 years (RECOMMENDED — reliable SMA200, Golden/Death Cross, full market cycle)
     *
     * Default: 750
     */
    @Builder.Default
    private int lookbackDays = 750;

    /**
     * If true, an OpenAI commentary will be appended to the result.
     * Default: true. Set false to skip AI call and get faster response.
     */
    @Builder.Default
    private boolean includeAiCommentary = true;
}
