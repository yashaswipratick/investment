package com.stock.stock_analyser.engine;

import com.stock.dto.StockHistoryDetails;
import com.stock.stock_analyser.dto.BacktestResult;
import com.stock.stock_analyser.dto.TechnicalSignals;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Backtests the current signal setup against historical data.
 *
 * Algorithm:
 *  1. Build a signal "fingerprint" for the current bar (RSI zone, MACD direction, trend, BB zone).
 *  2. Slide through history, compute the same fingerprint for each past bar.
 *  3. If fingerprints match, record the actual forward returns at 63 / 126 / 252 trading days.
 *  4. Aggregate: avgReturn, winRate, maxDrawdown, expectedValue.
 *
 * This gives probability-backed projections grounded in the stock's own history.
 *
 * Minimum data requirements:
 *  - Need at least 100 candles before a bar to compute reliable indicators
 *  - Need at least 252 candles after a bar to measure 1Y forward return
 *  - Minimum 5 occurrences for statistical significance
 */
@Slf4j
@Component
public class BacktestEngine {

    private static final int MIN_WARM_UP       = 60;   // candles before bar (for indicator stability)
    private static final int HORIZON_3M        = 63;   // trading days
    private static final int HORIZON_6M        = 126;
    private static final int HORIZON_1Y        = 252;
    private static final int MIN_OCCURRENCES   = 5;    // for statistical significance

    public BacktestResult backtest(List<StockHistoryDetails> candles, TechnicalSignals currentSignals) {
        if (candles == null || candles.size() < MIN_WARM_UP + HORIZON_1Y + 1) {
            log.debug("Backtest skipped: insufficient candles ({} available, need {})",
                      candles == null ? 0 : candles.size(), MIN_WARM_UP + HORIZON_1Y + 1);
            return BacktestResult.builder()
                    .occurrences(0)
                    .statistically_significant(false)
                    .signalFingerprint("INSUFFICIENT_DATA")
                    .horizonStats(List.of())
                    .build();
        }

        int n = candles.size();
        double[] closes = extractCloses(candles);

        // Build fingerprint for the current (last) bar
        String currentFp = buildFingerprint(currentSignals);
        log.debug("Backtest fingerprint for current bar: {}", currentFp);

        // Collect forward returns for each matching historical bar
        List<Double> returns3M  = new ArrayList<>();
        List<Double> returns6M  = new ArrayList<>();
        List<Double> returns1Y  = new ArrayList<>();
        List<Double> drawdowns3M = new ArrayList<>();
        List<Double> drawdowns6M = new ArrayList<>();
        List<Double> drawdowns1Y = new ArrayList<>();

        // Scan history: bar i is a candidate if there are enough candles before AND after
        for (int i = MIN_WARM_UP; i < n - HORIZON_1Y; i++) {
            String fp = buildFingerprintFromRaw(closes, i);
            if (!fp.equals(currentFp)) continue;

            // Record forward returns
            double closeAtI = closes[i];
            if (closeAtI <= 0) continue;

            // 3M forward return
            double ret3M = (closes[i + HORIZON_3M] - closeAtI) / closeAtI * 100;
            double dd3M  = maxDrawdown(closes, i, HORIZON_3M);
            returns3M.add(ret3M);
            drawdowns3M.add(dd3M);

            // 6M forward return
            double ret6M = (closes[i + HORIZON_6M] - closeAtI) / closeAtI * 100;
            double dd6M  = maxDrawdown(closes, i, HORIZON_6M);
            returns6M.add(ret6M);
            drawdowns6M.add(dd6M);

            // 1Y forward return
            double ret1Y = (closes[i + HORIZON_1Y] - closeAtI) / closeAtI * 100;
            double dd1Y  = maxDrawdown(closes, i, HORIZON_1Y);
            returns1Y.add(ret1Y);
            drawdowns1Y.add(dd1Y);
        }

        int occ = returns1Y.size();
        log.info("Backtest found {} historical occurrences of fingerprint '{}'", occ, currentFp);

        List<BacktestResult.HorizonStat> stats = new ArrayList<>();
        if (occ > 0) {
            stats.add(buildStat("3M", HORIZON_3M, returns3M, drawdowns3M));
            stats.add(buildStat("6M", HORIZON_6M, returns6M, drawdowns6M));
            stats.add(buildStat("1Y", HORIZON_1Y, returns1Y, drawdowns1Y));
        }

        return BacktestResult.builder()
                .occurrences(occ)
                .statistically_significant(occ >= MIN_OCCURRENCES)
                .signalFingerprint(currentFp)
                .horizonStats(stats)
                .build();
    }

    // ── Fingerprint builders ──────────────────────────────────────────────────

    /** Build fingerprint from pre-computed TechnicalSignals */
    private String buildFingerprint(TechnicalSignals t) {
        String rsiZone  = rsiZone(t.getRsi14());
        String macdDir  = t.getMacdLine() != null && t.getMacdSignal() != null
                          ? (t.getMacdLine() > t.getMacdSignal() ? "MACD_BULL" : "MACD_BEAR")
                          : "MACD_NA";
        String trend    = t.getTrendDirection() != null ? t.getTrendDirection() : "SIDEWAYS";
        String bbPos    = t.getBbSignal() != null ? t.getBbSignal() : "INSIDE";
        return rsiZone + "|" + macdDir + "|" + trend + "|" + bbPos;
    }

