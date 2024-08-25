package com.stock.repository;


import com.stock.dto.StocksDailyChange;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StocksDailyChangeRepository extends ReactiveCassandraRepository<StocksDailyChange, String> {
}
