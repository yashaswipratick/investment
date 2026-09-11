package com.stock.stock_analyser.engine;

import com.stock.stock_analyser.dto.FundamentalAnalysis;
import com.stock.stock_analyser.dto.FundamentalCriteriaResult;
import com.stock.stock_analyser.dto.FundamentalCriteriaResult.CriterionResult;
import org.springframework.stereotype.Component;

/** Evaluates Marcus hard fundamental criteria independently of informational scores. */
@Component
public class FundamentalCriteriaEngine {
    private static final double REV = 15.0, DE = 1.0, ROE = 15.0, ROCE = 18.0;

    public FundamentalCriteriaResult evaluate(FundamentalAnalysis f) {
        if (f == null) return unavailableAll("Fundamental analysis is unavailable.");
        CriterionResult revenue = greaterThan(f.getRevenueGrowthYoY(), REV, "> 15%", "Revenue growth must be above 15%.");
        CriterionResult margin = margin(f);
        CriterionResult debt = debt(f);
        CriterionResult roe = greaterThan(f.getRoe(), ROE, "> 15%", "ROE must be above 15%.");
        CriterionResult roce = greaterThan(f.getRoce(), ROCE, "> 18%", "ROCE must be above 18%.");
        CriterionResult pe = unavailable("Sector-relative P/E versus growth data is unavailable; no universal threshold is fabricated.");
        pe.setValue(f.getPeRatio()); pe.setThreshold("Reasonable relative to sector/growth");
        CriterionResult promoter = promoterHolding(f.getPromoterHolding(), null);
        CriterionResult pledge = pledge(f.getPromoterPledge());
        return aggregate(revenue, margin, debt, roe, roce, pe, promoter, pledge);
    }

    CriterionResult greaterThan(Double value, double threshold, String text, String failReason) {
        if (!valid(value)) return unavailable("Required value is unavailable or invalid.");
        boolean pass = value > threshold;
        return CriterionResult.builder().value(value).status(pass ? "PASS" : "FAIL").threshold(text)
                .reason(pass ? "Value satisfies Marcus hard criterion." : failReason).build();
    }

    CriterionResult margin(FundamentalAnalysis f) {
        Double value = f.getNetMargin(); String trend = f.getNetMarginTrend();
        if (!valid(value) || trend == null || "UNAVAILABLE".equalsIgnoreCase(trend)) return unavailable("Latest and previous PAT margin history is required.");
        if ("DECLINING".equalsIgnoreCase(trend)) return CriterionResult.builder().value(value).status("FAIL").threshold("Stable or IMPROVING PAT margin").reason("PAT margin is declining.").build();
        if ("STABLE".equalsIgnoreCase(trend) || "IMPROVING".equalsIgnoreCase(trend)) return CriterionResult.builder().value(value).status("PASS").threshold("Stable or IMPROVING PAT margin").reason("PAT margin is stable or improving.").build();
        return unavailable("PAT margin trend is not recognized.");
    }

    CriterionResult debt(FundamentalAnalysis f) {
        if (f.getDebtToEquity() == null && f.getInterestCoverage() == null) return unavailable("Conventional Debt/Equity is not meaningful for this supported bank model.");
        Double value = f.getDebtToEquity();
        if (!valid(value)) return unavailable("Debt/Equity is unavailable or invalid.");
        boolean pass = value < DE;
        return CriterionResult.builder().value(value).status(pass ? "PASS" : "FAIL").threshold("< 1.0")
                .reason(pass ? (value < 0.5 ? "Below the preferred 0.5 level." : "Below Marcus hard limit of 1.0.") : "Debt/Equity is at or above 1.0.").build();
    }

    CriterionResult promoterHolding(Double current, Double previous) {
        if (!valid(current) || !valid(previous)) return unavailable("Promoter holding history is unavailable.");
        boolean pass = current >= previous;
        return CriterionResult.builder().value(current).status(pass ? "PASS" : "FAIL").threshold("Stable or increasing")
                .reason(pass ? "Promoter holding is stable or increasing." : "Promoter holding is declining.").build();
    }

    CriterionResult pledge(Double value) {
        if (!valid(value)) return unavailable("Promoter pledge data is unavailable.");
        if (value == 0.0) return CriterionResult.builder().value(value).status("PASS").threshold("No major pledge concern; explicit zero acceptable").reason("Source explicitly reports zero promoter pledge.").build();
        return CriterionResult.builder().value(value).status("FAIL").threshold("No major pledge concern").reason("A non-zero sourced pledge is conservatively treated as a concern; no unsupported major-pledge threshold is invented.").build();
    }

    FundamentalCriteriaResult aggregate(CriterionResult... c) {
        int pass=0, fail=0, unavailable=0;
        for (CriterionResult x:c) { if ("PASS".equals(x.getStatus())) pass++; else if ("FAIL".equals(x.getStatus())) fail++; else unavailable++; }
        String overall = fail > 0 ? "FAIL" : unavailable > 0 ? "UNAVAILABLE" : "PASS";
        return FundamentalCriteriaResult.builder().overallStatus(overall).revenueGrowth(c[0]).patMarginTrend(c[1]).debtToEquity(c[2]).roe(c[3]).roce(c[4]).peValuation(c[5]).promoterHolding(c[6]).promoterPledge(c[7]).passedCount(pass).failedCount(fail).unavailableCount(unavailable).summary(String.format("Marcus fundamentals: %d PASS, %d FAIL, %d UNAVAILABLE; overall %s.", pass, fail, unavailable, overall)).build();
    }

    private FundamentalCriteriaResult unavailableAll(String reason) { CriterionResult c=unavailable(reason); return aggregate(c,c,c,c,c,c,c,c); }
    private CriterionResult unavailable(String reason) { return CriterionResult.builder().status("UNAVAILABLE").reason(reason).build(); }
    private boolean valid(Double v) { return v != null && Double.isFinite(v); }
}
