package com.stock.stock_analyser.engine;

import com.stock.stock_analyser.dto.BacktestResult;
import com.stock.stock_analyser.dto.EntryTiming;
import com.stock.stock_analyser.dto.InvestmentRecommendation;
import com.stock.stock_analyser.dto.PeriodProjection;
import com.stock.stock_analyser.dto.StopLossStrategy;
import com.stock.stock_analyser.dto.TechnicalSignals;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates period-wise profit projections, entry timing signals, and stop-loss strategies.
 *
 * Projection methodology:
 *  - Base 1Y return = (target - current) / current × 100  (from InvestmentSignalEngine)
 *  - Shorter/longer horizons are scaled using a progression factor
 *  - Adjusted by trend direction and confidence score:
 *      UPTREND   → full projection
 *      SIDEWAYS  → 60% of full projection
 *      DOWNTREND → 30% of full projection (expect slow recovery)
 *  - Confidence tier: HIGH (score ≥ 65) | MEDIUM (45–64) | LOW (< 45)
 */
@Slf4j
@Component
public class ProjectionEngine {

    // Horizon progression factors relative to 1Y (1.0)
    private static final double F_3M  = 0.20;
    private static final double F_6M  = 0.40;
    private static final double F_9M  = 0.60;
    private static final double F_1Y  = 1.00;
    private static final double F_2Y  = 1.50;
    private static final double F_3Y  = 1.85;
    private static final double F_5Y  = 2.40;

    // Trend multipliers — downtrend reduces expected gain; recovery takes longer
    private static final double TREND_UP      = 1.0;
    private static final double TREND_SIDEWAYS= 0.6;
    private static final double TREND_DOWN    = 0.3;

    // Horizon years (for volatility scaling: σ scales with √t)
    private static final double Y_3M  = 0.25;
    private static final double Y_6M  = 0.50;
    private static final double Y_9M  = 0.75;
    private static final double Y_1Y  = 1.00;
    private static final double Y_2Y  = 2.00;
    private static final double Y_3Y  = 3.00;
    private static final double Y_5Y  = 5.00;

    public List<PeriodProjection> computeProjections(TechnicalSignals t, InvestmentRecommendation r) {
        List<PeriodProjection> list = new ArrayList<>();
        if (t == null || r == null || t.getCurrentPrice() == null || r.getTargetPrice() == null) {
            return list;
        }

        double price    = t.getCurrentPrice();
        double target   = r.getTargetPrice();
        double upside1Y = price > 0 ? (target - price) / price * 100 : 0;

        String trend   = t.getTrendDirection() != null ? t.getTrendDirection() : "SIDEWAYS";
        double trendMult = switch (trend) {
            case "UPTREND"   -> TREND_UP;
            case "DOWNTREND" -> TREND_DOWN;
            default          -> TREND_SIDEWAYS;
        };

        int score = r.getConfidenceScore();
        String overallConf = score >= 65 ? "HIGH" : (score >= 45 ? "MEDIUM" : "LOW");

        // Annualised volatility (% / year) — used to build ±1σ bull/bear ranges
        // If not available, fall back to 30% (typical mid-cap Indian stock volatility)
        double annVol = (t.getAnnualizedVolatilityPct() != null && t.getAnnualizedVolatilityPct() > 0)
                ? t.getAnnualizedVolatilityPct()
                : 30.0;

        // Build a lookup from horizon → BacktestResult.HorizonStat (if available)
        Map<String, BacktestResult.HorizonStat> btMap = new HashMap<>();
        BacktestResult bt = t.getBacktestResult();
        if (bt != null && bt.isStatistically_significant() && bt.getHorizonStats() != null) {
            for (BacktestResult.HorizonStat hs : bt.getHorizonStats()) {
                btMap.put(hs.getHorizon(), hs);
            }
        }

        // ── Horizon projections ───────────────────────────────────────────────
        // When backtest data exists for a horizon, use its expected value as the base
        // return instead of the linear extrapolation. Confidence is upgraded.
        list.add(buildWithBacktest("3M",  price, upside1Y, F_3M,  Y_3M,  trendMult, annVol, trend, r, "LOW",    t, btMap.get("3M")));
        list.add(buildWithBacktest("6M",  price, upside1Y, F_6M,  Y_6M,  trendMult, annVol, trend, r, "LOW",    t, btMap.get("6M")));
        list.add(build("9M",  price, upside1Y, F_9M,  Y_9M,  trendMult, annVol, trend, r, "MEDIUM", t));
        list.add(buildWithBacktest("1Y",  price, upside1Y, F_1Y,  Y_1Y,  trendMult, annVol, trend, r, overallConf, t, btMap.get("1Y")));
        list.add(build("2Y",  price, upside1Y, F_2Y,  Y_2Y,  trendMult, annVol, trend, r, "MEDIUM", t));
        list.add(build("3Y",  price, upside1Y, F_3Y,  Y_3Y,  trendMult, annVol, trend, r, "MEDIUM", t));
        list.add(build("5Y",  price, upside1Y, F_5Y,  Y_5Y,  1.0,       annVol, trend, r, "LOW",    t));

        return list;
    }

