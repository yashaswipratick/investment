package com.stock.stock_analyser.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Output of CandlestickEngine — deterministically computed price-action signals.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandlestickSignals {

    /** Detected candlestick/chart patterns on the last 1-3 candles. e.g. ["HAMMER","BULLISH_ENGULFING"] */
    private List<String> candlestickPatterns;

    /** Price gap events. e.g. ["GAP_UP +1.2% (open ₹1045 vs prev close ₹1032)"] */
    private List<String> gapSignals;

    /** % price change over last 5 trading days */
    private Double momentum5dPct;

    /** % price change over last 10 trading days */
    private Double momentum10dPct;

    /**
     * Volume confirmation signal for today's candle.
     * e.g. "HIGH_VOLUME_BULLISH — volume spike on up day (confirms buying)"
     */
    private String volumeConfirmation;

    /**
     * Volume trend over last 10 days: what % of volume occurred on up-days vs down-days.
     * e.g. "BULLISH — 68% volume on up-days"
     */
    private String volumeTrend10d;

    /** True if today's candle range is entirely inside yesterday's candle */
    private boolean insideBar;
}
