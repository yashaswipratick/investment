package com.stock.service;

import com.stock.dto.StockHistory;
import com.stock.dto.StockHistoryDetails;
import com.stock.dto.key.StockHistoryKey;
import com.stock.repository.StockHistoryDataRepository;
import lombok.extern.slf4j.Slf4j;
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

    public Mono<StockHistory> save(StockHistory details) {
        if (details == null) {
            return Mono.error(() -> new Throwable("StockHistory is empty"));
        }
        return repository.save(details)
                .doOnNext(saved -> log.info("Stock history saved successfully. key: {}, count: {}", saved.getKey(), saved.getStockHistoryDetails().size()))
                .doOnError(error -> {
                    if (error instanceof QueryTimeoutException) {
                        log.error("Query timed out while saving stock history. key: {}", details.getKey());
                    } else {
                        log.error("Error saving stock history: {}", error.getMessage());
                    }
                })
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .filter(throwable -> throwable instanceof QueryTimeoutException)
                        .doBeforeRetry(retrySignal -> log.info("Retry attempt #{} for stock history. key: {}", retrySignal.totalRetries(), details.getKey())))
                .onErrorResume(error -> {
                    log.error("Failed to save stock history after retries: {}", error.getMessage());
                    return Mono.error(error);
                });
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
                            // TreeMap in natural order (chronological, oldest first) - do NOT use reverseOrder()
                            TreeMap<LocalDate, StockHistoryDetails> tree = new TreeMap<>();
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
            return Mono.error(new Throwable("symbol is empty"));
        }
        return repository.deleteById(key)
                .doFinally(signalType -> log.info("Stock history details deleted for key: {}", key));
    }
}
