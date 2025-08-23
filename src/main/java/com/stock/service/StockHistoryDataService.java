package com.stock.service;

import com.stock.dto.StockHistory;
import com.stock.dto.key.StockHistoryKey;
import com.stock.repository.StockHistoryDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

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

    public Mono<StockHistory> get(StockHistoryKey key) {
        return repository.findById(key)
                .switchIfEmpty(Mono.defer(Mono::empty))
                .doOnNext(details -> log.info("stock History Details fetched for date. key: {} ", key));
    }

    public Mono<Void> delete(StockHistoryKey key) {
        if (key == null) {
            return Mono.error(() -> new Throwable("symbol is empty"));
        }
        return repository.deleteById(key)
                .doOnNext(detailsLog -> log.info("Stock infor details deleted for date. date: {}", key));
    }
}
