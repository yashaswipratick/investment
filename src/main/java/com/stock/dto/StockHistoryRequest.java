package com.stock.dto;

import com.stock.dto.key.StockHistoryKey;
import lombok.*;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
public class StockHistoryRequest {

    private String stockSymbol;

    private String series;

    private String from;

    private String to;

    private Integer numOfDays;
}
