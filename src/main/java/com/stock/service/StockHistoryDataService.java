package com.stock.service;

import com.stock.dto.StockHistory;
import com.stock.dto.StockHistoryDetails;
import com.stock.dto.key.StockHistoryKey;
import com.stock.repository.StockHistoryDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StockHistoryDataService {

    @Autowired
    private StockHistoryDataRepository repository;

    public Mono<StockHistory> upsert(StockHistory details) {
        if (details == null) {
            return Mono.error(() -> new Throwable("StockInfoDetails is empty"));
        }
        return get(details.getKey())
                .flatMap(data -> repository.save(data)
                        .doOnNext(detailsLog ->
                                log.info("Stock info details saved successfully. key: {}", detailsLog.getKey()))
                        .doOnError(error -> {
                            if (error instanceof QueryTimeoutException) {
                                log.error("Query timed out. Retrying...");
                            } else {
                                log.error("Error saving details: {}", error.getMessage());
                            }
                        })
                        .retryWhen(Retry.backoff(5, Duration.ofSeconds(2))
                                .filter(throwable -> throwable instanceof QueryTimeoutException)
                                .doBeforeRetry(retrySignal -> log.info("Retry attempt #{} due to: {}", retrySignal.totalRetries(), retrySignal.failure().getMessage())))
                        .onErrorResume(throwable -> {
                            log.error("Failed to save details after retries: {}", throwable.getMessage());
                            return Mono.error(throwable);
                        }));
    }

    public Mono<StockHistory> save(StockHistory details) {
        if (details == null) {
            return Mono.error(() -> new Throwable("StockInfoDetails is empty"));
        }
        return repository.save(details);
    }

    public Mono<List<StockHistory>> saveAll(List<StockHistory> details) {
        if (details == null) {
            return Mono.error(() -> new Throwable("StockInfoDetails is empty"));
        }
        return repository.saveAll(details)
                .collect(Collectors.toList());
    }



    public Mono<StockHistory> get(StockHistoryKey key) {
        return repository.findById(key)
                .switchIfEmpty(Mono.defer(Mono::empty))
                .doOnNext(details -> log.info("stock History Details fetched for date. key: {} ", key));
    }

    public Mono<Map<String, TreeMap<LocalDate, StockHistoryDetails>>> getAll() {
        return repository.findAll()
                .flatMap(stockHistory -> {
                    if (MapUtils.isNotEmpty(stockHistory.getStockHistoryDetails())) {
                        return Mono.justOrEmpty(Pair.of(stockHistory.getKey().getKey(), stockHistory.getStockHistoryDetails().entrySet()));
                    }
                    return Mono.empty();
                })
                .collect(Collectors.toMap(
                        Pair::getLeft, // stock symbol as key
                        pair -> {
                            TreeMap<LocalDate, StockHistoryDetails> tree = new TreeMap<>(Collections.reverseOrder());
                            for (Map.Entry<LocalDate, StockHistoryDetails> entry : pair.getRight()) {
                                tree.put(entry.getKey(), entry.getValue());
                            }
                            return tree;
                        },
                        (existing, incoming) -> {
                            existing.putAll(incoming);
                            return existing;
                        }
                ))
                .switchIfEmpty(Mono.defer(Mono::empty));
    }

    public Mono<Void> delete(StockHistoryKey key) {
        if (key == null) {
            return Mono.error(() -> new Throwable("symbol is empty"));
        }
        return repository.deleteById(key)
                .doOnNext(detailsLog -> log.info("Stock infor details deleted for date. date: {}", key));
    }
}
