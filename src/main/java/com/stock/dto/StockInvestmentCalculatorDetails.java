package com.stock.dto;

import lombok.*;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
@Table("stock_investment_calculator")
public class StockInvestmentCalculatorDetails {

    @PrimaryKey
    private String stockName;

    private String price;

    private Double investmentAmount;

    private Long numberOfStocks;

    private Double roundUpInvestmentAmount;

    private Double fiftyTwoWeeksLowPrice;

    private Double fiftyTwoWeeksHighPrice;

    private Double lowerBuyingLimit;

    private Double upperBuyingLimit;

    private Boolean shouldBuy;
}
