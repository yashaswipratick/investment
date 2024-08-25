package com.stock.dto;

import lombok.*;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
@Table("positional_daily_net_income")
public class PositionalDailyNetIncome {

    @PrimaryKey
    private String key;

    @Column("positional_daily_net_income_info_details")
    Map<String, PositionalDailyNetIncomeInfo> positionalDailyNetIncomeInfoDetails;

    @Column("created_date")
    private LocalDateTime createdDate;
}
