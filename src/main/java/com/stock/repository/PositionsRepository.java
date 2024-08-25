package com.stock.repository;


import com.stock.dto.Positions;
import com.stock.dto.key.PositionsKey;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PositionsRepository extends ReactiveCassandraRepository<Positions, PositionsKey> {
}
