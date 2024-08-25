package com.stock.service;

import com.stock.dto.StockInfoDetails;
import com.stock.repository.StockInfoRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class StockDetailsService {

    @Autowired
    private StockInfoRepository repository;

    public Mono<StockInfoDetails> save(StockInfoDetails details) {
        if (details == null) {
            return Mono.error(() -> new Throwable("StockInfoDetails is empty"));
        }
        return get(details.getKey())
                .switchIfEmpty(Mono.defer(() -> repository.save(details)
                        .doOnNext(detailsLog ->
                                log.info("Stock info details saved successfully. key: {}", detailsLog.getKey()))))
                .flatMap(details1 -> {
                    if (Objects.nonNull(details1)) {
                        MapUtils.emptyIfNull(details1.getStockInfo()).putAll(details.getStockInfo());
                        details1.setCreatedDate(LocalDateTime.now());
                        return Mono.justOrEmpty(details1);
                    }
                    return Mono.empty();
                })
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

    public Mono<StockInfoDetails> get(String key) {
        return repository.findById(key)
                .doOnNext(details -> log.info("stock info Details fetched for date. key: {} ", key));
    }

    public Mono<StockInfoDetails> update(StockInfoDetails details) {
        if (details == null) {
            return Mono.error(() -> new Throwable("StockInfoDetails is empty"));
        }

        return get(details.getKey())
                .flatMap(stockInfo -> {
                    if (stockInfo.getCreatedDate() == null || stockInfo.getCreatedDate().isBefore(details.getCreatedDate())) {
                        return save(details);
                    } else {
                        log.info("Stock info details update skipped. key: {} ", details.getKey());
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(Mono.defer(() -> save(details)));
    }

    public Mono<Void> delete(String key) {
        if (key == null) {
            return Mono.error(() -> new Throwable("symbol is empty"));
        }
        return repository.deleteById(key)
                .doOnNext(detailsLog -> log.info("Stock infor details deleted for date. date: {}", key));
    }

    public Flux<StockInfoDetails> saveAll(List<StockInfoDetails> details) {
        return repository.saveAll(details);
    }
}
