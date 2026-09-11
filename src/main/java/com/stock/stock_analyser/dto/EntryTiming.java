package com.stock.stock_analyser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Answers: "Is NOW a good time to invest? If not, what should I wait for?"
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntryTiming {

    /**
     * Overall investment timing signal.
     * INVEST_NOW    — current price is in or below the ideal entry zone with trend confirmation
     * WAIT_FOR_DIP  — stock is good but price is above ideal entry; wait for pullback
     * WAIT_FOR_TRIGGER — downtrend/sideways; wait for a specific price/indicator event
     * AVOID         — strong downtrend with no near-term reversal signal
     */
    private String signal;

    /** true if conditions are currently met to invest */
    private boolean goodTimeToInvest;

    /** The specific price/indicator event to watch before entering */
    private String entryTrigger;

    /** What you are currently waiting for */
    private String currentSituation;

    /** The single most important price level or event to monitor */
    private String keyLevelToWatch;

    /**
     * Risk profile for this entry.
     * AGGRESSIVE — buy now, tight stop
     * MODERATE   — wait for trigger, wider stop
     * CONSERVATIVE — wait for confirmed trend reversal
     */
    private String riskProfile;
}
