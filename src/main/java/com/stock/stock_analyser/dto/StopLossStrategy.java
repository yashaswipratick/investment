package com.stock.stock_analyser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Answers: "What is the stop loss? What do I do if it is hit?"
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StopLossStrategy {

    /** Hard stop-loss price — exit immediately if this level is breached on a daily close */
    private Double stopLossPrice;

    /** % loss from the entry price to the stop-loss */
    private Double stopLossPct;

    /**
     * Type of stop loss being recommended.
     * HARD_STOP    — fixed price, exit immediately if breached
     * TRAILING_STOP — move up as price rises (lock in profits progressively)
     * MENTAL_STOP  — soft level; monitor closely before exiting
     */
    private String stopLossType;

    /** Exact instruction when stop is triggered: "Exit 100% immediately" etc. */
    private String actionOnStopHit;

    /**
     * What to do AFTER the stop is hit to recover capital.
     * e.g. "Move capital to a sector ETF while waiting for INFY to stabilise above ₹1,155"
     */
    private String recoveryPlan;

    /**
     * The specific condition that would allow re-entry after stop was hit.
     * e.g. "Re-enter only after RSI recovers above 50 AND price closes above ₹1,155 with volume"
     */
    private String reEntryCondition;

    /**
     * Maximum portfolio allocation advised for this position given the current risk.
     * e.g. "Max 5% of portfolio — high risk, downtrend active"
     */
    private String maxAllocationAdvice;
}
