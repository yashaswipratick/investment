package com.stock.dto.key;

import lombok.*;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
@PrimaryKeyClass
public class PositionalAggregatedDailyCalculatorKey {

    @PrimaryKeyColumn(type = PrimaryKeyType.PARTITIONED, ordinal = 0, name = "key")
    private String key;

    @PrimaryKeyColumn(type = PrimaryKeyType.PARTITIONED, ordinal = 1, name = "positioned_date")
    private LocalDate positionedDate;

    @PrimaryKeyColumn(type = PrimaryKeyType.PARTITIONED, ordinal = 2, name = "basket_id")
    private String basketId;
}
