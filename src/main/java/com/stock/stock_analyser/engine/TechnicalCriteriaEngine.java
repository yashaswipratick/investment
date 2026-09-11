package com.stock.stock_analyser.engine;

import com.stock.stock_analyser.dto.TechnicalCriteriaResult;
import com.stock.stock_analyser.dto.TechnicalSignals;
import org.springframework.stereotype.Component;

/** Evaluates Marcus's locked technical gate without changing indicator calculations. */
@Component
public class TechnicalCriteriaEngine {
    public TechnicalCriteriaResult evaluate(TechnicalSignals t) {
        if (t == null) return result("UNAVAILABLE", "Technical analysis is unavailable.", "UNAVAILABLE", "UNAVAILABLE", "UNAVAILABLE", "UNAVAILABLE", "UNAVAILABLE", "UNAVAILABLE");
        String price = t.getCurrentPrice() == null || t.getEma50() == null || t.getEma200() == null ? "UNAVAILABLE" :
                (t.getCurrentPrice() > t.getEma50() && t.getEma50() > t.getEma200() ? "PASS" : "FAIL");
        String rsi = t.getRsi14() == null ? "UNAVAILABLE" : (t.getRsi14() >= 55 && t.getRsi14() <= 75 ? "PASS" : "FAIL");
        String macd = t.getMacdSignalType() == null ? "UNAVAILABLE" : (("BULLISH".equals(t.getMacdSignalType()) || "BULLISH_CROSSOVER".equals(t.getMacdSignalType())) ? "PASS" : "FAIL");
        String volume = breakoutVolumeStatus(t);
        String breakout = breakoutStatus(t);
        String adx = t.getAdx14() == null ? "UNAVAILABLE" : (t.getAdx14() > 25 ? "PASS" : "FAIL");
        String overall = anyUnavailable(price,rsi,macd,volume,breakout,adx) ? "UNAVAILABLE" : (allPass(price,rsi,macd,volume,breakout,adx) ? "PASS" : "FAIL");
        return result(overall, String.format("Technical gate: price trend=%s, RSI=%s, MACD=%s, breakout volume=%s, breakout=%s, ADX=%s.", price,rsi,macd,volume,breakout,adx), price,rsi,macd,volume,breakout,adx);
    }

    private String breakoutVolumeStatus(TechnicalSignals t) {
        if (t.getBreakoutAnalysis() == null || t.getBreakoutAnalysis().getBreakoutVolumeRatio() == null
                || !Double.isFinite(t.getBreakoutAnalysis().getBreakoutVolumeRatio())) return "UNAVAILABLE";
        return t.getBreakoutAnalysis().getBreakoutVolumeRatio() >= 1.5 ? "PASS" : "FAIL";
    }

    private String breakoutStatus(TechnicalSignals t) {
        if (t.getBreakoutAnalysis() == null || t.getBreakoutAnalysis().getSignal() == null) return "UNAVAILABLE";
        boolean nearHigh = t.getFiftyTwoWeekHigh() != null && t.getCurrentPrice() != null && t.getFiftyTwoWeekHigh() > 0 && t.getCurrentPrice() / t.getFiftyTwoWeekHigh() >= 0.95;
        boolean fresh = "FRESH_BREAKOUT".equals(t.getBreakoutAnalysis().getSignal()) && t.getBreakoutAnalysis().getDaysAgoBreakout() != null
                && t.getBreakoutAnalysis().getDaysAgoBreakout() <= 5 && t.getCurrentPrice() != null
                && t.getBreakoutAnalysis().getBreakoutLevel() != null && t.getCurrentPrice() >= t.getBreakoutAnalysis().getBreakoutLevel()
                && t.getBreakoutAnalysis().getBreakoutVolumeRatio() != null && t.getBreakoutAnalysis().getBreakoutVolumeRatio() >= 1.5;
        return nearHigh || fresh ? "PASS" : "FAIL";
    }
    private boolean allPass(String... s) { for(String x:s) if(!"PASS".equals(x)) return false; return true; }
    private boolean anyUnavailable(String... s) { for(String x:s) if("UNAVAILABLE".equals(x)) return true; return false; }
    private TechnicalCriteriaResult result(String overall,String summary,String price,String rsi,String macd,String volume,String breakout,String adx) {
        return TechnicalCriteriaResult.builder().overallStatus(overall).priceTrendStatus(price).rsiStatus(rsi).macdStatus(macd).breakoutVolumeStatus(volume).breakoutStatus(breakout).adxStatus(adx).summary(summary).build();
    }
}
