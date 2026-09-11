package com.stock.stock_analyser.engine;

import com.stock.stock_analyser.dto.FundamentalCriteriaResult;
import com.stock.stock_analyser.dto.InvestmentRecommendation;
import com.stock.stock_analyser.dto.TechnicalCriteriaResult;
import com.stock.stock_analyser.dto.TechnicalSignals;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InvestmentSignalEngineTest {
    private final InvestmentSignalEngine engine=new InvestmentSignalEngine();
    private TechnicalSignals technical(){return TechnicalSignals.builder().currentPrice(110.0).sma20(105.0).sma50(100.0).sma200(90.0).rsi14(60.0).rsiSignal("NEUTRAL").macdSignalType("BULLISH_CROSSOVER").maSignal("BULLISH").volumeSpike(true).avgVolume20(100.0).currentVolume(200.0).volumeTrend("RISING_STRONG").adx14(30.0).trendDirection("UPTREND").fiftyTwoWeekHigh(112.0).priceVs52WeekHighPct(-1.8).vwap(100.0).bbSignal("INSIDE").bbMiddle(105.0).bbLower(95.0).resistanceLevel(130.0).build();}
    private TechnicalCriteriaResult tech(String s){return TechnicalCriteriaResult.builder().overallStatus(s).build();}
    private FundamentalCriteriaResult fund(String s){return FundamentalCriteriaResult.builder().overallStatus(s).build();}
    @Test void buyRequiresBothHardGates(){assertEquals("BUY",engine.recommend(technical(),tech("PASS"),fund("PASS")).getAction()); assertNotEquals("BUY",engine.recommend(technical(),tech("PASS"),fund("FAIL")).getAction()); assertNotEquals("BUY",engine.recommend(technical(),tech("PASS"),fund("UNAVAILABLE")).getAction()); assertNotEquals("BUY",engine.recommend(technical(),tech("FAIL"),fund("PASS")).getAction()); assertNotEquals("BUY",engine.recommend(technical(),tech("FAIL"),fund("FAIL")).getAction());}
    @Test void unavailableFundamentalsProduceInsufficientData(){assertEquals("INSUFFICIENT_DATA",engine.recommend(technical(),tech("PASS"),fund("UNAVAILABLE")).getAction());}
    @Test void failedTechnicalGateProducesWaitForConfirmation(){assertEquals("WAIT_FOR_CONFIRMATION",engine.recommend(technical(),tech("FAIL"),fund("PASS")).getAction());}
    @Test void buyTradeSetupHasConsistentLongRelationshipsAndMath(){
        InvestmentRecommendation r=engine.recommend(technical(),tech("PASS"),fund("PASS"));
        assertEquals("BUY",r.getAction());
        assertTrue(r.getStopLossPrice() < r.getEntryPriceLow());
        assertTrue(r.getEntryPriceLow() < r.getEntryPriceHigh());
        assertTrue(r.getTargetPrice() > r.getEntryPriceHigh());
        double risk=r.getEntryPriceHigh()-r.getStopLossPrice();
        double reward=r.getTargetPrice()-r.getEntryPriceHigh();
        assertEquals(reward/risk,r.getRiskRewardRatio(),0.000001);
        assertTrue(risk>0 && reward>0 && Double.isFinite(r.getRiskRewardRatio()));
    }
    @Test void nonBuyStatesDoNotCreateActionableLongSetup(){
        InvestmentRecommendation wait=engine.recommend(technical(),tech("FAIL"),fund("PASS"));
        assertEquals("WAIT_FOR_CONFIRMATION",wait.getAction()); assertNull(wait.getTargetPrice()); assertNull(wait.getStopLossPrice());
        InvestmentRecommendation insufficient=engine.recommend(technical(),tech("PASS"),fund("UNAVAILABLE"));
        assertEquals("INSUFFICIENT_DATA",insufficient.getAction()); assertNull(insufficient.getEntryPriceLow()); assertNull(insufficient.getTargetPrice());
    }
}
