package com.stock.repository;

import com.stock.stock_analyser.dto.StockAnalysisResultEntity;
import com.stock.stock_analyser.dto.key.StockAnalysisResultKey;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockAnalysisResultRepository
        extends ReactiveCassandraRepository<StockAnalysisResultEntity, StockAnalysisResultKey> {
}