    /**
     * Build fingerprint at historical bar index `i` from raw close array.
     * Uses simplified rolling indicators for performance:
     *   RSI-14, EMA12/EMA26 (MACD), SMA50 vs SMA200 (trend), SMA20 (BB position).
     */
    private String buildFingerprintFromRaw(double[] closes, int i) {
        // RSI zone
        Double rsi = simpleRsi(closes, i, 14);
        String rsiZone = rsiZone(rsi);

        // MACD direction (EMA12 vs EMA26)
        String macdDir = "MACD_NA";
        if (i >= 26) {
            double ema12 = simpleEma(closes, i, 12);
            double ema26 = simpleEma(closes, i, 26);
            macdDir = ema12 > ema26 ? "MACD_BULL" : "MACD_BEAR";
        }

        // Trend: SMA50 vs SMA200
        String trend = "SIDEWAYS";
        if (i >= 200) {
            double sma50  = sma(closes, i, 50);
            double sma200 = sma(closes, i, 200);
            if (closes[i] > sma50 && sma50 > sma200)  trend = "UPTREND";
            else if (closes[i] < sma50 && sma50 < sma200) trend = "DOWNTREND";
        } else if (i >= 50) {
            double sma50 = sma(closes, i, 50);
            trend = closes[i] > sma50 ? "UPTREND" : "DOWNTREND";
        }

        // BB position
        String bbPos = "INSIDE";
        if (i >= 20) {
            double sma20 = sma(closes, i, 20);
            double std   = stdDev(closes, i, 20);
            double upper = sma20 + 2 * std;
            double lower = sma20 - 2 * std;
            double range = upper - lower;
            if (range > 0) {
                double pct = (closes[i] - lower) / range;
                if (pct < 0.15)      bbPos = "NEAR_LOWER";
                else if (pct > 0.85) bbPos = "NEAR_UPPER";
            }
        }

        return rsiZone + "|" + macdDir + "|" + trend + "|" + bbPos;
    }

    private String rsiZone(Double rsi) {
        if (rsi == null) return "RSI_NA";
        if (rsi < 35)   return "RSI_OVERSOLD";
        if (rsi > 65)   return "RSI_OVERBOUGHT";
        if (rsi < 50)   return "RSI_WEAK";
        return "RSI_STRONG";
    }

    // ── Forward return helpers ─────────────────────────────────────────────────

    /** Maximum intra-period drawdown from bar i over the next `days` candles */
    private double maxDrawdown(double[] closes, int from, int days) {
        double peak = closes[from];
        double maxDD = 0;
        int end = Math.min(from + days, closes.length - 1);
        for (int j = from + 1; j <= end; j++) {
            if (closes[j] > peak) peak = closes[j];
            double dd = (peak - closes[j]) / peak * 100;
            if (dd > maxDD) maxDD = dd;
        }
        return maxDD;
    }

    // ── Statistics ─────────────────────────────────────────────────────────────

    private BacktestResult.HorizonStat buildStat(String horizon, int days,
                                                   List<Double> returns, List<Double> drawdowns) {
        if (returns.isEmpty()) return null;

        Collections.sort(returns);
        double avg     = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double median  = returns.get(returns.size() / 2);
        double best    = returns.get(returns.size() - 1);
        double worst   = returns.get(0);
        long   wins    = returns.stream().filter(r -> r > 0).count();
        double winRate = (double) wins / returns.size() * 100;
        double maxDD   = drawdowns.stream().mapToDouble(Double::doubleValue).max().orElse(0);

        // Expected value = P(win) × avg_win + P(loss) × avg_loss
        double avgWin  = returns.stream().filter(r -> r > 0).mapToDouble(Double::doubleValue).average().orElse(0);
        double avgLoss = returns.stream().filter(r -> r <= 0).mapToDouble(Double::doubleValue).average().orElse(0);
        double ev      = (winRate / 100) * avgWin + ((100 - winRate) / 100) * avgLoss;

        return BacktestResult.HorizonStat.builder()
                .horizon(horizon)
                .tradingDays(days)
                .avgReturnPct(round(avg))
                .medianReturnPct(round(median))
                .bestReturnPct(round(best))
                .worstReturnPct(round(worst))
                .winRatePct(round(winRate))
                .maxDrawdownPct(round(maxDD))
                .expectedValuePct(round(ev))
                .build();
    }

    // ── Rolling indicator helpers (for scanning history) ──────────────────────

    private double sma(double[] arr, int endIncl, int period) {
        double sum = 0;
        for (int i = endIncl - period + 1; i <= endIncl; i++) sum += arr[i];
        return sum / period;
    }

    private double stdDev(double[] arr, int endIncl, int period) {
        double mean = sma(arr, endIncl, period);
        double var  = 0;
        for (int i = endIncl - period + 1; i <= endIncl; i++) var += (arr[i] - mean) * (arr[i] - mean);
        return Math.sqrt(var / period);
    }

    private double simpleEma(double[] arr, int endIncl, int period) {
        double k   = 2.0 / (period + 1);
        double ema = arr[Math.max(0, endIncl - period * 3)]; // seed
        for (int i = Math.max(1, endIncl - period * 3); i <= endIncl; i++) {
            ema = arr[i] * k + ema * (1 - k);
        }
        return ema;
    }

    private Double simpleRsi(double[] arr, int endIncl, int period) {
        if (endIncl < period + 1) return null;
        double ag = 0, al = 0;
        for (int i = endIncl - period; i < endIncl; i++) {
            double d = arr[i + 1] - arr[i];
            if (d > 0) ag += d; else al += Math.abs(d);
        }
        ag /= period; al /= period;
        if (al == 0) return 100.0;
        double rs = ag / al;
        return 100 - (100 / (1 + rs));
    }

    private double[] extractCloses(List<StockHistoryDetails> candles) {
        double[] arr = new double[candles.size()];
        for (int i = 0; i < candles.size(); i++) {
            Double c = candles.get(i).getClose();
            arr[i] = c != null ? c : (i > 0 ? arr[i-1] : 0);
        }
        return arr;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
