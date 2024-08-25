package com.stock.repository;

import com.stock.dto.NiftyIndexStockDetails;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NiftyIndexStockDetailsRepository extends ReactiveCassandraRepository<NiftyIndexStockDetails, String> {
}
