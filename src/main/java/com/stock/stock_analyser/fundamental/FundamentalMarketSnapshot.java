package com.stock.stock_analyser.fundamental;

/** Current market/ownership snapshot. Promoter fields remain null when the configured workbook does not expose them. */
public record FundamentalMarketSnapshot(Double currentPrice, Double currentMarketCap,
                                        Double promoterHolding, Double promoterPledge) {
    public FundamentalMarketSnapshot(Double currentPrice, Double currentMarketCap) {
        this(currentPrice, currentMarketCap, null, null);
    }
}
