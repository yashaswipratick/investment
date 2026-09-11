package com.stock.stock_analyser.fundamental;

import java.time.LocalDate;

/** Period-specific annual financial data. Current market values do not belong here. */
public record FundamentalPeriodData(
        LocalDate date,
        Double sales,
        Double profit,
        Double equity,
        Double reserves,
        Double borrowings,
        Double operatingProfit,
        Double pbt,
        Double interest,
        Double otherIncome,
        Double cfo,
        Double shares) {
}
