package com.stock.repository;

import com.stock.stock_analyser.dto.StockAnalysisResultEntity;
import com.stock.stock_analyser.dto.key.StockAnalysisResultKey;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface StockAnalysisResultRepository
        extends ReactiveCassandraRepository<StockAnalysisResultEntity, StockAnalysisResultKey> {

    /** Find all analysis results for a specific period label across all symbols */
    @Query("SELECT * FROM stock_analysis_result WHERE period_label = ?0 ALLOW FILTERING")
    Flux<StockAnalysisResultEntity> findAllByPeriodLabel(String periodLabel);
}

