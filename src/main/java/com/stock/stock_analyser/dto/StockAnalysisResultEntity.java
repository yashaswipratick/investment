package com.stock.stock_analyser.dto;

import com.stock.stock_analyser.dto.key.StockAnalysisResultKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("stock_analysis_result")
public class StockAnalysisResultEntity {

    @PrimaryKey
    private StockAnalysisResultKey key;

    @Column("total_data_points")
    private Integer totalDataPoints;

    @Column("data_from")
    private LocalDate dataFrom;

    @Column("data_to")
    private LocalDate dataTo;

    @Column("required_from")
    private LocalDate requiredFrom;

    @Column("window_status")
    private String windowStatus;

    @Column("window_message")
    private String windowMessage;

    @Column("data_note")
    private String dataNote;

    @Column("technical_json")
    private String technicalJson;

    @Column("recommendation_json")
    private String recommendationJson;

    @Column("projections_json")
    private String projectionsJson;

    @Column("entry_timing_json")
    private String entryTimingJson;

    @Column("stop_loss_strategy_json")
    private String stopLossStrategyJson;

    @Column("created_at")
    private Instant createdAt;
}

