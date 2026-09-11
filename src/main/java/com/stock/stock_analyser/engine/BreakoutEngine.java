package com.stock.stock_analyser.engine;

import com.stock.dto.StockHistoryDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Detects the most recent breakout and whether it has been retested.
 *
 * Definition:
 *   Breakout = a daily close that is ABOVE the highest close of the previous
 *              20 candles (resistance). This signals a potential new uptrend.
 *
 * Retest   = after a breakout, the price returns within 2% of the breakout
 *              level from above (testing it as new support) and bounces back up.
 *              A confirmed retest increases conviction that the breakout is real.
 */
@Slf4j
@Component
public class BreakoutEngine {

    private static final int RESISTANCE_LOOKBACK = 20;  // candles to define resistance
    private static final double RETEST_TOLERANCE  = 0.02; // 2% tolerance for retest
    private static final int ACTIONABLE_BREAKOUT_MAX_DAYS_AGO = 5;
    private static final double ACTIONABLE_VOLUME_RATIO = 1.5;

    public BreakoutResult analyse(List<StockHistoryDetails> candles) {
        if (candles == null || candles.size() < RESISTANCE_LOOKBACK + 5) {
            return BreakoutResult.builder()
                    .breakoutFound(false)
                    .explanation("Insufficient data to detect breakouts.")
                    .build();
        }

        int n = candles.size();
        double[] closes = extractCloses(candles);

        // ── Find the most recent breakout ─────────────────────────────────────
        int    breakoutBar   = -1;
        double breakoutLevel = 0;
        String breakoutDate  = null;

        // Scan backwards from the most recent bar
        for (int i = n - 1; i >= RESISTANCE_LOOKBACK; i--) {
            // Resistance = highest close of the 20 candles BEFORE bar i
            double resistance = max(closes, i - RESISTANCE_LOOKBACK, i);
            if (closes[i] > resistance) {
                breakoutBar   = i;
                breakoutLevel = resistance;
                breakoutDate  = candles.get(i).getHistoryDate() != null
                        ? candles.get(i).getHistoryDate().toString() : "unknown";
                break;
            }
        }

        if (breakoutBar == -1) {
            return BreakoutResult.builder()
                    .breakoutFound(false)
                    .explanation("No recent breakout detected. Stock has been trading below its prior resistance levels — no clear upward surge found.")
                    .build();
        }

        double breakoutPrice = closes[breakoutBar];
        Double breakoutVolumeRatio = volumeRatio(candles, breakoutBar);
        int daysAgo = n - 1 - breakoutBar;

        // ── Check for retest after the breakout ───────────────────────────────
        boolean retested        = false;
        boolean retestConfirmed = false;
        String  retestDate      = null;

        for (int i = breakoutBar + 1; i < n; i++) {
            double low   = safe(candles.get(i).getLow());
            double close = closes[i];
            double upper = breakoutLevel * (1 + RETEST_TOLERANCE);
            double lower = breakoutLevel * (1 - RETEST_TOLERANCE);

            // Price dipped to within 2% of breakout level from above
            if (low <= upper && close >= lower) {
                retested   = true;
                retestDate = candles.get(i).getHistoryDate() != null
                        ? candles.get(i).getHistoryDate().toString() : "unknown";

                // Confirmed if the candle closed back above the breakout level
                if (close >= breakoutLevel) {
                    retestConfirmed = true;
                }
                break;
            }
        }

        // ── Signal and plain-English explanation ─────────────────────────────
        String signal;
        String explanation;

        boolean fresh = daysAgo <= ACTIONABLE_BREAKOUT_MAX_DAYS_AGO;
        boolean volumeConfirmed = breakoutVolumeRatio != null && breakoutVolumeRatio >= ACTIONABLE_VOLUME_RATIO;
        if (retestConfirmed && fresh && volumeConfirmed) {
            signal = "BULLISH_BREAKOUT_CONFIRMED";
            explanation = String.format(
                "📈 Current breakout confirmed %d day(s) ago above ₹%.2f with %.1fx breakout volume and a successful retest.",
                daysAgo, breakoutLevel, breakoutVolumeRatio);
        } else if (retestConfirmed) {
            signal = "HISTORICAL_BREAKOUT_CONFIRMED";
            explanation = String.format(
                "Historical breakout occurred %d day(s) ago at ₹%.2f. It is context only and is not a current entry signal.",
                daysAgo, breakoutLevel);
        } else if (retested) {
            signal = "BREAKOUT_RETEST_IN_PROGRESS";
            explanation = String.format(
                "⚠️ Breakout happened %d days ago at ₹%.2f. The stock is currently retesting " +
                "that level. Watch closely — if it bounces here, the breakout is confirmed. " +
                "If it falls below, the breakout has failed.",
                daysAgo, breakoutLevel);
        } else if (fresh && volumeConfirmed) {
            signal = "FRESH_BREAKOUT";
            explanation = String.format(
                "🚀 Fresh breakout! %d trading day(s) ago the stock broke above ₹%.2f with %.1fx volume. " +
                "Current technical alignment is still required before treating it as actionable.",
                daysAgo, breakoutLevel, breakoutVolumeRatio);
        } else {
            signal = fresh ? "FRESH_BREAKOUT_UNCONFIRMED_VOLUME" : "HISTORICAL_BREAKOUT";
            explanation = String.format(
                "Breakout detected %d trading day(s) ago at ₹%.2f, but it is not a confirmed current entry signal. " +
                "Historical events are informational until current price, volume and technical conditions align.",
                daysAgo, breakoutLevel);
        }

        return BreakoutResult.builder()
                .breakoutFound(true)
                .breakoutDate(breakoutDate)
                .breakoutLevel(round(breakoutLevel))
                .breakoutPrice(round(breakoutPrice))
                .breakoutVolumeRatio(round(breakoutVolumeRatio))
                .daysAgoBreakout(daysAgo)
                .retested(retested)
                .retestConfirmed(retestConfirmed)
                .retestDate(retestDate)
                .signal(signal)
                .explanation(explanation)
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private double[] extractCloses(List<StockHistoryDetails> candles) {
        double[] arr = new double[candles.size()];
        double last = 0;
        for (int i = 0; i < candles.size(); i++) {
            Double c = candles.get(i).getClose();
            arr[i] = (c != null) ? c : last;
            last = arr[i];
        }
        return arr;
    }

    private double max(double[] arr, int from, int toExclusive) {
        double m = Double.MIN_VALUE;
        for (int i = from; i < toExclusive; i++) if (arr[i] > m) m = arr[i];
        return m == Double.MIN_VALUE ? 0 : m;
    }

    private double safe(Double v)  { return v != null ? v : 0; }
    private Double volumeRatio(List<StockHistoryDetails> candles, int index) {
        if (index < RESISTANCE_LOOKBACK) return null;
        double sum = 0;
        int count = 0;
        for (int i = index - RESISTANCE_LOOKBACK; i < index; i++) {
            Double v = parseVolume(candles.get(i).getVolume());
            if (v != null && v > 0) { sum += v; count++; }
        }
        Double breakoutVolume = parseVolume(candles.get(index).getVolume());
        if (count == 0 || breakoutVolume == null || breakoutVolume <= 0) return null;
        return breakoutVolume / (sum / count);
    }

    private Double parseVolume(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Double.parseDouble(value.replace(",", "").trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private double round(double v) { return Math.round(v * 100.0) / 100.0; }
    private Double round(Double v) { return v == null || !Double.isFinite(v) ? null : round(v.doubleValue()); }
}
