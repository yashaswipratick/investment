package com.stock.stock_analyser.engine;

import com.stock.dto.StockHistoryDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic candlestick & price-action pattern engine.
 *
 * All patterns are computed from raw OHLCV using standard technical definitions.
 * No probabilistic inference — every rule is a precise numeric condition.
 *
 * Patterns detected:
 *  Single-candle : Doji, Hammer, Shooting Star, Spinning Top,
 *                  Marubozu (Bullish/Bearish), Dragonfly Doji, Gravestone Doji
 *  Two-candle    : Bullish/Bearish Engulfing, Piercing Line, Dark Cloud Cover,
 *                  Tweezer Top/Bottom
 *  Three-candle  : Morning Star, Evening Star, Three White Soldiers, Three Black Crows
 *  Price action  : Gap Up, Gap Down, Inside Bar
 *  Momentum      : 5-day % change, 10-day % change
 *  Volume        : Volume spike (current vs 20-day avg), volume on up/down days
 */
@Slf4j
@Component
public class CandlestickEngine {

    public CandlestickSignals analyse(List<StockHistoryDetails> candles) {
        if (candles == null || candles.size() < 3) {
            return CandlestickSignals.builder()
                    .candlestickPatterns(List.of())
                    .gapSignals(List.of())
                    .build();
        }

        int n = candles.size();
        StockHistoryDetails today = candles.get(n - 1);
        StockHistoryDetails prev  = candles.get(n - 2);
        StockHistoryDetails prev2 = candles.get(n - 3);

        double o = safe(today.getOpen());
        double h = safe(today.getHigh());
        double l = safe(today.getLow());
        double c = safe(today.getClose());

        double po = safe(prev.getOpen());
        double ph = safe(prev.getHigh());
        double pl = safe(prev.getLow());
        double pc = safe(prev.getClose());

        double p2o = safe(prev2.getOpen());
        double p2c = safe(prev2.getClose());

        double body       = Math.abs(c - o);
        double range      = h - l;
        double upperWick  = h - Math.max(o, c);
        double lowerWick  = Math.min(o, c) - l;
        boolean bullish   = c > o;
        boolean bearish   = c < o;

        double pBody      = Math.abs(pc - po);
        double pRange     = ph - pl;
        boolean pBullish  = pc > po;
        boolean pBearish  = pc < po;

        List<String> patterns = new ArrayList<>();
        List<String> gaps     = new ArrayList<>();

        // ── Single-candle patterns ─────────────────────────────────────────────

        // Doji: body < 10% of range
        if (range > 0 && body / range < 0.10) {
            if (lowerWick > range * 0.6)       patterns.add("DRAGONFLY_DOJI");
            else if (upperWick > range * 0.6)  patterns.add("GRAVESTONE_DOJI");
            else                               patterns.add("DOJI");
        }

        // Determine short-term trend from last 5 candles (needed to distinguish Hammer vs Hanging Man)
        boolean inShortTermUptrend   = isShortTermUptrend(candles, n, 5);
        boolean inShortTermDowntrend = !inShortTermUptrend;

        // Hammer shape: small body, long lower wick ≥ 2× body, tiny upper wick
        if (lowerWick >= 2 * body && upperWick <= 0.3 * body && range > 0) {
            if (inShortTermDowntrend) {
                // Appears at bottom of downtrend → bullish reversal signal
                patterns.add("HAMMER");
            } else {
                // Same shape at top of uptrend → bearish warning (hanging man)
                patterns.add("HANGING_MAN");
            }
        }

        // Inverted Hammer / Shooting Star — also trend-context-aware
        if (upperWick >= 2 * body && lowerWick <= 0.3 * body && range > 0) {
            if (bearish && inShortTermUptrend) {
                // Bearish, top of uptrend → Shooting Star (bearish reversal)
                patterns.add("SHOOTING_STAR");
            } else if (!bearish && inShortTermDowntrend) {
                // Bullish, bottom of downtrend → Inverted Hammer (potential reversal)
                patterns.add("INVERTED_HAMMER");
            } else {
                patterns.add(bearish ? "SHOOTING_STAR" : "INVERTED_HAMMER");
            }
        }

        // Spinning Top: small body, both wicks significant
        // Skip if already classified as a Doji — they overlap and Doji is the stronger label
        boolean isDoji = patterns.contains("DOJI") || patterns.contains("DRAGONFLY_DOJI") || patterns.contains("GRAVESTONE_DOJI");
        if (!isDoji && range > 0 && body / range < 0.25 && upperWick > body && lowerWick > body) {
            patterns.add("SPINNING_TOP");
        }

        // Marubozu: body covers ≥ 95% of range, minimal wicks
        if (range > 0 && body / range >= 0.95) {
            patterns.add(bullish ? "BULLISH_MARUBOZU" : "BEARISH_MARUBOZU");
        }

        // ── Two-candle patterns ────────────────────────────────────────────────

        // Bullish Engulfing: bearish candle followed by bullish that engulfs it
        if (pBearish && bullish && o < pc && c > po) {
            patterns.add("BULLISH_ENGULFING");
        }

        // Bearish Engulfing: bullish candle followed by bearish that engulfs it
        if (pBullish && bearish && o > pc && c < po) {
            patterns.add("BEARISH_ENGULFING");
        }

        // Piercing Line: bearish prev, bullish today opens below pl, closes above midpoint of prev body
        if (pBearish && bullish && o < pl && c > (po + pc) / 2 && c < po) {
            patterns.add("PIERCING_LINE");
        }

        // Dark Cloud Cover: bullish prev, bearish today opens above ph, closes below midpoint of prev body
        if (pBullish && bearish && o > ph && c < (po + pc) / 2 && c > po) {
            patterns.add("DARK_CLOUD_COVER");
        }

        // Tweezer Bottom: two candles with same/very close lows (within 0.5%)
        // Standard definition uses 0.3–0.5% tolerance; 0.1% was too strict and barely fired.
        if (Math.abs(l - pl) / Math.max(l, 0.01) < 0.005 && pBearish && bullish) {
            patterns.add("TWEEZER_BOTTOM");
        }

        // Tweezer Top: two candles with same/very close highs (within 0.5%)
        if (Math.abs(h - ph) / Math.max(h, 0.01) < 0.005 && pBullish && bearish) {
            patterns.add("TWEEZER_TOP");
        }

        // ── Kicker pattern ────────────────────────────────────────────────────
        // One of the strongest reversal signals — indicates sudden institutional shift.
        //
        // Bullish Kicker:
        //   Day 1 = bearish candle (closes below open)
        //   Day 2 = opens AT OR ABOVE Day 1's open (gap up) AND is a strong bullish candle
        //   Signals: institutions reversed their position overnight
        if (pBearish && bullish && o >= po * 0.999 && body > pBody * 0.5) {
            patterns.add("BULLISH_KICKER");
        }
        //
        // Bearish Kicker:
        //   Day 1 = bullish candle (closes above open)
        //   Day 2 = opens AT OR BELOW Day 1's open (gap down) AND is a strong bearish candle
        if (pBullish && bearish && o <= po * 1.001 && body > pBody * 0.5) {
            patterns.add("BEARISH_KICKER");
        }

        // ── Three-candle patterns ──────────────────────────────────────────────

        boolean p2Bearish = p2c < p2o;
        boolean p2Bullish = p2c > p2o;

        // Morning Star: bearish, small body (star), bullish
        if (p2Bearish && pBody < p2Body(candles, n) * 0.5 && bullish && c > (p2o + p2c) / 2) {
            patterns.add("MORNING_STAR");
        }

        // Evening Star: bullish, small body (star), bearish
        if (p2Bullish && pBody < p2Body(candles, n) * 0.5 && bearish && c < (p2o + p2c) / 2) {
            patterns.add("EVENING_STAR");
        }

        // Three White Soldiers: 3 consecutive bullish, each closing higher
        if (bullish && pBullish && p2Bullish && c > pc && pc > p2c && o > po && po > p2o) {
            patterns.add("THREE_WHITE_SOLDIERS");
        }

        // Three Black Crows: 3 consecutive bearish, each closing lower
        if (bearish && pBearish && p2Bearish && c < pc && pc < p2c && o < po && po < p2o) {
            patterns.add("THREE_BLACK_CROWS");
        }

        // Inside Bar: today's range is entirely within yesterday's range
        if (h < ph && l > pl) {
            patterns.add("INSIDE_BAR");
        }

        // ── Harami patterns ────────────────────────────────────────────────────
        // Bullish Harami: large bearish candle, then small bullish body INSIDE prev body
        // (opposite of Engulfing — current is engulfed by previous)
        if (pBearish && bullish && o > pc && c < po && body < pBody * 0.5) {
            patterns.add("BULLISH_HARAMI");
        }
        // Bearish Harami: large bullish candle, then small bearish body inside prev body
        if (pBullish && bearish && o < pc && c > po && body < pBody * 0.5) {
            patterns.add("BEARISH_HARAMI");
        }
        // Bullish Harami Cross: Harami where current candle is a Doji (even stronger signal)
        if (pBearish && range > 0 && body / range < 0.10 && Math.max(o,c) < po && Math.min(o,c) > pc) {
            patterns.add("BULLISH_HARAMI_CROSS");
        }
        // Bearish Harami Cross
        if (pBullish && range > 0 && body / range < 0.10 && Math.max(o,c) < pc && Math.min(o,c) > po) {
            patterns.add("BEARISH_HARAMI_CROSS");
        }

        // ── Three Outside Up/Down (Engulfing confirmation) ───────────────────
        // Stronger than Engulfing alone — requires a follow-through candle to confirm the reversal.
        //
        // Three Outside Up:
        //   Day 1 (p2) = bearish candle
        //   Day 2 (p)  = Bullish Engulfing of Day 1
        //   Day 3 (today) = bullish, closes above Day 2's high → reversal confirmed
        if (p2Bearish && pBullish && po < p2c && pc > p2o    // Day 2 engulfs Day 1
                && bullish && c > ph) {                        // Day 3 closes above Day 2 high
            patterns.add("THREE_OUTSIDE_UP");
        }
        //
        // Three Outside Down:
        //   Day 1 (p2) = bullish candle
        //   Day 2 (p)  = Bearish Engulfing of Day 1
        //   Day 3 (today) = bearish, closes below Day 2's low → reversal confirmed
        if (p2Bullish && pBearish && po > p2c && pc < p2o    // Day 2 engulfs Day 1
                && bearish && c < pl) {                        // Day 3 closes below Day 2 low
            patterns.add("THREE_OUTSIDE_DOWN");
        }

        // ── Three Inside Up/Down (Harami confirmation) ────────────────────────
        // Three Inside Up: [large bearish] → [small bullish Harami] → [bullish closes above Harami high]
        // p2=bearish, p=bullish inside p2 body, today=bullish closing above p high
        boolean p2Bearish2 = p2c < p2o;
        boolean p2Bullish2 = p2c > p2o;
        if (p2Bearish2 && pBullish && safe(prev.getOpen()) > p2c && pc < p2o && bullish && c > ph) {
            patterns.add("THREE_INSIDE_UP");
        }
        // Three Inside Down: [large bullish] → [small bearish Harami] → [bearish closes below Harami low]
        if (p2Bullish2 && pBearish && safe(prev.getOpen()) < p2c && pc > p2o && bearish && c < pl) {
            patterns.add("THREE_INSIDE_DOWN");
        }

        // ── Gap analysis ──────────────────────────────────────────────────────
        // Gap Up: today's open > previous close by more than 0.3%
        if (o > pc * 1.003) {
            gaps.add(String.format("GAP_UP +%.1f%% (open ₹%.2f vs prev close ₹%.2f)",
                    (o / pc - 1) * 100, o, pc));
        }
        // Gap Down: today's open < previous close by more than 0.3%
        if (o < pc * 0.997) {
            gaps.add(String.format("GAP_DOWN %.1f%% (open ₹%.2f vs prev close ₹%.2f)",
                    (o / pc - 1) * 100, o, pc));
        }

        // ── Momentum ──────────────────────────────────────────────────────────
        double mom5d  = momentum(candles, 5);
        double mom10d = momentum(candles, 10);

        // ── Volume confirmation ───────────────────────────────────────────────
        double currentVol = parseVolume(today.getVolume());
        double avgVol20   = averageVolume(candles, 20);
        String volConf;
        if (avgVol20 > 0) {
            double ratio = currentVol / avgVol20;
            if (ratio >= 1.5 && bullish)  volConf = "HIGH_VOLUME_BULLISH — volume spike on up day (confirms buying)";
            else if (ratio >= 1.5)        volConf = "HIGH_VOLUME_BEARISH — volume spike on down day (confirms selling)";
            else if (ratio >= 1.1)        volConf = "ABOVE_AVG_VOLUME";
            else if (ratio < 0.7)         volConf = "LOW_VOLUME — weak conviction";
            else                          volConf = "AVERAGE_VOLUME";
        } else {
            volConf = "N/A";
        }

        // ── Volume trend: more up-day volume than down-day volume (last 10 days)? ──
        String volTrend = volumeTrend(candles, 10);

        return CandlestickSignals.builder()
                .candlestickPatterns(patterns)
                .gapSignals(gaps)
                .momentum5dPct(round(mom5d))
                .momentum10dPct(round(mom10d))
                .volumeConfirmation(volConf)
                .volumeTrend10d(volTrend)
                .insideBar(patterns.contains("INSIDE_BAR"))
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns true if the last `lookback` closes form an uptrend
     * (linear regression slope is positive — more closes rising than falling).
     * Used to distinguish context-dependent patterns like Hammer vs Hanging Man.
     */
    private boolean isShortTermUptrend(List<StockHistoryDetails> candles, int n, int lookback) {
        int start = Math.max(0, n - lookback);
        if (n - start < 2) return false;
        double firstClose = safe(candles.get(start).getClose());
        double lastClose  = safe(candles.get(n - 1).getClose());
        return lastClose > firstClose;
    }

    private double p2Body(List<StockHistoryDetails> candles, int n) {
        if (n < 3) return 1;
        StockHistoryDetails p2 = candles.get(n - 3);
        return Math.abs(safe(p2.getClose()) - safe(p2.getOpen()));
    }

    private double momentum(List<StockHistoryDetails> candles, int days) {
        int n = candles.size();
        if (n <= days) return 0;
        double current = safe(candles.get(n - 1).getClose());
        double past    = safe(candles.get(n - 1 - days).getClose());
        return past > 0 ? (current - past) / past * 100 : 0;
    }

    private double averageVolume(List<StockHistoryDetails> candles, int days) {
        int n = candles.size();
        int start = Math.max(0, n - days);
        double sum = 0; int count = 0;
        for (int i = start; i < n; i++) {
            double v = parseVolume(candles.get(i).getVolume());
            if (v > 0) { sum += v; count++; }
        }
        return count > 0 ? sum / count : 0;
    }

    private String volumeTrend(List<StockHistoryDetails> candles, int days) {
        int n = candles.size();
        int start = Math.max(0, n - days);
        double upVol = 0, downVol = 0;
        for (int i = start; i < n; i++) {
            StockHistoryDetails c = candles.get(i);
            double v = parseVolume(c.getVolume());
            if (safe(c.getClose()) >= safe(c.getOpen())) upVol += v;
            else downVol += v;
        }
        if (upVol + downVol == 0) return "N/A";
        double ratio = upVol / (upVol + downVol);
        if (ratio >= 0.65) return "BULLISH — " + Math.round(ratio * 100) + "% volume on up-days";
        if (ratio <= 0.35) return "BEARISH — " + Math.round((1 - ratio) * 100) + "% volume on down-days";
        return "NEUTRAL — balanced up/down volume";
    }

    private double safe(Double v)      { return v != null ? v : 0; }
    private double round(double v)     { return Math.round(v * 100.0) / 100.0; }

    private double parseVolume(String vol) {
        if (vol == null || vol.isBlank()) return 0;
        try { return Double.parseDouble(vol.replace(",", "").trim()); }
        catch (NumberFormatException e) { return 0; }
    }
}
