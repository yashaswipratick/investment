package com.stock.stock_analyser.engine;

import com.stock.stock_analyser.dto.FundamentalCriteriaResult;
import com.stock.stock_analyser.dto.InvestmentRecommendation;
import com.stock.stock_analyser.dto.TechnicalCriteriaResult;
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
        return recommend(t, null, null);
    }

    /** Applies Marcus hard gates after the existing informational score is calculated. */
    public InvestmentRecommendation recommend(TechnicalSignals t, TechnicalCriteriaResult technicalCriteria,
                                               FundamentalCriteriaResult fundamentalCriteria) {
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

        if (technicalCriteria != null && fundamentalCriteria != null) {
            String technicalStatus = technicalCriteria.getOverallStatus();
            String fundamentalStatus = fundamentalCriteria.getOverallStatus();
            if ("PASS".equals(technicalStatus) && "PASS".equals(fundamentalStatus)) {
                // Marcus hard gates are authoritative. The legacy score remains
                // confidence/ranking information and must not downgrade PASS/PASS.
                action = "BUY";
            } else if ("UNAVAILABLE".equals(fundamentalStatus)) {
                action = "INSUFFICIENT_DATA";
                timeframe = "MEDIUM_TERM";
                reasons.add("Marcus BUY blocked: mandatory fundamental data is unavailable.");
            } else if ("FAIL".equals(fundamentalStatus)) {
                action = "HOLD";
                timeframe = "MEDIUM_TERM";
                reasons.add("Marcus BUY blocked: one or more hard fundamental criteria failed.");
            } else if ("UNAVAILABLE".equals(technicalStatus)) {
                action = "INSUFFICIENT_DATA";
                timeframe = "MEDIUM_TERM";
                reasons.add("Marcus BUY blocked: mandatory technical data is unavailable.");
            } else {
                action = "WAIT_FOR_CONFIRMATION";
                timeframe = "MEDIUM_TERM";
                reasons.add("Marcus BUY blocked: technical gate is not currently satisfied.");
            }
        }

        // ── Decision-driven trade setup ──────────────────────────────────────
        // The hard-gated action above is authoritative. Trade fields are only
        // populated when they are meaningful for that action; they never change it.
        if ("BUY".equals(action)) {
            double candidateLow = price * 0.96;
            if (t.getSma20() != null && t.getSma20() > candidateLow && t.getSma20() < price) candidateLow = t.getSma20();
            if (t.getBbMiddle() != null && t.getBbMiddle() > candidateLow && t.getBbMiddle() < price) candidateLow = t.getBbMiddle();

            double entryLow = round(candidateLow);
            double entryHigh = round(price * 1.005);
            if (entryLow >= entryHigh) entryLow = round(entryHigh * 0.99);

            // Technical invalidation: nearest defensible support, without an
            // artificial risk cap that could move the stop above the entry zone.
            double bbLower = t.getBbLower() != null && t.getBbLower() > 0 ? t.getBbLower() : entryLow * 0.94;
            double stopLoss = Math.min(bbLower, entryLow * 0.94);
            if (!(stopLoss > 0 && stopLoss < entryLow)) stopLoss = entryLow * 0.94;

            double riskPerUnit = entryHigh - stopLoss;
            // Do not let a nearby resistance level create a sub-2R BUY setup.
            // Resistance is usable only when it preserves the Marcus minimum
            // reward/risk quality floor; otherwise the deterministic 2R target
            // remains the safer fallback.
            double minimumTarget = entryHigh + riskPerUnit * 2.0;
            double target = t.getResistanceLevel() != null && t.getResistanceLevel() >= minimumTarget
                    ? t.getResistanceLevel()
                    : minimumTarget;
            if (t.getFiftyTwoWeekHigh() != null && t.getFiftyTwoWeekHigh() >= minimumTarget
                    && t.getFiftyTwoWeekHigh() > target) target = t.getFiftyTwoWeekHigh();
            if (!(target > entryHigh)) target = entryHigh + riskPerUnit * 2.0;

            // Single documented convention: R:R uses the conservative/worst-case
            // long entry (entryHigh): risk = entryHigh - stop, reward = target - entryHigh.
            double risk = entryHigh - stopLoss;
            double reward = target - entryHigh;
            Double rr = risk > 0 && reward > 0 ? reward / risk : null;
            double upside = reward / entryHigh * 100;
            double downside = risk / entryHigh * 100;

            return InvestmentRecommendation.builder()
                    .action(action).confidenceScore(score)
                    .entryPriceLow(entryLow).entryPriceHigh(entryHigh)
                    .targetPrice(round(target)).stopLossPrice(round(stopLoss))
                    .potentialUpsidePct(round(upside)).potentialDownsidePct(round(downside))
                    .riskRewardRatio(rr == null || !Double.isFinite(rr) ? null : rr)
                    .rationale(String.join(" | ", reasons)).timeframe(timeframe).build();
        }

        // WAIT can expose a trigger zone, but must not look like an active trade.
        if ("WAIT_FOR_CONFIRMATION".equals(action)) {
            double entryLow = round(price);
            double entryHigh = round(price * 1.005);
            return InvestmentRecommendation.builder()
                    .action(action).confidenceScore(score)
                    .entryPriceLow(entryLow).entryPriceHigh(entryHigh)
                    .rationale(String.join(" | ", reasons) + " | Wait for technical confirmation before entering.")
                    .timeframe(timeframe).build();
        }

        // HOLD / INSUFFICIENT_DATA / bearish states have no fabricated long setup.
        return InvestmentRecommendation.builder()
                .action(action).confidenceScore(score)
                .rationale(String.join(" | ", reasons))
                .timeframe(timeframe).build();
    }

    private Double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

