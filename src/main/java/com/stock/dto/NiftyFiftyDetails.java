package com.stock.dto;

import lombok.*;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.util.List;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
@Table("nifty_fifty_details")
public class NiftyFiftyDetails {

    @PrimaryKey
    private String name;

    @Column("advance")
    private Advance advance;

    @Column("nifty_fifty_timestamp")
    private String timestamp;

    @Column("stock_data")
    private Set<StockData> stockData;
}
