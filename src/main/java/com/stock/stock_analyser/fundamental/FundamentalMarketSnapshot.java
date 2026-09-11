package com.stock.stock_analyser.fundamental;

/** Current market values used only for valuation metrics such as P/E and P/B. */
public record FundamentalMarketSnapshot(Double currentPrice, Double currentMarketCap) {
}
