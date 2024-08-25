package com.stock.dto;

import com.stock.dto.key.PositionalAggregatedDailyCalculatorKey;
import lombok.*;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
@Table("positional_aggregated_daily_income")
public class PositionalAggregatedDailyCalculator {

    @PrimaryKey
    private PositionalAggregatedDailyCalculatorKey key;

    @Column("total_pnl")
    private Double totalPnl;

    @Column("total_pnl_percentage")
    private Double totalPnlPercentage;

    @Column("agg_invested_amount")
    private Double aggregatedInvestmentAmount;

    @Column("agg_daily_ltp_amount")
    private Double aggregatedDailyLtpAmount;

    @Column("created_date")
    private LocalDateTime createdDate;
}
