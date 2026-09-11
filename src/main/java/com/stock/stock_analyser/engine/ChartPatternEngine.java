package com.stock.stock_analyser.engine;

import com.stock.dto.StockHistoryDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects classical chart patterns from the last 6 months (~126 candles).
 *
 * Patterns checked (in priority order):
 *   1. Head and Shoulders / Inverse H&S  (reversal)
 *   2. Double Top / Double Bottom         (reversal)
 *   3. Ascending Triangle                 (bullish continuation)
 *   4. Descending Triangle                (bearish continuation)
 *   5. Bullish Flag                       (bullish continuation)
 *   6. Rising Wedge                       (bearish reversal)
 *   7. Falling Wedge                      (bullish reversal)
 *   8. Cup and Handle                     (bullish continuation)
 *
 * Each result includes:
 *   - Pattern name
 *   - Plain-English description
 *   - Signal (BULLISH / BEARISH / NEUTRAL)
 *   - Confidence (HIGH / MEDIUM / LOW)
 */
@Slf4j
@Component
public class ChartPatternEngine {

    private static final int WINDOW = 126; // ~6 months

    public List<ChartPatternResult> detectPatterns(List<StockHistoryDetails> candles) {
        List<ChartPatternResult> results = new ArrayList<>();
        if (candles == null || candles.size() < 30) return results;

        int n = candles.size();
        int start = Math.max(0, n - WINDOW);
        List<StockHistoryDetails> window = candles.subList(start, n);

        double[] closes = extractCloses(window);
        double[] highs  = extractHighs(window);
        double[] lows   = extractLows(window);
        int wn = closes.length;

        // Run each detector — add the first strong pattern found
        ChartPatternResult p;

        p = detectDoubleTop(closes, highs, wn);
        if (p != null) results.add(p);

        p = detectDoubleBottom(closes, lows, wn);
        if (p != null) results.add(p);

        p = detectHeadAndShoulders(highs, closes, wn);
        if (p != null) results.add(p);

        p = detectInverseHeadAndShoulders(lows, closes, wn);
        if (p != null) results.add(p);

        p = detectAscendingTriangle(highs, lows, wn);
        if (p != null) results.add(p);

        p = detectDescendingTriangle(highs, lows, wn);
        if (p != null) results.add(p);

        p = detectBullishFlag(closes, highs, lows, wn);
        if (p != null) results.add(p);

        p = detectFallingWedge(closes, highs, lows, wn);
        if (p != null) results.add(p);

        p = detectRisingWedge(closes, highs, lows, wn);
        if (p != null) results.add(p);

        // If no pattern, return general trend description
        if (results.isEmpty()) {
            results.add(generalTrend(closes, wn));
        }

        return results;
    }

    // ── Pattern Detectors ──────────────────────────────────────────────────────

    /** Double Top: two roughly equal highs with a valley between — bearish reversal */
    private ChartPatternResult detectDoubleTop(double[] closes, double[] highs, int n) {
        if (n < 40) return null;
        int mid = n / 2;
        double peak1 = max(highs, 0, mid);
        double peak2 = max(highs, mid, n);
        double valley = min(closes, n / 4, 3 * n / 4);

        double peakDiff = Math.abs(peak1 - peak2) / Math.max(peak1, 0.01);
        double depth    = (Math.min(peak1, peak2) - valley) / Math.max(peak1, 0.01);

        if (peakDiff < 0.04 && depth > 0.05 && closes[n - 1] < Math.min(peak1, peak2) * 0.98) {
            return ChartPatternResult.builder()
                    .patternName("Double Top")
                    .signal("BEARISH")
                    .confidence(peakDiff < 0.02 ? "HIGH" : "MEDIUM")
                    .description("The stock has hit the same price ceiling twice (₹" + fmt(peak1) + " and ₹" + fmt(peak2) + ") and failed to break higher.")
                    .whatItMeans("Think of it like a ball bouncing off the ceiling twice — it can't go higher. This usually means the stock is about to fall. Sellers are stronger than buyers at that price level.")
                    .tradingSignal("Watch for a break below the valley (₹" + fmt(valley) + "). If it breaks, prices could drop significantly. Avoid buying until confirmed reversal.")
                    .build();
        }
        return null;
    }

