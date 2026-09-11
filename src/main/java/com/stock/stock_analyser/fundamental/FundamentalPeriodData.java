package com.stock.stock_analyser.fundamental;

import java.time.LocalDate;

/** Normalized annual fundamental data consumed by the analysis layer. */
public record FundamentalPeriodData(
        LocalDate date,
        Double sales,
        Double profit,
        Double equity,
        Double reserves,
        Double borrowings,
        Double pbt,
        Double interest,
        Double otherIncome,
        Double cfo,
        Double price,
        Double marketCap,
        Double shares) {
}
