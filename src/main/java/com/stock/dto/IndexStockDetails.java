package com.stock.dto;


import lombok.*;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString
@UserDefinedType("index_stock_details")
public class IndexStockDetails {

    @Column("company_name")
    private String companyName;
    private String industry;
    @Column("last_price")
    private String lastPrice;
    private String change;
    @Column("percentage_change")
    private String percentageChange;
    @Column("market_cap")
    private String marketCap;
    private String link;
}