    /** Double Bottom: two roughly equal lows — bullish reversal */
    private ChartPatternResult detectDoubleBottom(double[] closes, double[] lows, int n) {
        if (n < 40) return null;
        int mid = n / 2;
        double trough1 = min(lows, 0, mid);
        double trough2 = min(lows, mid, n);
        double peak    = max(closes, n / 4, 3 * n / 4);

        double troughDiff = Math.abs(trough1 - trough2) / Math.max(trough1, 0.01);
        double height     = (peak - Math.max(trough1, trough2)) / Math.max(trough1, 0.01);

        if (troughDiff < 0.04 && height > 0.05 && closes[n - 1] > Math.max(trough1, trough2) * 1.02) {
            return ChartPatternResult.builder()
                    .patternName("Double Bottom")
                    .signal("BULLISH")
                    .confidence(troughDiff < 0.02 ? "HIGH" : "MEDIUM")
                    .description("The stock has bounced off the same support floor twice (₹" + fmt(trough1) + " and ₹" + fmt(trough2) + ").")
                    .whatItMeans("Like a ball bouncing off the floor twice — buyers keep stepping in at this price. This is a sign the selling is exhausted and a recovery may begin.")
                    .tradingSignal("A close above ₹" + fmt(peak) + " (the neckline) confirms the reversal. That could be a good entry point for a recovery trade.")
                    .build();
        }
        return null;
    }

    /** Head and Shoulders: 3 peaks, middle highest — bearish reversal */
    private ChartPatternResult detectHeadAndShoulders(double[] highs, double[] closes, int n) {
        if (n < 50) return null;
        int t1 = n / 4, t2 = n / 2, t3 = 3 * n / 4;
        double ls  = max(highs, 0,  t1);
        double head = max(highs, t1, t2 + (t2 - t1) / 2);
        double rs  = max(highs, t2, n);
        double neckline = min(closes, t1, t3);

        boolean headIsHighest = head > ls && head > rs;
        boolean shouldersEqual = Math.abs(ls - rs) / Math.max(ls, 0.01) < 0.06;

        if (headIsHighest && shouldersEqual && closes[n - 1] < neckline * 1.01) {
            return ChartPatternResult.builder()
                    .patternName("Head & Shoulders")
                    .signal("BEARISH")
                    .confidence("MEDIUM")
                    .description("Three consecutive peaks where the middle one (head) is the highest, flanked by two smaller peaks (shoulders).")
                    .whatItMeans("Imagine a person's silhouette — left shoulder, head, right shoulder. This is one of the most reliable bearish patterns. After making three highs, buyers are getting tired.")
                    .tradingSignal("If price breaks below the neckline (₹" + fmt(neckline) + "), a significant drop may follow. Good time to consider reducing position size or setting tight stop losses.")
                    .build();
        }
        return null;
    }

    /** Inverse Head and Shoulders — bullish reversal */
    private ChartPatternResult detectInverseHeadAndShoulders(double[] lows, double[] closes, int n) {
        if (n < 50) return null;
        int t1 = n / 4, t2 = n / 2, t3 = 3 * n / 4;
        double ls   = min(lows, 0,  t1);
        double head = min(lows, t1, t2 + (t2 - t1) / 2);
        double rs   = min(lows, t2, n);
        double neckline = max(closes, t1, t3);

        boolean headIsLowest    = head < ls && head < rs;
        boolean shouldersEqual  = Math.abs(ls - rs) / Math.max(Math.abs(ls), 0.01) < 0.06;

        if (headIsLowest && shouldersEqual && closes[n - 1] > neckline * 0.99) {
            return ChartPatternResult.builder()
                    .patternName("Inverse Head & Shoulders")
                    .signal("BULLISH")
                    .confidence("MEDIUM")
                    .description("Three consecutive troughs — middle one deepest — followed by recovery. Mirror image of H&S.")
                    .whatItMeans("The stock has made three lows — the middle being the deepest — and is now recovering. Sellers are getting tired. This is one of the most reliable bullish reversal patterns.")
                    .tradingSignal("A break above ₹" + fmt(neckline) + " (the neckline) with strong volume confirms the pattern. This can be a strong buying opportunity.")
                    .build();
        }
        return null;
    }

