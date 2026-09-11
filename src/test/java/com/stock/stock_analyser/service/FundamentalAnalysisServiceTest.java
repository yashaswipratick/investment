package com.stock.stock_analyser.service;

import com.stock.stock_analyser.dto.FundamentalAnalysis;
import com.stock.stock_analyser.fundamental.FundamentalDataSet;
import com.stock.stock_analyser.fundamental.FundamentalPeriodData;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FundamentalAnalysisServiceTest {

    private static final LocalDate DATE = LocalDate.of(2025, 3, 31);
    private static final LocalDate PREVIOUS_DATE = DATE.minusYears(1);

    private FundamentalAnalysis analyse(FundamentalPeriodData latest) {
        FundamentalAnalysisService service = new FundamentalAnalysisService("/unused", null);
        return service.analyse(new FundamentalDataSet("TEST", List.of(basePeriod(PREVIOUS_DATE), latest)));
    }

    @Test
    void missingProfitProducesNullEps() {
        FundamentalAnalysis result = analyse(period(null, 10.0, 100.0, 50.0, 20.0, 10.0, 5.0, 2.0, 30.0, 100.0, 1000.0));
        assertNull(result.getEps());
    }

    @Test
    void missingSharesProducesNullEps() {
        FundamentalAnalysis result = analyse(period(100.0, null, 100.0, 50.0, 20.0, 10.0, 5.0, 2.0, 30.0, 100.0, 1000.0));
        assertNull(result.getEps());
    }

    @Test
    void missingRevenueProducesNullNetMargin() {
        FundamentalAnalysis result = analyse(period(100.0, 10.0, null, 50.0, 20.0, 10.0, 5.0, 2.0, 30.0, 100.0, 1000.0));
        assertNull(result.getNetMargin());
    }

    @Test
    void missingDebtProducesNullDebtToEquity() {
        FundamentalAnalysis result = analyse(period(100.0, 10.0, 100.0, 50.0, 20.0, null, 5.0, 2.0, 30.0, 100.0, 1000.0));
        assertNull(result.getDebtToEquity());
    }

    @Test
    void missingCfoProducesNullCfoToPat() {
        FundamentalAnalysis result = analyse(period(100.0, 10.0, 100.0, 50.0, 20.0, 10.0, 5.0, 2.0, null, 100.0, 1000.0));
        assertNull(result.getCfoToPat());
    }

    @Test
    void missingInterestProducesNullInterestCoverage() {
        FundamentalAnalysis result = analyse(period(100.0, 10.0, 100.0, 50.0, 20.0, 10.0, 5.0, null, 30.0, 100.0, 1000.0));
        assertNull(result.getInterestCoverage());
    }

    @Test
    void missingRevenueEndpointProducesNullRevenueCagr() {
        FundamentalPeriodData latest = period(100.0, 10.0, null, 50.0, 20.0, 10.0, 5.0, 2.0, 30.0, 100.0, 1000.0);
        FundamentalAnalysis result = analyse(latest);
        assertNull(result.getRevenueCagr3Y());
    }

    @Test
    void missingProfitIsNotCountedAsNonPositiveProfitYear() {
        FundamentalAnalysis result = analyse(period(null, 10.0, 100.0, 50.0, 20.0, 10.0, 5.0, 2.0, 30.0, 100.0, 1000.0));
        assertEquals(1, result.getPositiveProfitYears());
    }

    @Test
    void validZeroDebtRemainsZeroAndProducesZeroDebtToEquity() {
        FundamentalAnalysis result = analyse(period(100.0, 10.0, 100.0, 50.0, 20.0, 0.0, 5.0, 2.0, 30.0, 100.0, 1000.0));
        assertEquals(0.0, result.getDebtToEquity());
    }

    @Test
    void unavailableAllGrowthInputsProduceNullGrowthScore() {
        FundamentalAnalysis result = analyse(period(null, 10.0, null, 50.0, 20.0, 10.0, 5.0, 2.0, 30.0, 100.0, 1000.0));
        assertNull(result.getGrowthScore());
    }

    private FundamentalPeriodData basePeriod(LocalDate date) {
        return new FundamentalPeriodData(date, 90.0, 9.0, 90.0, 20.0, 10.0, 12.0, 2.0, 1.0, 25.0, 90.0, 900.0, 100.0);
    }

    private FundamentalPeriodData period(Double profit, Double shares, Double sales, Double equity, Double reserves,
                                          Double debt, Double pbt, Double interest, Double cfo, Double price, Double marketCap) {
        return new FundamentalPeriodData(DATE, sales, profit, equity, reserves, debt, pbt, interest, 1.0, cfo, price, marketCap, shares);
    }
}
