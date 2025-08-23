package com.stock.dto;

import lombok.*;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
@UserDefinedType("stock_history_details")
public class StockHistoryDetails {

    @Column("series")
    private String series;

    @Column("stock_name")
    private String stockName;

    @Column("open")
    private Double open;

    @Column("high")
    private Double high;

    @Column("low")
    private Double low;

    @Column("prevClose")
    private Double pevClose;

    @Column("ltp")
    private Double ltp;

    @Column("close")
    private Double close;

    @Column("vwap")
    private Double vwap;

    @Column("fifty_two_week_high")
    private Double fiftyTwoWeekHigh;

    @Column("fifty_two_week_low")
    private Double fiftyTwoWeekLow;

    @Column("volume")
    private String volume;

    @Column("value")
    private String value;

    @Column("total_trades")
    private String totalTrades;

    @Column("isin")
    private String isin;

    @Column("history_date")
    private LocalDate historyDate;
}
