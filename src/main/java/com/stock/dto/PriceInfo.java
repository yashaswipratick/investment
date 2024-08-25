package com.stock.dto;

import lombok.*;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
@UserDefinedType("price_info")
public class PriceInfo {

    private String lastPrice;
    private String change;
    private String pChange;
    private String previousClose;
    private String open;
    private String close;
    private String vwap;
    private String lowerCP;
    private String upperCP;
    private String pPriceBand;
    private String basePrice;
    private IntraDayHighLow intraDayHighLow;
    private WeekHighLow weekHighLow;
    private String iNavValue;
    private boolean checkINAV;

}
