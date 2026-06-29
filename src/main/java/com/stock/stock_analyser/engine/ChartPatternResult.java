package com.stock.stock_analyser.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ChartPatternResult {
    /** Pattern name: Double Top, Ascending Triangle, etc. */
    private String patternName;
    /** BULLISH | BEARISH | NEUTRAL */
    private String signal;
    /** HIGH | MEDIUM | LOW */
    private String confidence;
    /** Short technical description */
    private String description;
    /** Plain-English explanation for non-traders */
    private String whatItMeans;
    /** What action the pattern suggests */
    private String tradingSignal;
}
