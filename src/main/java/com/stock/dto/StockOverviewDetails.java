package com.stock.dto;

import lombok.*;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString
@Table("stock_overview_details.cql")
public class StockOverviewDetails {

    @PrimaryKey
    private LocalDate date;

    @Column("company_stock_overview")
    private Map<String, StockOverview> stockOverviewMap;

    @Column("created_date")
    private LocalDateTime createdDate;
}
