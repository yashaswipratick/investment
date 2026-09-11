package com.stock.stock_analyser.engine;

import com.stock.stock_analyser.dto.FundamentalAnalysis;
import com.stock.stock_analyser.dto.FundamentalCriteriaResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FundamentalCriteriaEngineTest {
    private final FundamentalCriteriaEngine engine = new FundamentalCriteriaEngine();
    private FundamentalAnalysis f(double revenue, String margin, double de, double roe, double roce) {
        return FundamentalAnalysis.builder().revenueGrowthYoY(revenue).netMargin(12.0).netMarginTrend(margin).debtToEquity(de).interestCoverage(5.0).roe(roe).roce(roce).peRatio(20.0).promoterHolding(50.0).promoterPledge(0.0).build();
    }
    @Test void revenueThresholdsAreDeterministic() {
        assertEquals("PASS", engine.greaterThan(16.0,15,">15%","fail").getStatus());
        assertEquals("FAIL", engine.greaterThan(15.0,15,">15%","fail").getStatus());
        assertEquals("UNAVAILABLE", engine.greaterThan(null,15,">15%","fail").getStatus());
    }
    @Test void debtRoeAndRoceThresholdsAreDeterministic() {
        assertEquals("PASS", engine.debt(f(16,"STABLE",0.4,20,22)).getStatus());
        assertEquals("PASS", engine.debt(f(16,"STABLE",0.9,20,22)).getStatus());
        assertEquals("FAIL", engine.debt(f(16,"STABLE",1.0,20,22)).getStatus());
        assertEquals("PASS", engine.greaterThan(20.0,15,">15%","fail").getStatus());
        assertEquals("FAIL", engine.greaterThan(15.0,15,">15%","fail").getStatus());
        assertEquals("PASS", engine.greaterThan(22.0,18,">18%","fail").getStatus());
        assertEquals("FAIL", engine.greaterThan(18.0,18,">18%","fail").getStatus());
    }
    @Test void marginPromoterAndPledgeRulesAreDeterministic() {
        assertEquals("PASS", engine.margin(f(16,"IMPROVING",0.4,20,22)).getStatus());
        assertEquals("PASS", engine.margin(f(16,"STABLE",0.4,20,22)).getStatus());
        assertEquals("FAIL", engine.margin(f(16,"DECLINING",0.4,20,22)).getStatus());
        assertEquals("PASS", engine.promoterHolding(50.0,49.0).getStatus());
        assertEquals("PASS", engine.promoterHolding(50.0,50.0).getStatus());
        assertEquals("FAIL", engine.promoterHolding(49.0,50.0).getStatus());
        assertEquals("PASS", engine.pledge(0.0).getStatus());
        assertEquals("FAIL", engine.pledge(10.0).getStatus());
        assertEquals("UNAVAILABLE", engine.pledge(null).getStatus());
    }
    @Test void overallStatusIsFailBeforeUnavailableAndUnavailableBeforePass() {
        FundamentalCriteriaResult pass = engine.aggregate(engine.greaterThan(16.0,15,">15%","fail"), engine.margin(f(16,"STABLE",0.4,20,22)), engine.debt(f(16,"STABLE",0.4,20,22)), engine.greaterThan(20.0,15,">15%","fail"), engine.greaterThan(22.0,18,">18%","fail"), engine.pledge(0.0), engine.promoterHolding(50.0,50.0), engine.pledge(0.0));
        assertEquals("PASS", pass.getOverallStatus());
        assertEquals("FAIL", engine.aggregate(engine.greaterThan(14.0,15,">15%","fail"), pass.getPatMarginTrend(), pass.getDebtToEquity(), pass.getRoe(), pass.getRoce(), pass.getPeValuation(), pass.getPromoterHolding(), pass.getPromoterPledge()).getOverallStatus());
        assertEquals("UNAVAILABLE", engine.aggregate(engine.greaterThan(null,15,">15%","fail"), pass.getPatMarginTrend(), pass.getDebtToEquity(), pass.getRoe(), pass.getRoce(), pass.getPeValuation(), pass.getPromoterHolding(), pass.getPromoterPledge()).getOverallStatus());
    }
    @Test void peAndPromoterAreUnavailableWhenContextIsMissing() {
        FundamentalCriteriaResult result = engine.evaluate(f(16,"STABLE",0.4,20,22));
        assertEquals("UNAVAILABLE", result.getPeValuation().getStatus());
        assertEquals("UNAVAILABLE", result.getPromoterHolding().getStatus());
    }
    @Test void bankDebtEquityIsUnavailable() {
        var bank = FundamentalAnalysis.builder().debtToEquity(null).interestCoverage(null).build();
        assertEquals("UNAVAILABLE", engine.debt(bank).getStatus());
    }
}
