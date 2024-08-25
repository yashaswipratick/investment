package com.stock.dto;

import com.stock.dto.key.PositionsKey;
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
@Table("positions")
public class Positions {

    @PrimaryKey
    private PositionsKey key;

    @Column("positions_stock_info_details")
    Map<String, PositionsStockInfo> positionsStockInfoDetails;

    @Column("is_active_position")
    private String isActivePosition;

    @Column("created_date")
    private LocalDateTime createdDate;
}
