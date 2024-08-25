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
@Table("stocks_daily_change")
public class StocksDailyChange {

    @PrimaryKey
    private String key;

    @Column("stocks_daily_change_info_details")
    Map<String, StocksDailyChangeInfo> stocksDailyChangeInfoDetails;

    @Column("created_date")
    private LocalDateTime createdDate;
}
