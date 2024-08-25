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
public class PositionalDailyChangeNetIncomeCalculatorService {

    @Autowired
    private PositionalDailyChangeNetIncomeRepository repository;

    @Autowired
    private StockDailyChangeService stockDailyChangeService;

    @Autowired
    private PositionsService positionsService;

    public Mono<PositionalDailyNetIncome> save(PositionalDailyNetIncome entity) {
        if (entity == null) {
            log.error("entity can't be null. Save skipped for PositionalDailyNetIncome. entity: {}", entity);
            return Mono.empty();
        }
        return repository.save(entity)
                .doOnNext(details -> log.info("details saved for position daily change net income. entity: {} ", entity));
    }

    public Mono<PositionalDailyNetIncome> update(PositionalDailyNetIncome entity) {
        if (entity == null) {
            log.error("entity can't be null. update skipped for PositionalDailyNetIncome. entity: {}", entity);
            return Mono.empty();
        }
        return get(entity.getKey(), false)
                .switchIfEmpty(Mono.defer(() -> get(entity.getKey(), true)))
                .flatMap(details -> {
                    if (details.getCreatedDate() == null || entity.getCreatedDate().isAfter(details.getCreatedDate())) {
                        return repository.save(entity);
                    }
                    return Mono.empty();
                });
    }

    public Mono<Void> delete(String key) {
        if (StringUtils.isEmpty(key)) {
            log.error("key can't be null, PositionalDailyNetIncome can't be deleted. key: {} ", key);
            return Mono.empty();
        }
        return repository.deleteById(key);
    }

    public Mono<PositionalDailyNetIncome> get(String key, boolean forceUpdate) {
        if (StringUtils.isEmpty(key)) {
            log.error("key can't be null, PositionalDailyNetIncome can't be fetched. key: {} ", key);
            return Mono.empty();
        }
        if (forceUpdate) {
            return forceUpdatePositionalDailyNetIncome(key, forceUpdate)
                    .flatMap(this::save);
        }
        return repository.findById(key)
                .switchIfEmpty(Mono.defer(() -> forceUpdatePositionalDailyNetIncome(key, true)))
                .flatMap(this::save)
                .doOnNext(details -> log.info("details fetched successfully for positional daily " +
                        "net income. positionKey :{} entity: {} ", key, details));
    }

    private Mono<PositionalDailyNetIncome> forceUpdatePositionalDailyNetIncome(String key, boolean forceUpdate) {
        return stockDailyChangeService.get(key, forceUpdate)
                .switchIfEmpty(Mono.defer(() -> stockDailyChangeService.get(key, true)))
                .zipWhen(stocksDailyChange -> positionsService.getAllActivePositions())
                .flatMap(tuple -> {
                    StocksDailyChange t1 = tuple.getT1();
                    List<Positions> t2 = tuple.getT2();
                    return Flux.fromIterable(t2)
                            .flatMap(positions -> Flux.fromIterable(positions.getPositionsStockInfoDetails().entrySet())
                                    .flatMap(positionEntry -> setDailyChangeNetIncome(t1, positions, positionEntry)))
                            .collect(Collectors.toMap(Pair::getLeft, Pair::getRight))
                            .flatMap(dat -> Mono.justOrEmpty(PositionalDailyNetIncome.builder()
                                    .key(key)
                                    .positionalDailyNetIncomeInfoDetails(dat)
                                    .createdDate(LocalDateTime.now())
                                    .build()));
                });
    }

    private static Mono<Pair<String, PositionalDailyNetIncomeInfo>> setDailyChangeNetIncome(StocksDailyChange t1, Positions positions, Map.Entry<String, PositionsStockInfo> positionEntry) {
        String instrumentName = positionEntry.getKey();
        PositionsStockInfo positionDetails = positionEntry.getValue();
        double todayLtp = (MapUtils.isNotEmpty(t1.getStocksDailyChangeInfoDetails())
                && t1.getStocksDailyChangeInfoDetails().containsKey(instrumentName))
                ? t1.getStocksDailyChangeInfoDetails().get(instrumentName).getLastTradedPrice() : 0.0;
        double pnlPerStock = ((todayLtp - positionDetails.getAveragePricePerStock()) * positionDetails.getQuantity());
        double changePerStock = (((todayLtp - positionDetails.getAveragePricePerStock()) / positionDetails.getAveragePricePerStock()) * 100);
        double totalInvestmentPerStock = (positionDetails.getQuantity() * positionDetails.getAveragePricePerStock());
        double totalLtpPerStock = (positionDetails.getQuantity() * todayLtp);
        double totalPnlOnTotalInvestedAmountPerStock = (totalLtpPerStock - totalInvestmentPerStock);
        double totalPnlPercentageOnTotalInvestedAmountPerStock = ((totalPnlOnTotalInvestedAmountPerStock / totalInvestmentPerStock) * 100);
        return Mono.justOrEmpty(Pair.of(instrumentName, PositionalDailyNetIncomeInfo.builder()
                .positionDate(positions.getKey().getKey())
                .basketId(positions.getKey().getBasketId())
                .instrument(instrumentName)
                .quantity(positionDetails.getQuantity())
                .averagePricePerStock(positionDetails.getAveragePricePerStock())
                .lastTradedPricePerStock(todayLtp)
                .pnlPerStock(pnlPerStock)
                .changePerStock(changePerStock)
                .totalInvestedAmount(totalInvestmentPerStock)
                .totalLtpAmount(totalLtpPerStock)
                .totalPnl(totalPnlOnTotalInvestedAmountPerStock)
                .totalPnlPercentage(totalPnlPercentageOnTotalInvestedAmountPerStock)
                .createdDate(LocalDateTime.now())
                .build()));
    }
}
