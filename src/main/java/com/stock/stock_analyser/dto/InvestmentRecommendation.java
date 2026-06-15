package com.stock.stock_analyser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Final investment recommendation derived from all signals.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentRecommendation {

    /** Overall action to take */
    private String action;              // BUY | HOLD | SELL | AVOID

    /** Confidence score 0-100 */
    private int confidenceScore;

    /** Suggested entry price zone */
    private Double entryPriceLow;
    private Double entryPriceHigh;

    /** Suggested exit (target) price - based on resistance + volatility */
    private Double targetPrice;

    /** Stop-loss price - below which exit to limit downside */
    private Double stopLossPrice;

    /** Expected upside % if target is hit */
    private Double potentialUpsidePct;

    /** Downside % if stop-loss triggers */
    private Double potentialDownsidePct;

    /** Risk/reward ratio = potentialUpside / potentialDownside */
    private Double riskRewardRatio;

    /** Human-readable rationale summarising signals */
    private String rationale;

    /** OpenAI-generated commentary (if requested) */
    private String aiCommentary;

    /** Timeframe for this trade suggestion */
    private String timeframe;          // SHORT_TERM (days-weeks) | MEDIUM_TERM (weeks-months) | LONG_TERM (months+)
}

