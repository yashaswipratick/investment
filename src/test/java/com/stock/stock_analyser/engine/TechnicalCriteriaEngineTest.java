package com.stock.stock_analyser.engine;

import com.stock.stock_analyser.dto.TechnicalCriteriaResult;
import com.stock.stock_analyser.dto.TechnicalSignals;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TechnicalCriteriaEngineTest {
    private final TechnicalCriteriaEngine engine = new TechnicalCriteriaEngine();
    private TechnicalSignals passing() {
        return TechnicalSignals.builder().currentPrice(110.0).ema50(105.0).ema200(90.0).sma50(105.0).sma200(90.0).rsi14(60.0).macdSignalType("BULLISH")
                .currentVolume(200.0).avgVolume20(100.0).volumeSpike(true).fiftyTwoWeekHigh(112.0).adx14(30.0)
                .breakoutAnalysis(BreakoutResult.builder().signal("FRESH_BREAKOUT").daysAgoBreakout(1).breakoutLevel(108.0).build()).build();
    }
    @Test void lockedTechnicalGatePassesWhenAllConditionsPass() {
        TechnicalCriteriaResult r=engine.evaluate(passing()); assertEquals("PASS",r.getOverallStatus());
        assertEquals("PASS",r.getPriceTrendStatus()); assertEquals("PASS",r.getRsiStatus()); assertEquals("PASS",r.getMacdStatus());
        assertEquals("PASS",r.getBreakoutVolumeStatus()); assertEquals("PASS",r.getBreakoutStatus()); assertEquals("PASS",r.getAdxStatus());
    }
    @Test void rsiAndVolumeFailuresBlockTechnicalGate() { TechnicalSignals t=passing(); t.setRsi14(54.9); t.setVolumeSpike(false); TechnicalCriteriaResult r=engine.evaluate(t); assertEquals("FAIL",r.getOverallStatus()); assertEquals("FAIL",r.getRsiStatus()); assertEquals("FAIL",r.getBreakoutVolumeStatus()); }
    @Test void missingLongTermDataIsUnavailable() { TechnicalSignals t=passing(); t.setEma200(null); t.setAdx14(null); TechnicalCriteriaResult r=engine.evaluate(t); assertEquals("UNAVAILABLE",r.getOverallStatus()); assertEquals("UNAVAILABLE",r.getPriceTrendStatus()); assertEquals("UNAVAILABLE",r.getAdxStatus()); }
    @Test void historicalBreakoutDoesNotPassWhenNotNear52WeekHigh() { TechnicalSignals t=passing(); t.setFiftyTwoWeekHigh(150.0); t.setCurrentPrice(110.0); t.getBreakoutAnalysis().setSignal("HISTORICAL_BREAKOUT"); assertEquals("FAIL",engine.evaluate(t).getBreakoutStatus()); }
}
