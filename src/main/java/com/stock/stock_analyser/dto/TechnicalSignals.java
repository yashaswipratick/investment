package com.stock.stock_analyser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * All computed technical indicator values for a stock.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TechnicalSignals {

    // ── Moving Averages ──────────────────────────────────────────────────────
    private Double sma20;         // Simple Moving Average 20-day
    private Double sma50;         // Simple Moving Average 50-day
    private Double sma200;        // Simple Moving Average 200-day
    private Double ema12;         // Exponential Moving Average 12-day
    private Double ema26;         // Exponential Moving Average 26-day

    // ── MACD ─────────────────────────────────────────────────────────────────
    private Double macdLine;      // EMA12 - EMA26
    private Double macdSignal;    // 9-day EMA of MACD line
    private Double macdHistogram; // MACD line - Signal line

    // ── RSI ──────────────────────────────────────────────────────────────────
    private Double rsi14;         // Relative Strength Index (14-day)

    // ── Bollinger Bands ──────────────────────────────────────────────────────
    private Double bbUpper;       // Upper band (SMA20 + 2σ)
    private Double bbMiddle;      // Middle band (SMA20)
    private Double bbLower;       // Lower band (SMA20 - 2σ)
    private Double bbWidth;       // (Upper - Lower) / Middle * 100

    // ── Volume ───────────────────────────────────────────────────────────────
    private Double avgVolume20;   // Average 20-day volume
    private Double avgVolume5;    // Average 5-day volume (recent activity)
    private Double currentVolume; // Latest day volume
    private boolean volumeSpike;  // currentVolume > 1.5x avgVolume20
    /**
     * Volume trend: 5-day avg vs 20-day avg ratio.
     * RISING_STRONG | RISING | NEUTRAL | FALLING | FALLING_WEAK | N/A
     */
    private String volumeTrend;

    // ── Support & Resistance ─────────────────────────────────────────────────
    private Double supportLevel;  // Recent swing low
    private Double resistanceLevel; // Recent swing high

    // ── Trend ────────────────────────────────────────────────────────────────
    private String trendDirection;  // UPTREND | DOWNTREND | SIDEWAYS
    private Double adx14;           // Average Directional Index (trend strength 0-100)

    // ── VWAP ─────────────────────────────────────────────────────────────────
    private Double vwap;            // Latest day VWAP

    // ── 52-Week Range ────────────────────────────────────────────────────────
    private Double fiftyTwoWeekHigh;
    private Double fiftyTwoWeekLow;
    private Double currentPrice;
    private Double priceVs52WeekHighPct; // (currentPrice / 52wHigh - 1) * 100

    // ── Price Change over analysis period ────────────────────────────────────
    /**
     * Percentage price change over the configured lookback period.
     * The window used is determined by the request's lookbackDays:
     *   ≤ 180 trading days  →  6M  (~126 bars)
     *   ≤ 400 trading days  →  1Y  (~252 bars)
     *   ≤ 700 trading days  →  2Y  (~504 bars)
     *     > 700 trading days →  3Y  (~756 bars)
     * Positive = upward trajectory; negative = downward trajectory.
     */
    private Double priceChangePct;

    /**
     * Human-readable label for the price change period: "6M", "1Y", "2Y", "3Y".
     * Included in API response so clients know which window the change covers.
     */
    private String priceChangePeriodLabel;

    // ── Signal Summaries ─────────────────────────────────────────────────────
    private String rsiSignal;       // OVERSOLD | NEUTRAL | OVERBOUGHT
    private String macdSignalType;  // BULLISH_CROSSOVER | BEARISH_CROSSOVER | NEUTRAL
    private String bbSignal;        // NEAR_LOWER | NEAR_UPPER | INSIDE
    private String maSignal;        // GOLDEN_CROSS | DEATH_CROSS | BULLISH | BEARISH
}

