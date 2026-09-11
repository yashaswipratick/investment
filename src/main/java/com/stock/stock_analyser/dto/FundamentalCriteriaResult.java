package com.stock.stock_analyser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Deterministic hard-criteria result used by the Marcus decision engine. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundamentalCriteriaResult {
    private String overallStatus; // PASS | FAIL | UNAVAILABLE
    private CriterionResult revenueGrowth;
    private CriterionResult patMarginTrend;
    private CriterionResult debtToEquity;
    private CriterionResult roe;
    private CriterionResult roce;
    private CriterionResult peValuation;
    private CriterionResult promoterHolding;
    private CriterionResult promoterPledge;
    private int passedCount;
    private int failedCount;
    private int unavailableCount;
    private String summary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CriterionResult {
        private Double value;
        private String status; // PASS | FAIL | UNAVAILABLE
        private String threshold;
        private String reason;
    }
}
