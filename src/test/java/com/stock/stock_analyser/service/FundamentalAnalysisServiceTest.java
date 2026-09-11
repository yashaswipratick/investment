package com.stock.stock_analyser.service;

import com.stock.stock_analyser.dto.FundamentalAnalysis;
import com.stock.stock_analyser.fundamental.FundamentalDataSet;
import com.stock.stock_analyser.fundamental.FundamentalMarketSnapshot;
import com.stock.stock_analyser.fundamental.FundamentalPeriodData;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FundamentalAnalysisServiceTest {

    private final FundamentalAnalysisService service = new FundamentalAnalysisService("/tmp", null);

    private FundamentalPeriodData period(String date, double sales, double profit, double equity, double reserves,
                                         double debt, double operatingProfit, double pbt, double interest,
                                         double otherIncome, double cfo, double shares) {
        return new FundamentalPeriodData(LocalDate.parse(date), sales, profit, equity, reserves, debt,
                operatingProfit, pbt, interest, otherIncome, cfo, shares);
    }

    @Test void cagrUsesActualDates() {
        double expectedYears = service.elapsedYears(LocalDate.of(2022, 3, 31), LocalDate.of(2025, 3, 31));
        double expected = (Math.pow(121.0 / 100.0, 1.0 / expectedYears) - 1) * 100;
        assertEquals(expected, service.calculateCagr(121.0, 100.0,
                LocalDate.of(2025, 3, 31), LocalDate.of(2022, 3, 31)), 0.000001);
    }

    @Test void cagrUsesIrregularElapsedTime() {
        double expectedYears = 730.0 / 365.2425;
        double expected = (Math.pow(121.0 / 100.0, 1.0 / expectedYears) - 1) * 100;
        assertEquals(expected, service.calculateCagr(121.0, 100.0,
                LocalDate.of(2022, 12, 31), LocalDate.of(2020, 12, 31)), 0.000001);
    }

    @Test void historicalPeriodRequiresAtLeastTargetHistory() {
        var periods = List.of(period("2024-03-31", 100, 10, 20, 30, 5, 12, 12, 2, 1, 10, 2),
                period("2025-03-31", 110, 11, 21, 31, 5, 13, 13, 2, 1, 11, 2));
        assertNull(service.findClosestHistoricalPeriod(periods, LocalDate.of(2025, 3, 31), 3));
    }

    @Test void historicalPeriodSelectsClosestPeriodToTargetDateEitherSide() {
        var periods = List.of(period("2021-03-31", 80, 8, 18, 28, 5, 10, 10, 2, 1, 8, 2),
                period("2022-02-28", 90, 9, 19, 29, 5, 11, 11, 2, 1, 9, 2),
                period("2022-06-30", 95, 9.5, 19, 29, 5, 11, 11, 2, 1, 9, 2),
                period("2025-03-31", 120, 12, 22, 32, 5, 14, 14, 2, 1, 12, 2));
        assertEquals(LocalDate.of(2022, 2, 28), service.findClosestHistoricalPeriod(periods,
                LocalDate.of(2025, 3, 31), 3).date());
    }

    @Test void historicalPeriodCanSelectAfterTargetWhenItIsCloser() {
        var periods = List.of(
                period("2021-03-31", 80, 8, 18, 28, 5, 10, 10, 2, 1, 8, 2),
                period("2022-02-28", 90, 9, 19, 29, 5, 11, 11, 2, 1, 9, 2),
                period("2022-04-15", 95, 9.5, 19, 29, 5, 11, 11, 2, 1, 9, 2),
                period("2025-03-31", 120, 12, 22, 32, 5, 14, 14, 2, 1, 12, 2));
        assertEquals(LocalDate.of(2022, 4, 15), service.findClosestHistoricalPeriod(periods,
                LocalDate.of(2025, 3, 31), 3).date());
    }

    @Test void closestHistoricalPeriodOutsideToleranceIsUnavailable() {
        var periods = List.of(
                period("2020-03-31", 80, 8, 18, 28, 5, 10, 10, 2, 1, 8, 2),
                period("2025-03-31", 120, 12, 22, 32, 5, 14, 14, 2, 1, 12, 2));
        assertNull(service.findClosestHistoricalPeriod(periods, LocalDate.of(2025, 3, 31), 3));
    }

    @Test void cagrUsesActualSelectedFinancialPeriodDate() {
        var start = LocalDate.of(2022, 2, 28);
        var end = LocalDate.of(2025, 3, 31);
        var selected = service.findClosestHistoricalPeriod(List.of(
                period("2022-02-28", 100, 10, 20, 30, 5, 12, 12, 2, 1, 10, 2),
                period("2022-06-30", 110, 11, 21, 31, 5, 13, 13, 2, 1, 11, 2),
                period("2025-03-31", 150, 15, 25, 35, 5, 17, 17, 2, 1, 15, 2)), end, 3);
        assertEquals(start, selected.date());
        assertEquals(service.calculateCagr(150.0, 100.0, end, start),
                service.calculateCagr(150.0, selected.sales(), end, selected.date()));
    }

    @Test void unsortedPeriodsAreChronological() {
        var latest = period("2025-03-31", 120, 12, 22, 32, 5, 14, 14, 2, 1, 12, 2);
        var old = period("2023-03-31", 100, 10, 20, 30, 5, 12, 12, 2, 1, 10, 2);
        var result = service.validatedPeriods(List.of(latest, old));
        assertEquals(List.of(old, latest), result);
    }

    @Test void duplicateDatesAreIgnoredDeterministically() {
        var a = period("2024-03-31", 100, 10, 20, 30, 5, 12, 12, 2, 1, 10, 2);
        var b = period("2024-03-31", 101, 11, 21, 31, 5, 13, 13, 2, 1, 11, 2);
        assertEquals(1, service.validatedPeriods(List.of(a, b)).size());
    }

    @Test void epsIsCalculatedFromProfitAndAdjustedShares() {
        var p = period("2025-03-31", 100, -20, 20, 30, 5, 12, 12, 2, 1, 10, 4);
        assertEquals(-5.0, service.calculateEps(p));
    }

    @Test void epsMissingProfitOrSharesIsNull() {
        var p = new FundamentalPeriodData(LocalDate.of(2025, 3, 31), 100.0, null, 20.0, 30.0, 5.0,
                12.0, 12.0, 2.0, 1.0, 10.0, 4.0);
        assertNull(service.calculateEps(p));
        assertNull(service.calculateEps(new FundamentalPeriodData(LocalDate.of(2025, 3, 31), 100.0, 20.0,
                20.0, 30.0, 5.0, 12.0, 12.0, 2.0, 1.0, 10.0, 0.0)));
    }

    @Test void epsGrowthRequiresPositivePreviousEps() {
        assertNull(service.calculateEpsGrowth(10.0, 0.0));
        assertNull(service.calculateEpsGrowth(10.0, -2.0));
        assertEquals(100.0, service.calculateEpsGrowth(10.0, 5.0));
    }

    @Test void epsCagrRequiresPositiveEndpoints() {
        assertNull(service.calculateCagr(10.0, -5.0, LocalDate.of(2025, 3, 31), LocalDate.of(2022, 3, 31)));
        assertEquals(25.99, service.calculateCagr(16.0, 8.0,
                LocalDate.of(2025, 3, 31), LocalDate.of(2022, 3, 31)), 0.02);
    }

    @Test void roeUsesAverageNetWorthAndAllowsNegativeProfit() {
        assertEquals(-40.0, service.calculateRoe(-20.0, 50.0));
        assertNull(service.calculateRoe(20.0, 0.0));
    }

    @Test void roeMissingInputsIsNull() {
        assertNull(service.calculateRoe(null, 50.0));
        assertNull(service.calculateRoe(20.0, null));
    }

    @Test void interestCoverageUsesOperatingProfitAndNeverInfinity() {
        var p = period("2025-03-31", 100, 10, 20, 30, 5, 30, 28, 5, 3, 10, 2);
        assertEquals(6.0, service.calculateInterestCoverage(p));
        assertNull(service.calculateInterestCoverage(period("2025-03-31", 100, 10, 20, 30, 5, 30, 28, 0, 3, 10, 2)));
        assertNull(service.calculateInterestCoverage(new FundamentalPeriodData(LocalDate.of(2025, 3, 31), 100.0,
                10.0, 20.0, 30.0, 5.0, null, 28.0, 5.0, 3.0, 10.0, 2.0)));
    }

    @Test void marketSnapshotIsSeparateFromHistoricalPeriod() {
        var p = period("2025-03-31", 100, 10, 20, 30, 5, 30, 28, 5, 3, 10, 2);
        assertFalse(service.validatedPeriods(List.of(p)).get(0).toString().contains("price"));
        assertEquals(150.0, new FundamentalMarketSnapshot(150.0, 7500.0).currentPrice());
    }

    @Test void peUsesCurrentPriceAndPositiveLatestEps() {
        assertEquals(15.0, service.calculatePe(150.0, 10.0));
        assertNull(service.calculatePe(150.0, -10.0));
    }

    @Test void pbUsesCurrentMarketCapAndLatestNetWorth() {
        assertEquals(5.0, service.calculatePb(500.0, 100.0));
        assertNull(service.calculatePb(500.0, 0.0));
    }

    @Test void fullAnalysisUsesCurrentMarketSnapshotForValuation() {
        var periods = List.of(
                period("2022-03-31", 80, 8, 20, 30, 5, 12, 10, 2, 1, 8, 2),
                period("2023-03-31", 90, 9, 21, 31, 5, 13, 11, 2, 1, 9, 2),
                period("2024-03-31", 100, 10, 22, 32, 5, 14, 12, 2, 1, 10, 2),
                period("2025-03-31", 120, 12, 24, 36, 5, 16, 14, 2, 1, 12, 2));
        FundamentalAnalysis result = service.analyse(new FundamentalDataSet("TEST", periods,
                new FundamentalMarketSnapshot(180.0, 1200.0)));
        assertNotNull(result.getPeRatio());
        assertNotNull(result.getPbRatio());
        assertNotNull(result.getRevenueCagr3Y());
        assertEquals(LocalDate.of(2025, 3, 31), result.getLatestPeriod());
        assertTrue(result.getDataNote().contains("2022-03-31"));
    }

    @Test void missingHistoryDoesNotInventThreeOrFiveYearCagr() {
        var periods = List.of(
                period("2024-03-31", 100, 10, 20, 30, 5, 12, 12, 2, 1, 10, 2),
                period("2025-03-31", 110, 11, 21, 31, 5, 13, 13, 2, 1, 11, 2));
        FundamentalAnalysis result = service.analyse(new FundamentalDataSet("TEST", periods,
                new FundamentalMarketSnapshot(100.0, 500.0)));
        assertNull(result.getRevenueCagr3Y());
        assertNull(result.getRevenueCagr5Y());
    }

    @Test void elapsedYearsRejectsInvalidDates() {
        assertEquals(0.0, service.elapsedYears(LocalDate.of(2025, 3, 31), LocalDate.of(2025, 3, 31)));
        assertEquals(0.0, service.elapsedYears(LocalDate.of(2025, 3, 31), LocalDate.of(2024, 3, 31)));
    }

    @Test void cagrRejectsMissingOrZeroStart() {
        assertNull(service.calculateCagr(100.0, null, LocalDate.of(2025, 3, 31), LocalDate.of(2022, 3, 31)));
        assertNull(service.calculateCagr(100.0, 0.0, LocalDate.of(2025, 3, 31), LocalDate.of(2022, 3, 31)));
    }

    @Test void negativeEquityRemainsMeaningfulForRoe() {
        assertEquals(-20.0, service.calculateRoe(20.0, -100.0));
    }

    @Test void negativeOperatingProfitProducesNegativeCoverage() {
        var p = period("2025-03-31", 100, 10, 20, 30, 5, -10, -8, 2, 1, 10, 2);
        assertEquals(-5.0, service.calculateInterestCoverage(p));
    }

    @Test void missingCurrentPriceMakesPeUnavailable() {
        assertNull(service.calculatePe(null, 10.0));
    }

    @Test void missingCurrentMarketCapMakesPbUnavailable() {
        assertNull(service.calculatePb(null, 100.0));
    }

    @Test void roceUsesOperatingProfitAndCapitalEmployed() {
        var p = period("2025-03-31", 100, 10, 40, 20, 40, 20, 15, 5, 1, 10, 2);
        assertEquals(20.0, service.calculateRoce(p));
    }

    @Test void roceIsUnavailableForMissingOrInvalidCapitalBase() {
        var missing = new FundamentalPeriodData(LocalDate.of(2025, 3, 31), 100.0, 10.0, null, 20.0, 40.0,
                20.0, 15.0, 5.0, 1.0, 10.0, 2.0);
        assertNull(service.calculateRoce(missing));
        assertNull(service.calculateRoce(period("2025-03-31", 100, 10, -40, -20, 40, 20, 15, 5, 1, 10, 2)));
        assertNull(service.calculateRoce(period("2025-03-31", 100, 10, 0, 0, 0, 20, 15, 5, 1, 10, 2)));
    }

    @Test void growthRequiresPositivePreviousValue() {
        assertEquals(20.0, service.calculateGrowth(120.0, 100.0), 0.000001);
        assertNull(service.calculateGrowth(120.0, 0.0));
        assertNull(service.calculateGrowth(120.0, -100.0));
    }

    @Test void marginTrendClassifiesImprovingStableAndDeclining() {
        assertEquals("IMPROVING", service.trend(10.0, 11.0));
        assertEquals("STABLE", service.trend(10.0, 10.1));
        assertEquals("DECLINING", service.trend(11.0, 10.0));
        assertEquals("UNAVAILABLE", service.trend(null, 10.0));
    }

    @Test void historicalTrendUsesMultipleAnnualPeriods() {
        var periods = List.of(
                period("2022-03-31", 80, 8, 20, 30, 5, 12, 10, 2, 1, 8, 2),
                period("2023-03-31", 90, 9, 21, 31, 5, 13, 11, 2, 1, 9, 2),
                period("2024-03-31", 100, 10, 22, 32, 5, 14, 12, 2, 1, 10, 2),
                period("2025-03-31", 90, 11, 24, 36, 5, 13, 12, 2, 1, 11, 2));
        assertEquals("IMPROVING", service.historicalTrend(periods, FundamentalPeriodData::profit));
        assertEquals("STABLE", service.historicalTrend(periods, p -> 100.0));
        assertEquals("IMPROVING", service.historicalTrend(periods, FundamentalPeriodData::sales));
        assertEquals("UNAVAILABLE", service.historicalTrend(List.of(periods.get(0)), FundamentalPeriodData::sales));
    }

    @Test void revenueAndProfitTrendUsePositiveAndNegativeYoyGrowth() {
        assertEquals("IMPROVING", service.trendByValue(100.0, 110.0));
        assertEquals("DECLINING", service.trendByValue(110.0, 100.0));
        assertEquals("STABLE", service.trendByValue(100.0, 100.0));
        assertEquals("UNAVAILABLE", service.trendByValue(null, 100.0));
    }

    @Test void fullAnalysisExposesPoint4TrendsAndMetrics() {
        var periods = List.of(
                period("2022-03-31", 80, 8, 20, 30, 5, 12, 10, 2, 1, 8, 2),
                period("2023-03-31", 90, 9, 21, 31, 5, 13, 11, 2, 1, 9, 2),
                period("2024-03-31", 100, 10, 22, 32, 5, 14, 12, 2, 1, 10, 2),
                period("2025-03-31", 120, 15, 24, 36, 5, 20, 17, 2, 1, 15, 2));
        FundamentalAnalysis result = service.analyse(new FundamentalDataSet("TEST", periods,
                new FundamentalMarketSnapshot(180.0, 1200.0)));
        assertEquals(16.67, result.getOperatingMargin(), 0.01);
        assertNotNull(result.getRoce());
        assertNull(result.getPromoterHolding());
        assertNull(result.getPromoterPledge());
        assertEquals("IMPROVING", result.getNetMarginTrend());
        assertEquals("IMPROVING", result.getOperatingMarginTrend());
        assertEquals("IMPROVING", result.getRevenueTrend());
        assertEquals(120.0, result.getLatestRevenue());
        assertEquals(100.0, result.getPreviousRevenue());
        assertEquals(20.0, result.getRevenueGrowthYoY());
        assertEquals("IMPROVING", result.getProfitTrend());
        assertEquals(15.0, result.getLatestProfit());
        assertEquals(10.0, result.getPreviousProfit());
        assertEquals(50.0, result.getProfitGrowthYoY());
    }

    @Test void fullAnalysisPreservesNegativeProfitAndUnavailableProfitGrowth() {
        var periods = List.of(
                period("2024-03-31", 100, -10, 20, 30, 5, 12, -8, 2, 1, 10, 2),
                period("2025-03-31", 110, -5, 21, 31, 5, 13, -3, 2, 1, 11, 2));
        FundamentalAnalysis result = service.analyse(new FundamentalDataSet("TEST", periods,
                new FundamentalMarketSnapshot(100.0, 500.0)));
        assertEquals(-5.0, result.getLatestProfit());
        assertEquals(-10.0, result.getPreviousProfit());
        assertNull(result.getProfitGrowthYoY());
        assertEquals("IMPROVING", result.getProfitTrend());
    }

    @Test void fullAnalysisReturnsUnavailableMarginTrendWhenPriorInputsAreMissing() {
        var periods = List.of(
                new FundamentalPeriodData(LocalDate.of(2024, 3, 31), null, 10.0, 20.0, 30.0, 5.0,
                        12.0, 12.0, 2.0, 1.0, 10.0, 2.0),
                period("2025-03-31", 110, 12, 21, 31, 5, 13, 13, 2, 1, 11, 2));
        FundamentalAnalysis result = service.analyse(new FundamentalDataSet("TEST", periods,
                new FundamentalMarketSnapshot(100.0, 500.0)));
        assertEquals("UNAVAILABLE", result.getNetMarginTrend());
        assertEquals("UNAVAILABLE", result.getRevenueTrend());
    }

    @Test void nullPeriodsAreIgnoredDuringValidation() {
        var p = period("2025-03-31", 100, 10, 20, 30, 5, 12, 12, 2, 1, 10, 2);
        assertEquals(List.of(p), service.validatedPeriods(Arrays.asList(null, p)));
    }
}
