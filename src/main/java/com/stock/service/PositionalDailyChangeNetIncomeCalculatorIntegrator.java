package com.stock.service;

import com.stock.dto.*;
import com.stock.repository.PositionalDailyChangeNetIncomeRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PositionalDailyChangeNetIncomeCalculatorIntegrator {

    @Autowired
    private PositionalDailyChangeNetIncomeCalculatorService service;

    public Mono<PositionalDailyNetIncome> save(PositionalDailyNetIncome entity) {
        if (entity == null) {
            log.error("entity can't be null. Save skipped for PositionalDailyNetIncome. entity: {}", entity);
            return Mono.empty();
        }
        return service.save(entity);
    }

    public Mono<PositionalDailyNetIncome> update(PositionalDailyNetIncome entity) {
        if (entity == null) {
            log.error("entity can't be null. update skipped for PositionalDailyNetIncome. entity: {}", entity);
            return Mono.empty();
        }
        return service.update(entity);
    }

    public Mono<Void> delete(String key) {
        if (StringUtils.isEmpty(key)) {
            log.error("key can't be null, PositionalDailyNetIncome can't be deleted. key: {} ", key);
            return Mono.empty();
        }
        return service.delete(key);
    }

    public Mono<PositionalDailyNetIncome> get(String key, boolean forceUpdate) {
        if (StringUtils.isEmpty(key)) {
            log.error("key can't be null, PositionalDailyNetIncome can't be fetched. key: {} ", key);
            return Mono.empty();
        }
        return service.get(key, forceUpdate);
    }
}
