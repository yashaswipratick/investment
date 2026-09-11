package com.stock.stock_analyser.engine;

import com.stock.stock_analyser.dto.EntryTiming;
import com.stock.stock_analyser.dto.InvestmentRecommendation;
import com.stock.stock_analyser.dto.TechnicalSignals;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProjectionEngineTest {
    private final ProjectionEngine engine = new ProjectionEngine();

    private TechnicalSignals technical() {
        return TechnicalSignals.builder().currentPrice(100.0).trendDirection("UPTREND").rsi14(60.0).build();
    }

    @Test void buyMapsToInvestNowOnlyWithValidSetup() {
        InvestmentRecommendation r = InvestmentRecommendation.builder().action("BUY")
                .entryPriceLow(98.0).entryPriceHigh(100.5).stopLossPrice(94.0).targetPrice(115.0)
                .riskRewardRatio((115.0 - 100.5) / (100.5 - 94.0)).build();
        EntryTiming timing = engine.computeEntryTiming(technical(), r);
        assertEquals("INVEST_NOW", timing.getSignal());
        assertTrue(timing.isGoodTimeToInvest());
    }

    @Test void waitNeverMapsToInvestNow() {
        InvestmentRecommendation r = InvestmentRecommendation.builder().action("WAIT_FOR_CONFIRMATION").entryPriceLow(100.0).entryPriceHigh(101.0).build();
        EntryTiming timing = engine.computeEntryTiming(technical(), r);
        assertEquals("WAIT_FOR_CONFIRMATION", timing.getSignal());
        assertFalse(timing.isGoodTimeToInvest());
    }

    @Test void holdHasNoActionableEntryTiming() {
        EntryTiming timing = engine.computeEntryTiming(technical(), InvestmentRecommendation.builder().action("HOLD").build());
        assertEquals("HOLD", timing.getSignal());
        assertFalse(timing.isGoodTimeToInvest());
    }

    @Test void insufficientDataMapsToDataRequired() {
        EntryTiming timing = engine.computeEntryTiming(technical(), InvestmentRecommendation.builder().action("INSUFFICIENT_DATA").build());
        assertEquals("DATA_REQUIRED", timing.getSignal());
        assertFalse(timing.isGoodTimeToInvest());
    }

    @Test void invalidBuySetupCannotProduceInvestNow() {
        InvestmentRecommendation r = InvestmentRecommendation.builder().action("BUY")
                .entryPriceLow(98.0).entryPriceHigh(100.5).stopLossPrice(101.0).targetPrice(115.0).riskRewardRatio(2.0).build();
        EntryTiming timing = engine.computeEntryTiming(technical(), r);
        assertNotEquals("INVEST_NOW", timing.getSignal());
        assertFalse(timing.isGoodTimeToInvest());
    }
}
