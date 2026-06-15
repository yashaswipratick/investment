package com.stock.dto;

import lombok.*;

/**
 * Result for a single date-range chunk fetched from NSE.
 * status: SUCCESS | SKIPPED | NSE_ERROR | PARTIAL
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
public class ChunkFetchStatus {

    /** Inclusive chunk start, format dd-MM-yyyy */
    private String fromDate;

    /** Inclusive chunk end, format dd-MM-yyyy */
    private String toDate;

    /** Records returned by NSE for this chunk. */
    private int recordsFetched;

    /**
     * Approximate expected trading days (weekday count only, no holiday deduction).
     * Used as an upper-bound for fill-rate computation.
     */
    private int expectedTradingDays;

    /**
     * recordsFetched / expectedTradingDays * 100.
     * Below 70 is flagged as PARTIAL.
     */
    private double fillRatePct;

    /** SUCCESS | SKIPPED | NSE_ERROR | PARTIAL */
    private String status;

    private String message;
}

