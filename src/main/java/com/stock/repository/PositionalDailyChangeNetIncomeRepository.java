package com.stock.repository;


import com.stock.dto.PositionalDailyNetIncome;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PositionalDailyChangeNetIncomeRepository extends ReactiveCassandraRepository<PositionalDailyNetIncome, String> {
}