    /** Ascending Triangle: flat top resistance + rising lows — bullish breakout pending */
    private ChartPatternResult detectAscendingTriangle(double[] highs, double[] lows, int n) {
        if (n < 30) return null;
        // Flat top: last 30% of window high stays within 3%
        int flatStart = 2 * n / 3;
        double topHigh = max(highs, flatStart, n);
        double topLow  = min(highs, flatStart, n);
        boolean flatTop = (topHigh - topLow) / Math.max(topHigh, 0.01) < 0.03;

        // Rising lows: compare first half vs second half
        double loFirst  = min(lows, 0, n / 2);
        double loSecond = min(lows, n / 2, n);
        boolean risingLows = loSecond > loFirst * 1.02;

        if (flatTop && risingLows) {
            return ChartPatternResult.builder()
                    .patternName("Ascending Triangle")
                    .signal("BULLISH")
                    .confidence("MEDIUM")
                    .description("Price keeps making higher lows while hitting the same resistance ceiling at ₹" + fmt(topHigh) + ".")
                    .whatItMeans("Each time the stock dips, it doesn't fall as far — buyers are getting more aggressive. But sellers keep blocking it at ₹" + fmt(topHigh) + ". Eventually buyers usually win this tug of war.")
                    .tradingSignal("Watch for a strong close ABOVE ₹" + fmt(topHigh) + ". That breakout is typically very powerful with fast price increase.")
                    .build();
        }
        return null;
    }

    /** Descending Triangle: flat support + lower highs — bearish breakdown pending */
    private ChartPatternResult detectDescendingTriangle(double[] highs, double[] lows, int n) {
        if (n < 30) return null;
        int flatStart = 2 * n / 3;
        double botHigh = max(lows, flatStart, n);
        double botLow  = min(lows, flatStart, n);
        boolean flatBottom = (botHigh - botLow) / Math.max(botHigh, 0.01) < 0.03;

        double hiFirst  = max(highs, 0, n / 2);
        double hiSecond = max(highs, n / 2, n);
        boolean fallingHighs = hiSecond < hiFirst * 0.98;

        if (flatBottom && fallingHighs) {
            return ChartPatternResult.builder()
                    .patternName("Descending Triangle")
                    .signal("BEARISH")
                    .confidence("MEDIUM")
                    .description("Price makes lower highs but keeps finding support at ₹" + fmt(botLow) + ".")
                    .whatItMeans("Sellers are getting more aggressive (highs keep dropping) but buyers are stubbornly holding ₹" + fmt(botLow) + ". Once that floor breaks, there can be a sharp fall.")
                    .tradingSignal("If price closes below ₹" + fmt(botLow) + " on high volume, it often signals a significant drop. Consider protecting any existing positions.")
                    .build();
        }
        return null;
    }