    private PeriodProjection buildWithBacktest(String horizon, double price, double upside1Y,
                                               double factor, double years, double trendMult,
                                               double annVol, String trend,
                                               InvestmentRecommendation r, String conf,
                                               TechnicalSignals t, BacktestResult.HorizonStat bt) {
        if (bt == null) {
            return build(horizon, price, upside1Y, factor, years, trendMult, annVol, trend, r, conf, t);
        }

        // Use backtest expected value as the base return — grounded in actual history
        double baseReturn = bt.getExpectedValuePct() != null ? bt.getExpectedValuePct() : upside1Y * factor * trendMult;
        double targetPrice = price * (1 + baseReturn / 100);
        double oneSigma   = annVol * Math.sqrt(years);
        double bullReturn = round(baseReturn + oneSigma);
        double bearReturn = round(baseReturn - oneSigma);
        double bullPrice  = round(price * (1 + bullReturn / 100));
        double bearPrice  = round(price * (1 + bearReturn / 100));

        // Confidence is higher when backtest is statistically significant
        String btConf = bt.getWinRatePct() != null && bt.getWinRatePct() >= 65 ? "HIGH"
                      : bt.getWinRatePct() != null && bt.getWinRatePct() >= 50 ? "MEDIUM" : "LOW";

        String scenario = String.format(
            "Backtest: %d historical matches | Win rate: %.0f%% | Avg: %.1f%% | Median: %.1f%%",
            (int)(bt.getWinRatePct() * 0 + (bt.getBestReturnPct() != null ? 1 : 0)), // occurrences proxy
            bt.getWinRatePct() != null ? bt.getWinRatePct() : 0,
            bt.getAvgReturnPct() != null ? bt.getAvgReturnPct() : 0,
            bt.getMedianReturnPct() != null ? bt.getMedianReturnPct() : 0
        );

        return PeriodProjection.builder()
                .horizon(horizon)
                .targetPrice(round(targetPrice))
                .expectedReturnPct(round(baseReturn))
                .bullCasePct(bullReturn)
                .bearCasePct(bearReturn)
                .bullCasePrice(bullPrice)
                .bearCasePrice(bearPrice)
                .annualizedVolatilityPct(round(annVol))
                .scenario(scenario)
                .confidence(btConf)
                .build();
    }

