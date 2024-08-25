package com.stock.dto;

import lombok.*;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString
@UserDefinedType("stock_overview")
public class StockOverview {

    @Column("open")
    private String open;

    @Column("previous_close")
    private String previousClose;

    @Column("volume")
    private String volume;

    @Column("value_in_lacs")
    private String valueInLacs;

    @Column("vwap")
    private String vwap;

    @Column("beta")
    private String beta;

    @Column("market_cap_in_cr")
    private String marketCapInCR;

    @Column("high")
    private String high;

    @Column("low")
    private String low;

    @Column("uc_limit")
    private String ucLimit;

    @Column("lc_limit")
    private String lcLimit;

    @Column("fifty_two_weeks_high")
    private String fiftyTwoWeeksHigh;

    @Column("fifty_two_weeks_low")
    private String fiftyTwoWeeksLow;

    @Column("face_value")
    private Integer faceValue;

    @Column("all_time_high")
    private String allTimeHigh;

    @Column("all_time_low")
    private String allTimeLow;

    @Column("twenty_day_avg_vol")
    private String twentyDayAvgVol;

    @Column("twenty_day_avg_delivery_percentage")
    private String twentyDayAvgDeliveryPercentage;

    @Column("book_value_per_share")
    private String bookValuePerShare;

    @Column("dividend_yield")
    private String dividendYield;

    @Column("ttm_eps")
    private String ttmEps;

    @Column("ttm_pe")
    private String ttmPe;

    @Column("pb_ratio")
    private String pbRatio;

    @Column("sector_pe")
    private String sectorPE;
}
