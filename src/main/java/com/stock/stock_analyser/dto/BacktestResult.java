package com.stock.stock_analyser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Backtested forward-return statistics for the current signal setup.
 *
 * The engine scans historical candles, finds occurrences where the same
 * signal combination (RSI zone + MACD direction + trend + BB position) appeared,
 * then measures what actually happened over the following 3M / 6M / 1Y.
 *
 * This gives probability-backed projections instead of linear extrapolations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestResult {

    /** Number of historical occurrences of a similar signal setup */
    private int occurrences;

    /** Signal fingerprint that was matched (e.g. "RSI_NEUTRAL|MACD_BEARISH|DOWNTREND|BB_NEAR_LOWER") */
    private String signalFingerprint;

    /**
     * Whether the backtest has enough samples to be statistically meaningful.
     * true if occurrences >= 5.
     */
    private boolean statistically_significant;

    /** Per-horizon stats */
    private List<HorizonStat> horizonStats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HorizonStat {
        /** 3M | 6M | 1Y */
        private String horizon;
        /** Trading days looked forward */
        private int tradingDays;
        /** Average % return across all occurrences */
        private Double avgReturnPct;
        /** Median % return */
        private Double medianReturnPct;
        /** Best case % return seen in history */
        private Double bestReturnPct;
        /** Worst case % return seen in history */
        private Double worstReturnPct;
        /** % of occurrences that resulted in a positive return */
        private Double winRatePct;
        /** Maximum drawdown observed during the holding period */
        private Double maxDrawdownPct;
        /** Probability-weighted expected return (win_rate × avg_win + loss_rate × avg_loss) */
        private Double expectedValuePct;
    }
}
