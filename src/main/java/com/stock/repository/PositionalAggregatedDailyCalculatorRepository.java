package com.stock.repository;


import com.stock.dto.PositionalAggregatedDailyCalculator;
import com.stock.dto.key.PositionalAggregatedDailyCalculatorKey;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PositionalAggregatedDailyCalculatorRepository
        extends ReactiveCassandraRepository<PositionalAggregatedDailyCalculator, PositionalAggregatedDailyCalculatorKey> {
}
