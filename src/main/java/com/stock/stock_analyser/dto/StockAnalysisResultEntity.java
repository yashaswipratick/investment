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

    @Column("analysis_execution_date")
    private LocalDate analysisExecutionDate;

    @Column("market_data_latest_date")
    private LocalDate marketDataLatestDate;

    @Column("fundamental_latest_period")
    private LocalDate fundamentalLatestPeriod;

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

    @Column("fundamental_json")
    private String fundamentalJson;

    @Column("technical_criteria_json")
    private String technicalCriteriaJson;

    @Column("fundamental_criteria_json")
    private String fundamentalCriteriaJson;

    @Column("marcus_decision")
    private String marcusDecision;

    @Column("decision_reason")
    private String decisionReason;

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

