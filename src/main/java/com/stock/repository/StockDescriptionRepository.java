package com.stock.repository;

import com.stock.dto.StockDescriptionDetails;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Repository
public interface StockDescriptionRepository extends ReactiveCassandraRepository<StockDescriptionDetails, String> {
}
