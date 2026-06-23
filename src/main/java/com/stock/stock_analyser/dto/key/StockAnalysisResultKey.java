package com.stock.stock_analyser.dto.key;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
@PrimaryKeyClass
public class StockAnalysisResultKey {

    @PrimaryKeyColumn(type = PrimaryKeyType.PARTITIONED, ordinal = 0, name = "symbol")
    private String symbol;

    /** Analysis period label: 6M | 1Y | 2Y | 3Y */
    @PrimaryKeyColumn(type = PrimaryKeyType.CLUSTERED, ordinal = 1, name = "period_label")
    private String periodLabel;

    @PrimaryKeyColumn(type = PrimaryKeyType.CLUSTERED, ordinal = 2, name = "analysis_date")
    private LocalDate analysisDate;
}

