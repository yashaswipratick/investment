package com.stock.dto;

import lombok.*;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
@UserDefinedType("stocks_daily_change_info")
public class StocksDailyChangeInfo {

    @Column("instrument")
    private String instrument;

    @Column("last_traded_price")
    private Double lastTradedPrice;

    @Column("created_date")
    private LocalDateTime createdDate;
}