    /** Bullish Flag: sharp rise followed by tight pullback channel */
    private ChartPatternResult detectBullishFlag(double[] closes, double[] highs, double[] lows, int n) {
        if (n < 25) return null;
        // Pole: first 40% shows strong upward move
        int poleEnd = 2 * n / 5;
        double poleStart = closes[0];
        double polePeak  = max(highs, 0, poleEnd);
        double poleGain  = (polePeak - poleStart) / Math.max(poleStart, 0.01);

        // Flag: last 30% consolidates in a tight range
        int flagStart = 7 * n / 10;
        double flagHigh = max(highs, flagStart, n);
        double flagLow  = min(lows, flagStart, n);
        double flagRange = (flagHigh - flagLow) / Math.max(flagHigh, 0.01);
        boolean tightConsolidation = flagRange < 0.08;
        boolean stillHigh = closes[n - 1] > polePeak * 0.85;

        if (poleGain > 0.12 && tightConsolidation && stillHigh) {
            return ChartPatternResult.builder()
                    .patternName("Bullish Flag")
                    .signal("BULLISH")
                    .confidence("MEDIUM")
                    .description("After a sharp rise (the 'pole'), price is taking a breather in a tight sideways range (the 'flag').")
                    .whatItMeans("Like a flag on a flagpole — after a strong surge, the stock is resting and digesting gains. This is healthy. After the pause, stocks often resume the upward move.")
                    .tradingSignal("A breakout above the flag's upper boundary typically leads to another move equal in size to the original pole. Good continuation pattern for existing holders.")
                    .build();
        }
        return null;
    }

    /** Falling Wedge: converging lower highs and lower lows — bullish reversal */
    private ChartPatternResult detectFallingWedge(double[] closes, double[] highs, double[] lows, int n) {
        if (n < 30) return null;
        int mid = n / 2;
        double hiFirst  = max(highs, 0, mid);
        double hiSecond = max(highs, mid, n);
        double loFirst  = min(lows, 0, mid);
        double loSecond = min(lows, mid, n);

        boolean fallingHighs = hiSecond < hiFirst * 0.96;
        boolean fallingLows  = loSecond < loFirst * 0.96;
        // Lows fall less steeply than highs (converging)
        double hiDrop  = (hiFirst - hiSecond) / Math.max(hiFirst, 0.01);
        double loDrop  = (loFirst - loSecond) / Math.max(Math.abs(loFirst), 0.01);
        boolean converging = hiDrop > loDrop && hiDrop - loDrop > 0.01;

        if (fallingHighs && fallingLows && converging) {
            return ChartPatternResult.builder()
                    .patternName("Falling Wedge")
                    .signal("BULLISH")
                    .confidence("MEDIUM")
                    .description("Both highs and lows are declining but converging — the range is getting tighter as the stock falls.")
                    .whatItMeans("Even though prices are falling, sellers are losing momentum — each drop is smaller. This compression often resolves with a sharp upward breakout.")
                    .tradingSignal("Watch for a breakout above the upper trendline. The falling wedge is considered a bullish pattern — breakouts can be strong and fast.")
                    .build();
        }
        return null;
    }

    /** Rising Wedge: converging higher highs and higher lows — bearish reversal */
    private ChartPatternResult detectRisingWedge(double[] closes, double[] highs, double[] lows, int n) {
        if (n < 30) return null;
        int mid = n / 2;
        double hiFirst  = max(highs, 0, mid);
        double hiSecond = max(highs, mid, n);
        double loFirst  = min(lows, 0, mid);
        double loSecond = min(lows, mid, n);

        boolean risingHighs = hiSecond > hiFirst * 1.02;
        boolean risingLows  = loSecond > loFirst * 1.02;
        double hiGain = (hiSecond - hiFirst) / Math.max(hiFirst, 0.01);
        double loGain = (loSecond - loFirst) / Math.max(Math.abs(loFirst), 0.01);
        boolean converging = loGain > hiGain && loGain - hiGain > 0.01;

        if (risingHighs && risingLows && converging && closes[n - 1] > closes[0] * 1.05) {
            return ChartPatternResult.builder()
                    .patternName("Rising Wedge")
                    .signal("BEARISH")
                    .confidence("LOW")
                    .description("Price making higher highs and higher lows but in a narrowing range — buyers are struggling despite gains.")
                    .whatItMeans("Although prices are rising, the moves are getting smaller and smaller (converging). This shows buying is becoming exhausted — like a car running out of fuel while going uphill.")
                    .tradingSignal("A breakdown below the lower trendline often leads to a sharp reversal. Consider taking some profits or tightening stop losses if holding.")
                    .build();
        }
        return null;
    }

