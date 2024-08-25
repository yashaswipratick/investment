package com.stock.dto;

import lombok.*;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
@UserDefinedType("pre_open_market")
public class PreOpenMarket {

    private List<PreOpen> preopen;
    private Ato ato;
    private String IEP;
    private int totalTradedVolume;
    private String finalPrice;
    private int finalQuantity;
    private String lastUpdateTime;
    private int totalBuyQuantity;
    private int totalSellQuantity;
    private int atoBuyQty;
    private int atoSellQty;
    private String Change;
    private String perChange;
    private String prevClose;

}
