package com.stock.repository;

import com.stock.dto.StockHistory;
import com.stock.dto.key.StockHistoryKey;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockHistoryDataRepository extends ReactiveCassandraRepository<StockHistory, StockHistoryKey> {
}
