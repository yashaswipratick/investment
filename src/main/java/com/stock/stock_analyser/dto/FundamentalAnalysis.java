package com.stock.stock_analyser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundamentalAnalysis {
    private String status;
    private String confidence;
    private LocalDate latestPeriod;
    private int periodsAvailable;
    private Double revenueCagr3Y;
    private Double revenueCagr5Y;
    private Double eps;
    private Double epsGrowthYoY;
    private Double epsCagr3Y;
    private Double netMargin;
    private Double operatingMargin;
    private Double roce;
    private Double promoterHolding;
    private Double promoterPledge;
    private String netMarginTrend;
    private String operatingMarginTrend;
    private String revenueTrend;
    private Double latestRevenue;
    private Double previousRevenue;
    private Double revenueGrowthYoY;
    private String profitTrend;
    private Double latestProfit;
    private Double previousProfit;
    private Double profitGrowthYoY;
    private Double roe;
    private Double debtToEquity;
    private Double interestCoverage;
    private Double cfoToPat;
    private Integer positiveProfitYears;
    private Integer positiveRevenueGrowthYears;
    private Double peRatio;
    private Double pbRatio;
    private Double growthScore;
    private Double profitabilityScore;
    private Double financialHealthScore;
    private Double cashFlowScore;
    private Double consistencyScore;
    private Double valuationScore;
    private Double overallScore;
    private String valuationLabel;
    private String summary;
    private String dataNote;
}
