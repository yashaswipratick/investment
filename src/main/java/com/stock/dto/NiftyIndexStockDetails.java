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
@Table("nifty_fifty_index_stock")
public class NiftyIndexStockDetails {

    @PrimaryKey
    private LocalDate date;

    @Column("index_stock_details")
    private Map<String, IndexStockDetails> indexStockDetailsMap;

    @Column("created_date")
    private LocalDateTime createdDate;
}
