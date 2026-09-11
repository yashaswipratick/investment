package com.stock.stock_analyser.fundamental;

import java.util.List;

/** Parsed fundamental dataset for a stock. Parsing concerns stop at this boundary. */
public record FundamentalDataSet(String symbol, List<FundamentalPeriodData> annualPeriods) {
}
