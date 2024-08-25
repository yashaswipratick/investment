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
@UserDefinedType("positions_stock_info")
public class PositionsStockInfo {

    @Column("instrument")
    private String instrument;

    @Column("quantity")
    private Integer quantity;

    @Column("average_price_per_stock")
    private Double averagePricePerStock;

    @Column("last_traded_price")
    private Double lastTradedPrice;

    @Column("pnl_per_stock")
    private Double pnlPerStock;

    @Column("change_per_stock")
    private Double changePerStock;

    @Column("total_invested_amount")
    private Double totalInvestedAmount;

    @Column("total_ltp_amount")
    private Double totalLtpAmount;

    @Column("total_pnl")
    private Double totalPnl;

    @Column("total_pnl_percentage")
    private Double totalPnlPercentage;

    @Column("created_date")
    private LocalDateTime createdDate;
}
