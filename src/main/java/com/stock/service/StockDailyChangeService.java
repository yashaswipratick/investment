package com.stock.service;

import com.stock.dto.StockInfoDTO;
import com.stock.dto.StocksDailyChange;
import com.stock.dto.StocksDailyChangeInfo;
import com.stock.repository.StocksDailyChangeRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Slf4j
@Service
public class StockDailyChangeService {

    @Autowired
    private StocksDailyChangeRepository repository;

    @Autowired
    private StockDetailsService stockDetailsService;

    public Mono<StocksDailyChange> save(StocksDailyChange entity) {
        if (entity == null) {
            log.error("entity cant be null. skipping save for stock daily change");
            return Mono.empty();
        }
        return repository.save(entity)
                .doOnNext(details -> log.info("Stock Daily Change details saved successfully. entity: {} ", entity));
    }

    public Mono<StocksDailyChange> update(StocksDailyChange entity) {
        if (entity == null) {
            log.error("entity cant be null. skipping save for stock daily change");
            return Mono.empty();
        }
        return get(entity.getKey(), false)
                .switchIfEmpty(Mono.defer(() -> get(entity.getKey(), true)
                        .doOnNext(details -> log.info("Stock Daily Change details saved successfully. entity: {} ", entity))))
                .flatMap(existingDetails -> {
                    if (existingDetails.getCreatedDate() == null || entity.getCreatedDate().isAfter(existingDetails.getCreatedDate())) {
                        return save(entity)
                                .doOnNext(details -> log.info("Stock Daily Change details saved successfully. entity: {} ", entity));
                    }
                    return Mono.empty();
                });
    }

    public Mono<StocksDailyChange> get(String key, boolean forceUpdate) {
        if (StringUtils.isEmpty(key)) {
            log.error("can't fetch stock daily change data for null key. key: {} ", key);
            return Mono.empty();
        }
        if (forceUpdate) {
            return forceUpdateStocksDailyChange(key)
                    .flatMap(this::save);
        }
        return repository.findById(key)
                .switchIfEmpty(Mono.defer(() -> forceUpdateStocksDailyChange(key)
                        .doOnNext(details -> log.info("data fetched forcefully for stock daily change service. details: {}", details))))
                .flatMap(this::save)
                .doOnNext(details -> log.info("Stock daily change data: details: {} ", details));
    }

    private Mono<StocksDailyChange> forceUpdateStocksDailyChange(String key) {
        return stockDetailsService.get(key)
                .doOnNext(stockInfoDetails -> log.info("data fetched for stock info details: {} ", stockInfoDetails))
                .flatMap(stockInfoDetails -> Flux.fromIterable(stockInfoDetails.getStockInfo().entrySet())
                        .flatMap(realTimeStockInfoDetailsEntry -> {
                            String instrumentName = realTimeStockInfoDetailsEntry.getKey();
                            StockInfoDTO stockInfo = realTimeStockInfoDetailsEntry.getValue();
                            double ltp = Double.parseDouble(stockInfo.getPriceInfo().getClose());
                            if (ltp != 0.0) {
                                return Mono.justOrEmpty(Pair.of(instrumentName, StocksDailyChangeInfo.builder()
                                        .instrument(instrumentName)
                                        .lastTradedPrice(ltp)
                                        .createdDate(LocalDateTime.now())
                                        .build()));
                            }
                            return Mono.empty();
                        })
                        .collect(Collectors.toMap(Pair::getKey, Pair::getValue)))
                .flatMap(stockLtpDetails -> Mono.justOrEmpty(StocksDailyChange.builder()
                        .key(key)
                        .stocksDailyChangeInfoDetails(stockLtpDetails)
                        .createdDate(LocalDateTime.now())
                        .build()));
    }

    public Mono<Void> delete(String key) {
        if (StringUtils.isEmpty(key)) {
            log.error("can't fetch stock daily change data for null key. key: {} ", key);
            return Mono.empty();
        }
        return repository.deleteById(key)
                .doOnNext(details -> log.info("Stock daily change data: details: {} ", details));
    }
}
