package com.stock.dto;

import lombok.*;

/**
 * Request payload for the historical-data backfill endpoints.
 *
 * Example JSON:
 * {
 *   "stockSymbol": "INFY",
 *   "series":      "EQ",
 *   "yearsBack":   3,
 *   "chunkMonths": 6
 * }
 *
 * - yearsBack  : how many calendar years of history to ensure in the DB  (default 3)
 * - chunkMonths: size of each NSE fetch window in calendar months         (default 6)
 *
 * If {@code stockSymbol} is null/blank the endpoint will process every stock
 * that exists in the sector specified via the path variable.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
public class BackfillRequest {

    /** NSE stock symbol, e.g. "INFY". Null/blank → process all stocks in sector. */
    private String stockSymbol;

    /** NSE series, e.g. "EQ". Defaults to "EQ" if blank. */
    private String series;

    /**
     * How many years of history to guarantee.
     * The service will back-fill data all the way to {@code today - yearsBack}.
     * Default: 3
     */
    @Builder.Default
    private int yearsBack = 3;

    /**
     * Size of each NSE HTTP fetch window expressed in calendar months.
     * A smaller value is safer (less risk of NSE rate-limiting); 6 months is a good default.
     * Default: 6
     */
    @Builder.Default
    private int chunkMonths = 6;

    /**
     * Delay in seconds between consecutive NSE calls for the same stock.
     * Increase this if you observe HTTP 429 / 403 responses.
     * Default: 3
     */
    @Builder.Default
    private int delaySecondsBetweenChunks = 3;

    /**
     * Delay in seconds between different stocks when processing a whole sector.
     * Default: 5
     */
    @Builder.Default
    private int delaySecondsBetweenStocks = 5;
}

