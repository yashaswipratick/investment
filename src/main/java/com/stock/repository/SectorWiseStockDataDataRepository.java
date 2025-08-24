package com.stock.repository;

import com.stock.dto.SectorWiseStockDetails;
import com.stock.dto.key.SectorWiseStockKey;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SectorWiseStockDataDataRepository extends ReactiveCassandraRepository<SectorWiseStockDetails, SectorWiseStockKey> {
}
