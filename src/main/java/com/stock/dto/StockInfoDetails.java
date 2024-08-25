package com.stock.dto;

import lombok.*;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
@Table("stock_info_details")
public class StockInfoDetails {

    @PrimaryKey
    private String key;

    @Column("stock_info")
    private Map<String, StockInfoDTO> stockInfo;

    @Column("created_date")
    private LocalDateTime createdDate;
}
