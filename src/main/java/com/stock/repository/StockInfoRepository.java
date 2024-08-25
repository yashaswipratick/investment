package com.stock.repository;

import com.stock.dto.StockInfoDetails;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface StockInfoRepository extends ReactiveCassandraRepository<StockInfoDetails, String> {
}
