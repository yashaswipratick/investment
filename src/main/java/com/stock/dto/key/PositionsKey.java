package com.stock.dto.key;

import lombok.*;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
@PrimaryKeyClass
public class PositionsKey {

    @PrimaryKeyColumn(type = PrimaryKeyType.PARTITIONED, ordinal = 0, name = "key")
    private String key; //date on which stock was purchased

    @PrimaryKeyColumn(type = PrimaryKeyType.PARTITIONED, ordinal = 1, name = "basket_id")
    private String basketId;
}
