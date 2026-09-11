package com.stock.stock_analyser.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BreakoutResult {
    /** True if a breakout was detected */
    private boolean breakoutFound;
    /** Date of the breakout bar */
    private String breakoutDate;
    /** Resistance level that was broken */
    private Double breakoutLevel;
    /** Closing price on the breakout day */
    private Double breakoutPrice;
    /** How many trading days ago the breakout occurred */
    private Integer daysAgoBreakout;
    /** Did the price come back to test the breakout level? */
    private boolean retested;
    /** Did the price bounce back above the breakout level after the retest? */
    private boolean retestConfirmed;
    /** Date of the retest bar */
    private String retestDate;
    /** BULLISH_BREAKOUT_CONFIRMED | FRESH_BREAKOUT | BREAKOUT_RETEST_IN_PROGRESS | NONE */
    private String signal;
    /** Plain-English explanation for non-traders */
    private String explanation;
}
