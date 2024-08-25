package com.stock.service;

import com.stock.dto.PositionalAggregatedDailyCalculator;
import com.stock.dto.key.PositionalAggregatedDailyCalculatorKey;
import com.stock.repository.PositionalAggregatedDailyCalculatorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Service
public class PositionalAggregatedDailyCalculatorIntegrator {

    @Autowired
    private PositionalAggregatedDailyCalculatorService service;

    public Mono<PositionalAggregatedDailyCalculator> save(PositionalAggregatedDailyCalculator entity) {
        if (entity == null) {
            log.error("entity can't be null. entity: {} ", entity);
            return Mono.empty();
        }
        return service.save(entity);
    }

    public Mono<PositionalAggregatedDailyCalculator> update(PositionalAggregatedDailyCalculator entity) {
        if (entity == null) {
            log.error("entity can't be null. entity: {} ", entity);
            return Mono.empty();
        }

        return service.update(entity);
    }

    public Mono<PositionalAggregatedDailyCalculator> get(PositionalAggregatedDailyCalculatorKey key, boolean forceUpdate) {
        if (key == null) {
            log.error("key can't be null. entity: {} ", key);
            return Mono.empty();
        }

        return service.get(key, forceUpdate);
    }
}
