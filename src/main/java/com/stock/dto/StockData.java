package com.stock.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
@UserDefinedType("stock_data")
public class StockData {

    private Integer priority;
    private String symbol;
    private String identifier;
    private String series;
    private Double open;
    private Double dayHigh;
    private Double dayLow;
    private Double lastPrice;
    private Double previousClose;
    private Double change;
    private Double pChange;
    private Long totalTradedVolume;
    private Double totalTradedValue;
    private String lastUpdateTime;
    private Double ffmc;
    private Double yearHigh;
    private Double yearLow;
    private Double nearWKH;
    private Double nearWKL;
    private Double perChange365d;
    private String date365dAgo;
    private String chart365dPath;
    private String date30dAgo;
    private Double perChange30d;
    private String chart30dPath;
    private String chartTodayPath;
    private Meta meta;
}