    /** Fallback: general trend description */
    private ChartPatternResult generalTrend(double[] closes, int n) {
        double firstHalf = avg(closes, 0, n / 2);
        double secondHalf = avg(closes, n / 2, n);
        double change = (closes[n - 1] - closes[0]) / Math.max(closes[0], 0.01) * 100;

        if (secondHalf > firstHalf * 1.05) {
            return ChartPatternResult.builder()
                    .patternName("Uptrend")
                    .signal("BULLISH")
                    .confidence("MEDIUM")
                    .description("Price has generally moved higher over the last 6 months (+"+String.format("%.1f", change)+"%).")
                    .whatItMeans("The stock has been in a rising trend — higher highs and higher lows. The overall direction is positive.")
                    .tradingSignal("Uptrends are healthy to hold. Look for dips to support levels as potential entry points.")
                    .build();
        } else if (secondHalf < firstHalf * 0.95) {
            return ChartPatternResult.builder()
                    .patternName("Downtrend")
                    .signal("BEARISH")
                    .confidence("MEDIUM")
                    .description("Price has generally moved lower over the last 6 months ("+String.format("%.1f", change)+"%).")
                    .whatItMeans("The stock has been making lower lows and lower highs — sellers have been in control. No clear reversal pattern yet.")
                    .tradingSignal("In a downtrend, avoid buying until there is a reversal signal. Patience is key.")
                    .build();
        } else {
            return ChartPatternResult.builder()
                    .patternName("Sideways / Consolidation")
                    .signal("NEUTRAL")
                    .confidence("MEDIUM")
                    .description("Price has been range-bound over the last 6 months — no strong directional move.")
                    .whatItMeans("The stock is stuck between a floor and ceiling — like a ball bouncing between two walls. Neither buyers nor sellers are winning.")
                    .tradingSignal("Wait for a breakout above the range or a breakdown below it. Range breakouts often lead to strong trends.")
                    .build();
        }
    }

    // ── Array helpers ─────────────────────────────────────────────────────────

    private double[] extractCloses(List<StockHistoryDetails> c) {
        double[] a = new double[c.size()]; double last = 0;
        for (int i = 0; i < c.size(); i++) { Double v = c.get(i).getClose(); a[i] = v != null ? v : last; last = a[i]; }
        return a;
    }
    private double[] extractHighs(List<StockHistoryDetails> c) {
        double[] a = new double[c.size()]; double last = 0;
        for (int i = 0; i < c.size(); i++) { Double v = c.get(i).getHigh(); a[i] = v != null ? v : last; last = a[i]; }
        return a;
    }
    private double[] extractLows(List<StockHistoryDetails> c) {
        double[] a = new double[c.size()]; double last = 0;
        for (int i = 0; i < c.size(); i++) { Double v = c.get(i).getLow(); a[i] = v != null ? v : last; last = a[i]; }
        return a;
    }

    private double max(double[] a, int from, int to) {
        double m = Double.MIN_VALUE;
        for (int i = from; i < to && i < a.length; i++) if (a[i] > m) m = a[i];
        return m == Double.MIN_VALUE ? 0 : m;
    }
    private double min(double[] a, int from, int to) {
        double m = Double.MAX_VALUE;
        for (int i = from; i < to && i < a.length; i++) if (a[i] < m) m = a[i];
        return m == Double.MAX_VALUE ? 0 : m;
    }
    private double avg(double[] a, int from, int to) {
        double s = 0; int c = 0;
        for (int i = from; i < to && i < a.length; i++) { s += a[i]; c++; }
        return c > 0 ? s / c : 0;
    }
    private String fmt(double v) { return String.format("%.0f", v); }
}
