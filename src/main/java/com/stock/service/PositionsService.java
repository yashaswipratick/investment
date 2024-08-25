package com.stock.service;

import com.stock.dto.Positions;
import com.stock.dto.PositionsStockInfo;
import com.stock.dto.key.PositionsKey;
import com.stock.repository.PositionsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PositionsService {

    @Autowired
    private PositionsRepository repository;

    public Mono<Positions> save(Positions entity) {
        if (entity == null) {
            log.error("positions details are null. skipping save...");
            return Mono.empty();
        }
        return repository.save(entity);
    }

    public Mono<Positions> update(Positions entity) {
        if (entity == null) {
            log.error("positions details are null. skipping save...");
            return Mono.empty();
        }

        return get(PositionsKey.builder()
                .key(entity.getKey().getKey())
                .basketId(entity.getKey().getBasketId())
                .build())
                .switchIfEmpty(Mono.empty())
                .flatMap(positionDetails -> {
                    if (entity.getCreatedDate().isAfter(positionDetails.getCreatedDate())) {
                        return repository.save(entity);
                    }
                    log.info("Skipped update as requested is before the db date.");
                    return Mono.empty();
                });
    }

    public Mono<Positions> get(PositionsKey key) {
        if (key == null) {
            log.error("key can't be null. skipping get for positions");
            return Mono.empty();
        }
        return repository.findById(key);
    }

    public Mono<List<Positions>> getAllActivePositions() {
        return repository.findAll()
                .filter(positions -> positions.getIsActivePosition().equalsIgnoreCase(Boolean.TRUE.toString()))
                .collect(Collectors.toList());
    }

    public Mono<Set<String>> getAllPositionStockSymbol() {
        return getAllActivePositions()
                .flatMap(positions -> Flux.fromIterable(positions)
                        .flatMap(position -> Flux.fromIterable(position.getPositionsStockInfoDetails().keySet()))
                        .collect(Collectors.toSet()));
    }
}
