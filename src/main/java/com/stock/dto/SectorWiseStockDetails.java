package com.stock.dto;

import com.stock.dto.key.SectorWiseStockKey;
import lombok.*;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
@Table("sector_wise_stock_details")
public class SectorWiseStockDetails {

    @PrimaryKey
    private SectorWiseStockKey key;

    @Column("stocks")
    private List<String> stocks;
}
