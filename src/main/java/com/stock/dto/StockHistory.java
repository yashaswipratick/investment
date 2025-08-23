package com.stock.dto;

import com.stock.dto.key.StockHistoryKey;
import lombok.*;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
@Table("stock_history")
public class StockHistory {

    @PrimaryKey
    private StockHistoryKey key;

    @Column("stock_history_details")
    private TreeMap<LocalDate, StockHistoryDetails> stockHistoryDetails;
}
