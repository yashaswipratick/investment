package com.stock.stock_analyser.engine;

import com.stock.stock_analyser.dto.InvestmentRecommendation;
import com.stock.stock_analyser.dto.TechnicalSignals;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Combines all technical signals to generate a concrete investment recommendation:
 *  - BUY / HOLD / SELL / AVOID
 *  - Entry price zone
 *  - Target (exit) price
 *  - Stop-loss price
 *  - Confidence score (0-100)
 *  - Risk/reward ratio
 *
 * Scoring model (max 100 points):
 *  RSI zone         : ±20 pts
 *  MACD signal      : ±20 pts
 *  MA alignment     : ±20 pts
 *  Bollinger signal : ±15 pts
 *  Volume spike     : ±10 pts
 *  ADX trend        : ±10 pts
 *  52-week position : ±5 pts
 *
 *  Score ≥ 65  → BUY
 *  Score 45-64 → HOLD
 *  Score 25-44 → SELL
 *  Score <  25 → AVOID
 */
@Slf4j
@Component
public class InvestmentSignalEngine {

    public InvestmentRecommendation recommend(TechnicalSignals t) {
        if (t == null || t.getCurrentPrice() == null) {
            return InvestmentRecommendation.builder()
                    .action("NO_DATA")
                    .rationale("Insufficient data to produce a recommendation.")
                    .build();
        }

        double price = t.getCurrentPrice();
        List<String> reasons = new ArrayList<>();
        int score = 50; // neutral starting point

        // ── RSI score ─────────────────────────────────────────────────────────
        if ("OVERSOLD".equals(t.getRsiSignal())) {
            score += 20;
            reasons.add(String.format("RSI=%.1f is OVERSOLD (<30) — potential reversal up", t.getRsi14()));
        } else if ("OVERBOUGHT".equals(t.getRsiSignal())) {
            score -= 20;
            reasons.add(String.format("RSI=%.1f is OVERBOUGHT (>70) — momentum may fade", t.getRsi14()));
        } else if (t.getRsi14() != null) {
            // Slightly bullish if 40-60 range (healthy)
            if (t.getRsi14() >= 45 && t.getRsi14() <= 60) score += 5;
            reasons.add(String.format("RSI=%.1f is neutral", t.getRsi14()));
        }

        // ── MACD score ────────────────────────────────────────────────────────
        if ("BULLISH_CROSSOVER".equals(t.getMacdSignalType())) {
            score += 20;
            reasons.add("MACD bullish crossover detected — early uptrend signal");
        } else if ("BEARISH_CROSSOVER".equals(t.getMacdSignalType())) {
            score -= 20;
            reasons.add("MACD bearish crossover detected — early downtrend signal");
        } else if ("BULLISH".equals(t.getMacdSignalType())) {
            score += 10;
            reasons.add("MACD line above signal — bullish momentum ongoing");
        } else if ("BEARISH".equals(t.getMacdSignalType())) {
            score -= 10;
            reasons.add("MACD line below signal — bearish momentum ongoing");
        }

        // ── Moving Average score ──────────────────────────────────────────────
        if ("GOLDEN_CROSS".equals(t.getMaSignal())) {
            score += 20;
            reasons.add("Golden Cross: SMA50 crossed above SMA200 — strong long-term bullish");
        } else if ("DEATH_CROSS".equals(t.getMaSignal())) {
            score -= 20;
            reasons.add("Death Cross: SMA50 crossed below SMA200 — strong long-term bearish");
        } else if ("BULLISH".equals(t.getMaSignal())) {
            score += 10;
            reasons.add("SMA50 above SMA200 — price in long-term uptrend");
        } else if ("BEARISH".equals(t.getMaSignal())) {
            score -= 10;
            reasons.add("SMA50 below SMA200 — price in long-term downtrend");
        }

        // ── Bollinger Bands score ─────────────────────────────────────────────
        if ("NEAR_LOWER".equals(t.getBbSignal())) {
            score += 15;
            reasons.add("Price near lower Bollinger Band — potential mean reversion bounce");
        } else if ("NEAR_UPPER".equals(t.getBbSignal())) {
            score -= 15;
            reasons.add("Price near upper Bollinger Band — overbought, resistance likely");
        } else {
            reasons.add("Price inside Bollinger Bands — consolidation zone");
        }

        // ── Volume score (5-day avg vs 20-day avg trend) ─────────────────────
        // Primary: use the 5d/20d volume trend for a more stable signal.
        // Secondary: use volumeSpike as a confirmation on top.
        String vt = t.getVolumeTrend();
        if (vt != null) {
            switch (vt) {
                case "RISING_STRONG" -> { score += 10; reasons.add("Volume surging (5d avg > 130% of 20d avg) — strong conviction"); }
                case "RISING"        -> { score += 5;  reasons.add("Volume rising (5d avg > 20d avg) — buying interest growing"); }
                case "FALLING_WEAK"  -> { score -= 10; reasons.add("Volume drying up sharply — weak conviction, possible distribution"); }
                case "FALLING"       -> { score -= 5;  reasons.add("Volume declining (5d avg < 20d avg) — fading interest"); }
                default              -> reasons.add("Volume neutral — no strong confirmation signal"); // NEUTRAL / N/A
            }
        }
        // Volume spike on top of trend confirms direction
        if (t.isVolumeSpike()) {
            if (score > 50) { score += 5; reasons.add("Volume spike confirms buying interest"); }
            else            { score -= 5; reasons.add("Volume spike on downward move — selling pressure"); }
        }

        // ── ADX trend strength ────────────────────────────────────────────────
        if (t.getAdx14() != null) {
            if (t.getAdx14() > 40) {
                if ("UPTREND".equals(t.getTrendDirection())) {
                    score += 10; reasons.add(String.format("Strong uptrend confirmed (ADX=%.1f)", t.getAdx14()));
                } else if ("DOWNTREND".equals(t.getTrendDirection())) {
                    score -= 10; reasons.add(String.format("Strong downtrend confirmed (ADX=%.1f)", t.getAdx14()));
                }
            } else if (t.getAdx14() < 20) {
                reasons.add(String.format("Weak trend (ADX=%.1f) — range-bound/sideways", t.getAdx14()));
            }
        }

        // ── 52-week range position score ──────────────────────────────────────
        if (t.getPriceVs52WeekHighPct() != null) {
            double pct = t.getPriceVs52WeekHighPct(); // negative means below 52w high
            if (pct < -30) {
                score += 5;
                reasons.add(String.format("Price is %.0f%% below 52-week high — deep value zone", Math.abs(pct)));
            } else if (pct > -5) {
                score -= 5;
                reasons.add("Price near 52-week high — limited upside, watch for distribution");
            }
        }

        // ── Price % Change — overall trajectory over analysis period ─────────
        // Period is determined by the request's lookbackDays (6M / 1Y / 2Y / 3Y).
        // A positive trajectory adds confidence to a BUY; a strongly negative one
        // warns of structural decline regardless of short-term signals.
        String changePeriod = t.getPriceChangePeriodLabel() != null ? t.getPriceChangePeriodLabel() : "period";
        if (t.getPriceChangePct() != null) {
            double chg6m = t.getPriceChangePct();
            if (chg6m > 20) {
                score += 5;
                reasons.add(String.format("Strong %s price trajectory: +%.1f%%", changePeriod, chg6m));
            } else if (chg6m > 5) {
                score += 3;
                reasons.add(String.format("Positive %s trajectory: +%.1f%%", changePeriod, chg6m));
            } else if (chg6m < -20) {
                score -= 5;
                reasons.add(String.format("Weak %s trajectory: %.1f%% — structural decline risk", changePeriod, chg6m));
            } else if (chg6m < -5) {
                score -= 3;
                reasons.add(String.format("Negative %s trajectory: %.1f%%", changePeriod, chg6m));
            } else {
                reasons.add(String.format("Flat %s trajectory: %.1f%% — sideways consolidation", changePeriod, chg6m));
            }
        }

        // ── VWAP confirmation ─────────────────────────────────────────────────
        // Price above VWAP = institutional buying; below = selling pressure.
        // Small ±5 adjustment so VWAP acts as a tiebreaker, not a dominant signal.
        if (t.getVwap() != null && t.getVwap() > 0) {
            if (price > t.getVwap() * 1.005) {
                score += 5;
                reasons.add(String.format("Price ₹%.2f above VWAP ₹%.2f — intraday buying pressure", price, t.getVwap()));
            } else if (price < t.getVwap() * 0.995) {
                score -= 5;
                reasons.add(String.format("Price ₹%.2f below VWAP ₹%.2f — intraday selling pressure", price, t.getVwap()));
            }
        }

        // ── Clamp score ───────────────────────────────────────────────────────
        score = Math.max(0, Math.min(100, score));

        // ── Count how many long-term signals are actually available ───────────
        // On short windows (<60 candles), SMA200, ADX and MA-cross are null/N/A.
        // Prevent a confident BUY/SELL from being emitted when only short-term
        // signals (RSI + MACD + BB) are present — those alone cannot justify a
        // high-confidence directional call.
        boolean hasSma200   = t.getSma200() != null;
        boolean hasAdx      = t.getAdx14()  != null;
        boolean hasMaSignal = t.getMaSignal() != null && !"N/A".equals(t.getMaSignal());
        int longTermSignals = (hasSma200 ? 1 : 0) + (hasAdx ? 1 : 0) + (hasMaSignal ? 1 : 0);

        // Cap action at HOLD if fewer than 2 long-term signals are present
        // so we never emit BUY/SELL purely on RSI+MACD+BB from a 35-candle window.
        boolean sufficientContext = longTermSignals >= 2;
        if (!sufficientContext) {
            score = Math.min(score, 64); // caps at HOLD even if short-term signals are bullish
            reasons.add(String.format(
                "⚠️ Confidence capped at HOLD: only %d/3 long-term signals available " +
                "(SMA200=%s, ADX=%s, MA-cross=%s). Fetch more history for full analysis.",
                longTermSignals,
                hasSma200  ? "✓" : "✗",
                hasAdx     ? "✓" : "✗",
                hasMaSignal? "✓" : "✗"
            ));
        }

        // ── Derive action ─────────────────────────────────────────────────────
        String action;
        String timeframe;
        if      (score >= 65) { action = "BUY";   timeframe = score >= 80 ? "SHORT_TERM" : "MEDIUM_TERM"; }
        else if (score >= 45) { action = "HOLD";  timeframe = "MEDIUM_TERM"; }
        else if (score >= 25) { action = "SELL";  timeframe = "SHORT_TERM"; }
        else                  { action = "AVOID"; timeframe = "LONG_TERM"; }

        // ── Entry zone ────────────────────────────────────────────────────────
        // Entry low = support level or current price - 2% cushion
        double entryLow  = t.getSupportLevel() != null && t.getSupportLevel() > 0
                           ? t.getSupportLevel()
                           : price * 0.98;
        double entryHigh = price * 1.01;  // Allow up to 1% above current price

        // ── Stop-loss ─────────────────────────────────────────────────────────
        // Place below lower Bollinger Band or support - 2%, whichever is lower
        double bbLower = t.getBbLower() != null ? t.getBbLower() : price * 0.95;
        double stopLoss = Math.min(bbLower, entryLow * 0.97);
        stopLoss = Math.max(stopLoss, price * 0.90); // never more than 10% below current price

        // ── Target price ─────────────────────────────────────────────────────
        // Use resistance if meaningful; else use 1.5 * risk above entry
        double riskPerUnit = entryHigh - stopLoss;
        double target = t.getResistanceLevel() != null && t.getResistanceLevel() > entryHigh
                        ? t.getResistanceLevel()
                        : entryHigh + (riskPerUnit * 2.0); // 2:1 R/R minimum

        // Upgrade target if near 52-week high and score is high
        if (t.getFiftyTwoWeekHigh() != null && t.getFiftyTwoWeekHigh() > target && score > 70) {
            target = t.getFiftyTwoWeekHigh();
        }

        // ── Potential pct ─────────────────────────────────────────────────────
        double upside   = entryHigh > 0 ? (target - entryHigh) / entryHigh * 100  : 0;
        double downside = entryLow  > 0 ? (entryLow - stopLoss) / entryLow * 100  : 0;
        double rr = downside > 0 ? upside / downside : 0;

        return InvestmentRecommendation.builder()
                .action(action)
                .confidenceScore(score)
                .entryPriceLow(round(entryLow))
                .entryPriceHigh(round(entryHigh))
                .targetPrice(round(target))
                .stopLossPrice(round(stopLoss))
                .potentialUpsidePct(round(upside))
                .potentialDownsidePct(round(downside))
                .riskRewardRatio(round(rr))
                .rationale(String.join(" | ", reasons))
                .timeframe(timeframe)
                .build();
    }

    private Double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

