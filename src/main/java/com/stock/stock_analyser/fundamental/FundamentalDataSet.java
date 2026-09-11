package com.stock.stock_analyser.fundamental;

import java.util.List;

/** Parsed fundamental dataset. Period data and the current market snapshot are separate contracts. */
public record FundamentalDataSet(
        String symbol,
        List<FundamentalPeriodData> annualPeriods,
        FundamentalMarketSnapshot marketSnapshot) {

    public FundamentalDataSet(String symbol, List<FundamentalPeriodData> annualPeriods) {
        this(symbol, annualPeriods, new FundamentalMarketSnapshot(null, null));
    }
}
