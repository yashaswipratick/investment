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
public class PositionalAggregatedDailyCalculatorService {

    @Autowired
    private PositionalAggregatedDailyCalculatorRepository repository;

    @Autowired
    private PositionalDailyChangeNetIncomeCalculatorService calculatorService;

    public Mono<PositionalAggregatedDailyCalculator> save(PositionalAggregatedDailyCalculator entity) {
        if (entity == null) {
            log.error("entity can't be null. entity: {} ", entity);
            return Mono.empty();
        }
        return repository.save(entity);
    }

    public Mono<PositionalAggregatedDailyCalculator> update(PositionalAggregatedDailyCalculator entity) {
        if (entity == null) {
            log.error("entity can't be null. entity: {} ", entity);
            return Mono.empty();
        }

        return get(entity.getKey(), false)
                .switchIfEmpty(Mono.defer(() -> get(entity.getKey(), true)))
                .flatMap(details -> {
                    if (details.getCreatedDate() == null || entity.getCreatedDate().isAfter(details.getCreatedDate())) {
                        return save(entity);
                    }
                    return Mono.empty();
                });
    }

    public Mono<PositionalAggregatedDailyCalculator> get(PositionalAggregatedDailyCalculatorKey key, boolean forceUpdate) {
        if (key == null) {
            log.error("key can't be null. entity: {} ", key);
            return Mono.empty();
        }
        if (forceUpdate) {
            return forceUpdatePositionalAggregatedDailyCalculator(key, forceUpdate)
                    .flatMap(this::save)
                    .doOnNext(details -> log.info("fetched and updated positional aggregated daily income forcefully. key: {} entity: {} ", key, details));
        }
        return repository.findById(key)
                .switchIfEmpty(Mono.defer(() -> forceUpdatePositionalAggregatedDailyCalculator(key, forceUpdate)))
                .flatMap(this::save)
                .doOnNext(details -> log.info("fetched positional aggregated daily income. key: {} entity: {} ", key, details));
    }

    private Mono<PositionalAggregatedDailyCalculator> forceUpdatePositionalAggregatedDailyCalculator(PositionalAggregatedDailyCalculatorKey key, boolean forceUpdate) {
        return calculatorService.get(key.getKey(), forceUpdate)
                .doOnNext(details -> log.info("details fetched successfully for positional daily net income. positionKey :{} entity: {} ", key, details))
                .switchIfEmpty(Mono.defer(() -> calculatorService.get(key.getKey(), true)))
                .flatMap(positionalDailyNetIncome ->
                        Flux.fromIterable(positionalDailyNetIncome.getPositionalDailyNetIncomeInfoDetails().entrySet())
                                .collectList()
                                .map(entries -> {
                                    PositionalAggregatedDailyCalculator build = PositionalAggregatedDailyCalculator.builder().build();
                                    entries.forEach(stringPositionalDailyNetIncomeInfoEntry -> {
                                        build.setAggregatedInvestmentAmount(
                                                build.getAggregatedInvestmentAmount() == null ? stringPositionalDailyNetIncomeInfoEntry.getValue().getTotalInvestedAmount()
                                                        : (build.getAggregatedInvestmentAmount() + stringPositionalDailyNetIncomeInfoEntry.getValue().getTotalInvestedAmount()));
                                        build.setAggregatedDailyLtpAmount(build.getAggregatedDailyLtpAmount() == null ? stringPositionalDailyNetIncomeInfoEntry.getValue().getTotalLtpAmount()
                                                : (build.getAggregatedDailyLtpAmount() + stringPositionalDailyNetIncomeInfoEntry.getValue().getTotalLtpAmount()));
                                        build.setTotalPnl(build.getTotalPnl() == null ? stringPositionalDailyNetIncomeInfoEntry.getValue().getTotalPnl()
                                                : (build.getTotalPnl() + stringPositionalDailyNetIncomeInfoEntry.getValue().getTotalPnl()));
                                    });
                                    return build;
                                })
                                .flatMap(positionalAggregatedDailyIncome -> Mono.justOrEmpty(PositionalAggregatedDailyCalculator.builder()
                                        .key(PositionalAggregatedDailyCalculatorKey.builder()
                                                .key(key.getKey())
                                                .basketId(key.getBasketId())
                                                .positionedDate(key.getPositionedDate())
                                                .build())
                                        .aggregatedInvestmentAmount(positionalAggregatedDailyIncome.getAggregatedInvestmentAmount())
                                        .aggregatedDailyLtpAmount(positionalAggregatedDailyIncome.getAggregatedDailyLtpAmount())
                                        .totalPnl(positionalAggregatedDailyIncome.getTotalPnl())
                                        .totalPnlPercentage((positionalAggregatedDailyIncome.getTotalPnl() / positionalAggregatedDailyIncome.getAggregatedInvestmentAmount()) * 100)
                                        .createdDate(LocalDateTime.now())
                                        .build())));
    }
}
