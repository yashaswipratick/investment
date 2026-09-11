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
     *   252 = 1 year  →  generates: 1Y + 6M
     *   504 = 2 years →  generates: 2Y + 1Y + 6M
     *   756 = 3 years →  generates: 3Y + 2Y + 1Y + 6M  (RECOMMENDED)
     *
     * Period thresholds (trading days):
     *   6M = 126  |  1Y = 252  |  2Y = 504  |  3Y = 756
     *
     * Default: 756 (generates all 4 periods)
     */
    @Builder.Default
    private int lookbackDays = 756;

    /**
     * If true, an OpenAI commentary will be appended to the result.
     * Default: true. Set false to skip AI call and get faster response.
     */
    @Builder.Default
    private boolean includeAiCommentary = true;
}