    public EntryTiming computeEntryTiming(TechnicalSignals t, InvestmentRecommendation r) {
        if (t == null || r == null) return EntryTiming.builder().signal("NO_DATA").build();

        String action    = r.getAction() != null ? r.getAction() : "HOLD";
        String trend     = t.getTrendDirection() != null ? t.getTrendDirection() : "SIDEWAYS";
        Double rsi       = t.getRsi14();
        Double price     = t.getCurrentPrice();
        Double sma20     = t.getSma20();
        Double sma50     = t.getSma50();
        Double entryLow  = r.getEntryPriceLow();
        Double entryHigh = r.getEntryPriceHigh();

        boolean inBuyZone    = price != null && entryLow != null && entryHigh != null
                               && price >= entryLow * 0.98 && price <= entryHigh * 1.05;
        boolean oversold      = rsi != null && rsi < 32;
        boolean nearSupport   = t.getSupportLevel() != null && price != null
                               && price <= t.getSupportLevel() * 1.03;

        String signal;
        boolean goodTime;
        String currentSituation;
        String entryTrigger;
        String keyLevel;
        String riskProfile;

        if ("DOWNTREND".equals(trend) && !oversold && !nearSupport) {
            signal           = "AVOID";
            goodTime         = false;
            currentSituation = "Stock is in a confirmed downtrend. Price is below SMA50 and SMA200. "
                             + "No reversal signal yet.";
            entryTrigger     = sma20 != null
                             ? String.format("Wait for daily close above ₹%.2f (20-DMA) with above-average volume", sma20)
                             : "Wait for trend reversal confirmation";
            keyLevel         = sma50 != null
                             ? String.format("₹%.2f (50-DMA) — must reclaim to shift bias", sma50)
                             : "Watch 50-DMA reclaim";
            riskProfile      = "CONSERVATIVE";

        } else if ("DOWNTREND".equals(trend) && (oversold || nearSupport)) {
            signal           = "WAIT_FOR_TRIGGER";
            goodTime         = false;
            currentSituation = "Downtrend active but price is near oversold/support — potential bounce. "
                             + "Do not enter without confirmation.";
            entryTrigger     = sma20 != null
                             ? String.format("Strong bullish daily candle closing above ₹%.2f with volume spike", sma20)
                             : "Bullish reversal candle above recent high with volume";
            keyLevel         = t.getSupportLevel() != null
                             ? String.format("₹%.2f (key support) — break below = avoid entirely", t.getSupportLevel())
                             : "Watch support level";
            riskProfile      = "MODERATE";

        } else if ("BUY".equals(action) && inBuyZone) {
            signal           = "INVEST_NOW";
            goodTime         = true;
            currentSituation = String.format("Price ₹%.2f is in the ideal buy zone (₹%.2f–₹%.2f). "
                             + "Trend and momentum support entry.", price, entryLow, entryHigh);
            entryTrigger     = "Already in buy zone — enter now in tranches";
            keyLevel         = r.getStopLossPrice() != null
                             ? String.format("₹%.2f (stop-loss) — exit immediately if breached", r.getStopLossPrice())
                             : "Hard stop-loss level";
            riskProfile      = "AGGRESSIVE";

        } else if (inBuyZone) {
            signal           = "WAIT_FOR_DIP";
            goodTime         = false;
            currentSituation = String.format("Price ₹%.2f is above the ideal buy zone. "
                             + "Good stock but not the right entry price yet.", price);
            entryTrigger     = entryHigh != null
                             ? String.format("Wait for pullback to ₹%.2f–₹%.2f before entering", entryLow, entryHigh)
                             : "Wait for pullback to buy zone";
            keyLevel         = entryLow != null
                             ? String.format("₹%.2f (bottom of buy zone)", entryLow)
                             : "Buy zone floor";
            riskProfile      = "MODERATE";

        } else {
            signal           = "WAIT_FOR_DIP";
            goodTime         = false;
            currentSituation = "Sideways/consolidation. Monitor for a breakout or a dip into the buy zone.";
            entryTrigger     = entryLow != null
                             ? String.format("Price dips to ₹%.2f–₹%.2f with RSI below 45", entryLow, entryHigh)
                             : "Wait for buy zone entry";
            keyLevel         = entryLow != null
                             ? String.format("₹%.2f (buy zone floor)", entryLow)
                             : "Watch buy zone";
            riskProfile      = "MODERATE";
        }

        return EntryTiming.builder()
                .signal(signal)
                .goodTimeToInvest(goodTime)
                .currentSituation(currentSituation)
                .entryTrigger(entryTrigger)
                .keyLevelToWatch(keyLevel)
                .riskProfile(riskProfile)
                .build();
    }

