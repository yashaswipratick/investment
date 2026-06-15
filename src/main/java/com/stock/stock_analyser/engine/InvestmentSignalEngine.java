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

        // ── Volume score ──────────────────────────────────────────────────────
        if (t.isVolumeSpike()) {
            // Volume spike confirms trend direction
            if (score > 50) { score += 10; reasons.add("Volume spike confirms buying interest"); }
            else             { score -= 10; reasons.add("Volume spike on downward move — selling pressure"); }
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

        // ── Clamp score ───────────────────────────────────────────────────────
        score = Math.max(0, Math.min(100, score));

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

