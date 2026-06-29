package com.stock.stock_analyser.engine;

import com.stock.dto.StockHistoryDetails;
import com.stock.stock_analyser.dto.BacktestResult;
import com.stock.stock_analyser.dto.TechnicalSignals;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Computes all technical indicators from a sorted (oldest→newest) list of candles.
 *
 * Indicators implemented:
 *  - SMA 20, 50, 200
 *  - EMA 12, 26
 *  - MACD (EMA12 - EMA26) + Signal (EMA9 of MACD) + Histogram
 *  - RSI 14
 *  - Bollinger Bands 20-period, 2σ
 *  - Volume spike detection (20-day avg)
 *  - Support / Resistance (recent swing high/low over 20 days)
 *  - ADX 14 (trend strength)
 *  - Trend direction (SMA cross)
 *  - Signal category labels (RSI zone, MACD crossover, BB position, MA cross)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TechnicalIndicatorEngine {

    private final CandlestickEngine  candlestickEngine;
    private final BacktestEngine     backtestEngine;
    private final BreakoutEngine     breakoutEngine;
    private final ChartPatternEngine chartPatternEngine;

    // ─── Public entry point ────────────────────────────────────────────────────

    // ── Period constants (trading days) ───────────────────────────────────────
    private static final int BARS_6M = 126;
    private static final int BARS_1Y = 252;
    private static final int BARS_2Y = 504;
    private static final int BARS_3Y = 756;

    /**
     * Maps a lookback request (in trading days) to the nearest standard period.
     * @return trading-day count to use for price-change calculation
     */
    private int resolvePeriodBars(int lookbackDays) {
        if (lookbackDays <= 180) return BARS_6M;
        if (lookbackDays <= 400) return BARS_1Y;
        if (lookbackDays <= 700) return BARS_2Y;
        return BARS_3Y;
    }

    private String resolvePeriodLabel(int bars) {
        if (bars <= BARS_6M) return "6M";
        if (bars <= BARS_1Y) return "1Y";
        if (bars <= BARS_2Y) return "2Y";
        return "3Y";
    }

    /**
     * Convenience overload — uses the default 1-year window for price-change calculation.
     * Prefer {@link #compute(List, int)} when a specific lookback is known.
     */
    public TechnicalSignals compute(List<StockHistoryDetails> candles) {
        return compute(candles, BARS_1Y);   // default: 1-year price change
    }

    public TechnicalSignals compute(List<StockHistoryDetails> candles, int lookbackDays) {
        if (candles == null || candles.size() < 30) {
            log.warn("Not enough candles for technical analysis. Got: {}", candles == null ? 0 : candles.size());
            return TechnicalSignals.builder().build();
        }

        double[] closes  = extract(candles, "close");
        double[] highs   = extract(candles, "high");
        double[] lows    = extract(candles, "low");
        double[] volumes = extract(candles, "volume");
        int n = closes.length;

        // ─── Moving Averages ────────────────────────────────────────────────
        Double sma20  = sma(closes, 20);
        Double sma50  = sma(closes, 50);
        Double sma200 = sma(closes, 200);
        double[] ema12Arr = emaArray(closes, 12);
        double[] ema26Arr = emaArray(closes, 26);
        Double ema12 = ema12Arr[n - 1];
        Double ema26 = ema26Arr[n - 1];

        // ─── MACD ────────────────────────────────────────────────────────────
        double[] macdLineArr = new double[n];
        for (int i = 0; i < n; i++) {
            macdLineArr[i] = ema12Arr[i] - ema26Arr[i];
        }
        double[] macdSignalArr = emaArray(macdLineArr, 9);
        Double macdLine      = macdLineArr[n - 1];
        Double macdSignal    = macdSignalArr[n - 1];
        Double macdHistogram = macdLine - macdSignal;

        // ─── RSI ─────────────────────────────────────────────────────────────
        Double rsi14 = rsi(closes, 14);

        // ─── Bollinger Bands ─────────────────────────────────────────────────
        double bbMiddle = sma20 != null ? sma20 : 0.0;
        double stddev   = stddev(closes, 20);
        Double bbUpper  = bbMiddle + 2 * stddev;
        Double bbLower  = bbMiddle - 2 * stddev;
        Double bbWidth  = bbMiddle > 0 ? (bbUpper - bbLower) / bbMiddle * 100 : 0.0;

        // ─── Volume Trend (5-day avg vs 20-day avg) ──────────────────────────
        // Spec: compare 5-day average volume against 20-day average volume.
        // This measures whether RECENT activity is picking up or fading vs the
        // longer baseline — more meaningful than checking a single day.
        Double avgVolume20   = avgVolume(volumes, 20);
        Double avgVolume5    = avgVolume(volumes, 5);
        Double currentVolume = volumes[n - 1];
        // Legacy spike flag kept for backward compatibility with InvestmentSignalEngine
        boolean volumeSpike  = avgVolume20 > 0 && currentVolume > 1.5 * avgVolume20;
        // New: trend label based on 5-day vs 20-day ratio
        String volumeTrend;
        if (avgVolume20 == null || avgVolume20 == 0) {
            volumeTrend = "N/A";
        } else {
            double volRatio = avgVolume5 / avgVolume20;
            if      (volRatio > 1.3)  volumeTrend = "RISING_STRONG";   // 5d avg > 130% of 20d avg
            else if (volRatio > 1.1)  volumeTrend = "RISING";
            else if (volRatio < 0.7)  volumeTrend = "FALLING_WEAK";
            else if (volRatio < 0.9)  volumeTrend = "FALLING";
            else                      volumeTrend = "NEUTRAL";
        }

        // ─── Support & Resistance (last 50 candles) ───────────────────────────
        // Spec says 50-day window for recent high/low levels.
        // Using 20 was too narrow and gave noisy, easily-breached levels.
        int srWindow  = Math.min(50, n);
        double support    = min(lows,  n - srWindow, n);
        double resistance = max(highs, n - srWindow, n);

        // ─── ADX ─────────────────────────────────────────────────────────────
        Double adx = adx(highs, lows, closes, 14);

        // ─── Trend (SMA50 vs SMA200) ──────────────────────────────────────────
        // Spec: "Trend (50DMA vs 200DMA) → Uptrend / Downtrend / Sideways".
        // Previous code used SMA20 vs SMA50 which is a short-term signal;
        // the correct long-term trend classification uses SMA50 vs SMA200.
        String trend = "SIDEWAYS";
        if (sma50 != null && sma200 != null) {
            // Classic definition: price above both MAs and SMA50 > SMA200 = Uptrend
            if (closes[n - 1] > sma50 && sma50 > sma200)  trend = "UPTREND";
            else if (closes[n - 1] < sma50 && sma50 < sma200) trend = "DOWNTREND";
            // else: mixed signals → SIDEWAYS
        } else if (sma50 != null) {
            // SMA200 not yet available (< 200 candles); fall back to price vs SMA50
            if (closes[n - 1] > sma50)      trend = "UPTREND";
            else if (closes[n - 1] < sma50) trend = "DOWNTREND";
        }

        // ─── Price % Change over configured analysis period ──────────────────
        // Maps lookbackDays → standard trading-day count, then clamps to available data.
        int periodBars      = Math.min(resolvePeriodBars(lookbackDays), n - 1);
        String periodLabel  = resolvePeriodLabel(periodBars);
        Double priceChangePct = periodBars > 0
                ? round((closes[n - 1] - closes[n - 1 - periodBars])
                         / closes[n - 1 - periodBars] * 100)
                : null;
        log.debug("Price change ({}) = {}% over {} bars (lookbackDays={})",
                  periodLabel, priceChangePct, periodBars, lookbackDays);

        // ─── Price / 52-week range ────────────────────────────────────────────
        double currentPrice          = closes[n - 1];
        double high52 = candles.get(n - 1).getFiftyTwoWeekHigh() != null
                        ? candles.get(n - 1).getFiftyTwoWeekHigh() : max(highs, 0, n);
        double low52  = candles.get(n - 1).getFiftyTwoWeekLow() != null
                        ? candles.get(n - 1).getFiftyTwoWeekLow()  : min(lows,  0, n);
        double priceVs52H = high52 > 0 ? (currentPrice / high52 - 1) * 100 : 0;

        // ─── VWAP (latest day) ─────────────────────────────────────────────
        double vwap = candles.get(n - 1).getVwap() != null ? candles.get(n - 1).getVwap() : currentPrice;

        // ─── Signal Labels ────────────────────────────────────────────────────
        String rsiSignal     = rsiLabel(rsi14);
        String macdSigType   = macdLabel(macdLine, macdSignal,
                                          n > 1 ? macdLineArr[n - 2] : macdLine,
                                          n > 1 ? macdSignalArr[n - 2] : macdSignal);
        String bbSig         = bbLabel(currentPrice, bbUpper, bbLower);
        // Use n-1 (yesterday) as previous bar — consistent with how macdLabel uses [n-2] vs [n-1]
        // n-2 in zero-based means the bar BEFORE the current last bar
        String maSig         = maLabel(sma50, sma200,
                n > 1 ? sma(closes, 50,  n - 1) : sma50,
                n > 1 ? sma(closes, 200, n - 1) : sma200);

        // ─── Breakout analysis ────────────────────────────────────────────────
        BreakoutResult breakoutResult = breakoutEngine.analyse(candles);

        // ─── Chart pattern detection (6-month window) ─────────────────────────
        List<ChartPatternResult> chartPatterns = chartPatternEngine.detectPatterns(candles);

        // ─── Build intermediate signals for backtest fingerprint ──────────────
        // (backtest needs RSI, MACD, trend, BB — computed above — to fingerprint the current bar)
        TechnicalSignals partialSignals = TechnicalSignals.builder()
                .rsi14(round(rsi14))
                .macdLine(round(macdLine))
                .macdSignal(round(macdSignal))
                .trendDirection(trend)
                .bbSignal(bbLabel(currentPrice, bbUpper, bbLower))
                .build();

        // ─── Backtest ─────────────────────────────────────────────────────────
        BacktestResult backtestResult = backtestEngine.backtest(candles, partialSignals);

        // ─── Historical Volatility (annualised) ──────────────────────────────
        // Uses daily log returns: ln(close[i] / close[i-1]).
        // Log returns are preferred over simple returns because they're symmetric
        // and additive — the standard approach used by options traders / quants.
        Double annualizedVol = annualizedVolatility(closes);

        // ─── Candlestick & Price-Action (deterministic) ───────────────────────
        CandlestickSignals csSignals = candlestickEngine.analyse(candles);

        return TechnicalSignals.builder()
                .breakoutAnalysis(breakoutResult)
                .chartPatterns(chartPatterns)
                .backtestResult(backtestResult)
                .annualizedVolatilityPct(round(annualizedVol))
                .candlestickSignals(csSignals)
                .sma20(round(sma20))
                .sma50(round(sma50))
                .sma200(round(sma200))
                .ema12(round(ema12))
                .ema26(round(ema26))
                .macdLine(round(macdLine))
                .macdSignal(round(macdSignal))
                .macdHistogram(round(macdHistogram))
                .rsi14(round(rsi14))
                .bbUpper(round(bbUpper))
                .bbMiddle(round(bbMiddle))
                .bbLower(round(bbLower))
                .bbWidth(round(bbWidth))
                .avgVolume20(round(avgVolume20))
                .avgVolume5(round(avgVolume5))
                .currentVolume(round(currentVolume))
                .volumeSpike(volumeSpike)
                .volumeTrend(volumeTrend)
                .supportLevel(round(support))
                .resistanceLevel(round(resistance))
                .trendDirection(trend)
                .adx14(round(adx))
                .vwap(round(vwap))
                .fiftyTwoWeekHigh(round(high52))
                .fiftyTwoWeekLow(round(low52))
                .currentPrice(round(currentPrice))
                .priceVs52WeekHighPct(round(priceVs52H))
                .priceChangePct(priceChangePct)
                .priceChangePeriodLabel(periodLabel)
                .rsiSignal(rsiSignal)
                .macdSignalType(macdSigType)
                .bbSignal(bbSig)
                .maSignal(maSig)
                .build();
    }

    // ─── Indicator Calculations ────────────────────────────────────────────────

    /** Simple Moving Average over last `period` values */
    private Double sma(double[] arr, int period) {
        return sma(arr, period, arr.length);
    }

    /** SMA ending at index `endExclusive - 1` */
    private Double sma(double[] arr, int period, int endExclusive) {
        if (endExclusive < period) return null;
        double sum = 0;
        for (int i = endExclusive - period; i < endExclusive; i++) sum += arr[i];
        return sum / period;
    }

    /**
     * Computes full EMA array using standard exponential smoothing (multiplier = 2/(period+1)).
     * Note: this is standard EMA as used in MACD, NOT Wilder's smoothing
     * (which uses multiplier 1/period and is used in RSI/ADX).
     */
    private double[] emaArray(double[] arr, int period) {
        double[] ema = new double[arr.length];
        double multiplier = 2.0 / (period + 1);
        // Seed with simple average of first `period` values
        double seed = 0;
        int start = Math.min(period, arr.length);
        for (int i = 0; i < start; i++) seed += arr[i];
        seed /= start;
        ema[start - 1] = seed;
        for (int i = start; i < arr.length; i++) {
            ema[i] = (arr[i] - ema[i - 1]) * multiplier + ema[i - 1];
        }
        // Fill initial with seed to avoid 0-gaps
        for (int i = 0; i < start - 1; i++) ema[i] = seed;
        return ema;
    }

    /**
     * RSI using Wilder's smoothing.
     * Returns value in [0, 100].
     */
    private Double rsi(double[] closes, int period) {
        int n = closes.length;
        if (n < period + 1) return null;
        double avgGain = 0, avgLoss = 0;
        for (int i = 1; i <= period; i++) {
            double change = closes[i] - closes[i - 1];
            if (change > 0) avgGain += change;
            else avgLoss += Math.abs(change);
        }
        avgGain /= period;
        avgLoss /= period;
        for (int i = period + 1; i < n; i++) {
            double change = closes[i] - closes[i - 1];
            double gain   = change > 0 ? change : 0;
            double loss   = change < 0 ? Math.abs(change) : 0;
            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
        }
        if (avgLoss == 0) return 100.0;
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    /** Population standard deviation of last `period` values */
    private double stddev(double[] arr, int period) {
        int n = arr.length;
        if (n < period) return 0;
        double mean = 0;
        for (int i = n - period; i < n; i++) mean += arr[i];
        mean /= period;
        double variance = 0;
        for (int i = n - period; i < n; i++) variance += Math.pow(arr[i] - mean, 2);
        return Math.sqrt(variance / period);
    }

    /** Average of last `period` volume values */
    private Double avgVolume(double[] volumes, int period) {
        int n = volumes.length;
        if (n < period) return 0.0;
        double sum = 0;
        for (int i = n - period; i < n; i++) sum += volumes[i];
        return sum / period;
    }

    /** Min of arr[from..toExclusive] */
    private double min(double[] arr, int from, int toExclusive) {
        double m = Double.MAX_VALUE;
        for (int i = from; i < toExclusive; i++) if (arr[i] < m) m = arr[i];
        return m == Double.MAX_VALUE ? 0 : m;
    }

    /** Max of arr[from..toExclusive] */
    private double max(double[] arr, int from, int toExclusive) {
        double m = -Double.MAX_VALUE;
        for (int i = from; i < toExclusive; i++) if (arr[i] > m) m = arr[i];
        return m == -Double.MAX_VALUE ? 0 : m;
    }

    /**
     * ADX (Average Directional Index) — measures trend strength.
     * Returns value in [0, 100].
     */
    private Double adx(double[] highs, double[] lows, double[] closes, int period) {
        int n = highs.length;
        if (n < period * 2 + 1) return null;

        double[] trueRange = new double[n];
        double[] plusDM    = new double[n];
        double[] minusDM   = new double[n];

        for (int i = 1; i < n; i++) {
            double highDiff = highs[i]  - highs[i - 1];
            double lowDiff  = lows[i - 1] - lows[i];
            plusDM[i]  = highDiff > lowDiff && highDiff > 0 ? highDiff : 0;
            minusDM[i] = lowDiff > highDiff && lowDiff > 0 ? lowDiff  : 0;
            trueRange[i] = Math.max(highs[i] - lows[i],
                           Math.max(Math.abs(highs[i] - closes[i - 1]),
                                    Math.abs(lows[i]  - closes[i - 1])));
        }

        // Wilder smoothing for first period
        double atrS = 0, plusS = 0, minusS = 0;
        for (int i = 1; i <= period; i++) {
            atrS  += trueRange[i];
            plusS  += plusDM[i];
            minusS += minusDM[i];
        }

        double[] dx = new double[n];
        for (int i = period + 1; i < n; i++) {
            atrS  = atrS  - (atrS / period)  + trueRange[i];
            plusS  = plusS  - (plusS / period)  + plusDM[i];
            minusS = minusS - (minusS / period) + minusDM[i];
            double plusDI  = atrS > 0 ? 100 * plusS  / atrS : 0;
            double minusDI = atrS > 0 ? 100 * minusS / atrS : 0;
            double diSum   = plusDI + minusDI;
            dx[i] = diSum > 0 ? 100 * Math.abs(plusDI - minusDI) / diSum : 0;
        }

        // ADX = SMA of DX over period
        Double adxVal = sma(dx, period);
        return adxVal;
    }

    // ─── Label Helpers ─────────────────────────────────────────────────────────

    private String rsiLabel(Double rsi) {
        if (rsi == null) return "N/A";
        if (rsi < 30) return "OVERSOLD";
        if (rsi > 70) return "OVERBOUGHT";
        return "NEUTRAL";
    }

    private String macdLabel(double macdNow, double signalNow, double macdPrev, double signalPrev) {
        boolean bullCross = macdPrev < signalPrev && macdNow > signalNow;
        boolean bearCross = macdPrev > signalPrev && macdNow < signalNow;
        if (bullCross) return "BULLISH_CROSSOVER";
        if (bearCross) return "BEARISH_CROSSOVER";
        return macdNow > signalNow ? "BULLISH" : "BEARISH";
    }

    private String bbLabel(double price, double upper, double lower) {
        double range = upper - lower;
        if (range == 0) return "N/A";
        double pct = (price - lower) / range;
        if (pct < 0.15) return "NEAR_LOWER";
        if (pct > 0.85) return "NEAR_UPPER";
        return "INSIDE";
    }

    private String maLabel(Double sma50now, Double sma200now, Double sma50prev, Double sma200prev) {
        if (sma50now == null || sma200now == null || sma50prev == null || sma200prev == null) return "N/A";
        boolean goldCross = sma50prev < sma200prev && sma50now > sma200now;
        boolean deathCross = sma50prev > sma200prev && sma50now < sma200now;
        if (goldCross)  return "GOLDEN_CROSS";
        if (deathCross) return "DEATH_CROSS";
        return sma50now > sma200now ? "BULLISH" : "BEARISH";
    }

    // ─── Utility ───────────────────────────────────────────────────────────────

    /**
     * Extracts a price/volume field from candles into a double[].
     *
     * <p><b>Null handling:</b> NSE data has gaps (holidays, trading halts).
     * A null value is forward-filled from the previous candle so that indicator
     * arithmetic is never poisoned by a 0.  The first element falls back to 0
     * only if the very first candle is null (extremely rare).</p>
     *
     * <p>Zero-filling was the previous behaviour and silently corrupted SMA, RSI,
     * MACD and Bollinger calculations whenever a null appeared mid-series.</p>
     */
    private double[] extract(List<StockHistoryDetails> candles, String field) {
        double[] arr = new double[candles.size()];
        double lastValid = 0;
        for (int i = 0; i < candles.size(); i++) {
            StockHistoryDetails c = candles.get(i);
            Double raw = switch (field) {
                case "close"  -> c.getClose();
                case "high"   -> c.getHigh();
                case "low"    -> c.getLow();
                case "open"   -> c.getOpen();
                case "volume" -> { double v = parseVolume(c.getVolume()); yield v > 0 ? v : null; }
                default       -> null;
            };
            if (raw != null) {
                arr[i] = raw;
                lastValid = raw;
            } else {
                // Forward-fill: reuse the last known value
                arr[i] = lastValid;
                log.debug("Forward-fill applied for field={} at candle index={} (null in source data)", field, i);
            }
        }
        return arr;
    }

    private double parseVolume(String vol) {
        if (vol == null || vol.isBlank()) return 0;
        try { return Double.parseDouble(vol.replace(",", "").trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    /**
     * Annualised historical volatility using daily log returns.
     *
     * Formula:
     *   dailyReturn[i] = ln(close[i] / close[i-1])
     *   stdDev         = population std dev of dailyReturn[]
     *   annVol         = stdDev × √252 × 100  (in %)
     *
     * Returns null if fewer than 10 data points are available.
     * Uses ALL available candles (not just the lookback window) for a
     * stable volatility estimate.
     */
    private Double annualizedVolatility(double[] closes) {
        int n = closes.length;
        if (n < 10) return null;
        double[] logReturns = new double[n - 1];
        for (int i = 1; i < n; i++) {
            if (closes[i - 1] > 0) {
                logReturns[i - 1] = Math.log(closes[i] / closes[i - 1]);
            }
        }
        double mean = 0;
        for (double r : logReturns) mean += r;
        mean /= logReturns.length;
        double variance = 0;
        for (double r : logReturns) variance += (r - mean) * (r - mean);
        variance /= logReturns.length;
        double dailyStdDev = Math.sqrt(variance);
        return dailyStdDev * Math.sqrt(252) * 100; // annualised %
    }

    private Double round(Double v) {
        if (v == null) return null;
        return Math.round(v * 100.0) / 100.0;
    }
}