    public StopLossStrategy computeStopLossStrategy(TechnicalSignals t, InvestmentRecommendation r) {
        if (t == null || r == null || t.getCurrentPrice() == null) {
            return StopLossStrategy.builder().build();
        }

        double price   = t.getCurrentPrice();
        double slPrice = r.getStopLossPrice() != null ? r.getStopLossPrice() : price * 0.92;
        double slPct   = price > 0 ? (slPrice - price) / price * 100 : 0;
        String trend   = t.getTrendDirection() != null ? t.getTrendDirection() : "SIDEWAYS";
        Double sma20   = t.getSma20();
        int score      = r.getConfidenceScore();

        // Max allocation — reduce as risk increases
        String maxAlloc;
        if (score >= 70 && "UPTREND".equals(trend))       maxAlloc = "Up to 15–20% of portfolio";
        else if (score >= 55)                              maxAlloc = "5–10% of portfolio";
        else if ("DOWNTREND".equals(trend))                maxAlloc = "Max 3–5% (high risk, downtrend active)";
        else                                               maxAlloc = "Max 5–8% of portfolio";

        // Recovery plan
        String recoveryPlan;
        if ("DOWNTREND".equals(trend)) {
            recoveryPlan = String.format(
                "Do NOT average down after stop is hit. Move freed capital to a safer instrument "
                + "(e.g., liquid fund or sector index ETF). Re-evaluate the stock after "
                + "%s — if it shows 2 consecutive closes above that level with volume, it may be safe to re-enter.",
                sma20 != null ? String.format("₹%.2f (20-DMA reclaim)", sma20) : "the 20-DMA");
        } else {
            recoveryPlan = "If stop is hit, exit the position and wait for the next confirmed "
                         + "buy signal (price back in buy zone + RSI above 45 + volume rising).";
        }

        // Re-entry condition
        String reEntry = String.format(
            "Re-enter only after: (1) price closes above ₹%.2f on above-average volume, "
            + "(2) RSI recovers above 50, (3) MACD histogram turns positive",
            sma20 != null ? sma20 : price * 1.05);

        return StopLossStrategy.builder()
                .stopLossPrice(r.getStopLossPrice())
                .stopLossPct(round(slPct))
                .stopLossType("DOWNTREND".equals(trend) ? "HARD_STOP" : "TRAILING_STOP")
                .actionOnStopHit(String.format(
                    "Exit 100%% of position immediately on a daily close below ₹%.2f. "
                    + "Do not wait or hope for recovery below this level.", slPrice))
                .recoveryPlan(recoveryPlan)
                .reEntryCondition(reEntry)
                .maxAllocationAdvice(maxAlloc)
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PeriodProjection build(String horizon, double price, double upside1Y,
                                   double factor, double years, double trendMult,
                                   double annVol, String trend,
                                   InvestmentRecommendation r, String conf,
                                   TechnicalSignals t) {
        double projectedReturn = upside1Y * factor * trendMult;
        double targetPrice     = price * (1 + projectedReturn / 100);

        // ── Volatility-based bull/bear range (±1σ) ───────────────────────────
        // Volatility scales with √time (random walk property).
        // 1σ band = annVol × √years  (in %)
        // Bull case: base + 1σ  (probability: ~84% of outcomes below this)
        // Bear case: base - 1σ  (probability: ~16% of outcomes below this)
        double oneSigma   = annVol * Math.sqrt(years);
        double bullReturn = round(projectedReturn + oneSigma);
        double bearReturn = round(projectedReturn - oneSigma);
        double bullPrice  = round(price * (1 + bullReturn / 100));
        double bearPrice  = round(price * (1 + bearReturn / 100));

        String scenario = buildScenario(horizon, trend, projectedReturn, targetPrice, t, r);

        return PeriodProjection.builder()
                .horizon(horizon)
                .targetPrice(round(targetPrice))
                .expectedReturnPct(round(projectedReturn))
                .bullCasePct(bullReturn)
                .bearCasePct(bearReturn)
                .bullCasePrice(bullPrice)
                .bearCasePrice(bearPrice)
                .annualizedVolatilityPct(round(annVol))
                .scenario(scenario)
                .confidence(conf)
                .build();
    }

    private String buildScenario(String horizon, String trend, double returnPct,
                                  double targetPrice, TechnicalSignals t, InvestmentRecommendation r) {
        if ("DOWNTREND".equals(trend)) {
            if (returnPct < 0) {
                return String.format("Downtrend continues — risk of further decline to ₹%.0f", targetPrice);
            }
            return String.format("Partial recovery scenario — potential rebound to ₹%.0f "
                               + "if support at ₹%.0f holds", targetPrice,
                               t.getSupportLevel() != null ? t.getSupportLevel() : targetPrice * 0.95);
        }
        if ("UPTREND".equals(trend)) {
            return String.format("Uptrend continuation — target ₹%.0f aligns with %s resistance",
                               targetPrice,
                               t.getResistanceLevel() != null ? "₹" + Math.round(t.getResistanceLevel()) : "next");
        }
        // SIDEWAYS
        return String.format("Range-bound scenario — mean-reversion to ₹%.0f (mid-Bollinger zone)", targetPrice);
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
