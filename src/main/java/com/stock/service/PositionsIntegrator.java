package com.stock.service;

import com.stock.dto.Positions;
import com.stock.dto.PositionsStockInfo;
import com.stock.dto.StockInfoDTO;
import com.stock.dto.key.PositionsKey;
import com.stock.repository.PositionsRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PositionsIntegrator {

    @Autowired
    private PositionsService service;

    public Mono<Positions> save(Positions entity) {
        if (entity == null) {
            log.error("positions details are null. skipping save...");
            return Mono.empty();
        }
        if (StringUtils.isEmpty(entity.getKey().getBasketId())) {
            entity.getKey().setBasketId(UUID.randomUUID().toString());
        }
        return service.save(entity);
    }

    public Mono<Positions> update(Positions entity) {
        if (entity == null) {
            log.error("positions details are null. skipping save...");
            return Mono.empty();
        }

        return service.update(entity);
    }

    public Mono<Positions> get(PositionsKey key) {
        if (key == null) {
            log.error("key can't be null. skipping get for positions");
            return Mono.empty();
        }
        return service.get(key);
    }

    public Mono<List<Positions>> getAllActivePositions() {
        return service.getAllActivePositions();
    }

    public Mono<Positions> saveAll(List<PositionsStockInfo> positionsStockInfos) {
        return Flux.fromIterable(positionsStockInfos)
                .flatMap(positionsPerStock ->
                        Mono.justOrEmpty(Pair.of(positionsPerStock.getInstrument(), positionsPerStock)))
                .collectMap(Pair::getKey, Pair::getValue)
                .flatMap(map -> Mono.justOrEmpty(Positions.builder()
                                .key(PositionsKey.builder()
                                        .key(LocalDate.now().minusDays(2).toString())
                                        .basketId(UUID.randomUUID().toString())
                                        .build())
                                .positionsStockInfoDetails(map)
                                .isActivePosition(Boolean.TRUE.toString())
                                .createdDate(LocalDateTime.now().minusDays(2))
                        .build()))
                .flatMap(positions -> service.save(positions));
    }
}
