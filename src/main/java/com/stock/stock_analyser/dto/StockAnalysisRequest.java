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
     * If true, an OpenAI commentary will be appended to the result.
     * Default: true. Set false to skip AI call and get faster response.
     */
    @Builder.Default
    private boolean includeAiCommentary = true;
}
