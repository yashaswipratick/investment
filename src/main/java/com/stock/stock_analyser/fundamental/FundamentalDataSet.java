package com.stock.stock_analyser.fundamental;

import java.util.List;

/** Parsed fundamental dataset with separate market snapshot and parser quality notes. */
public record FundamentalDataSet(
        String symbol,
        List<FundamentalPeriodData> annualPeriods,
        FundamentalMarketSnapshot marketSnapshot,
        List<String> dataQualityNotes) {

    public FundamentalDataSet(String symbol, List<FundamentalPeriodData> annualPeriods) {
        this(symbol, annualPeriods, new FundamentalMarketSnapshot(null, null), List.of());
    }

    public FundamentalDataSet(String symbol, List<FundamentalPeriodData> annualPeriods,
                              FundamentalMarketSnapshot marketSnapshot) {
        this(symbol, annualPeriods, marketSnapshot, List.of());
    }
}
