package com.stock.service;

import com.stock.dto.StockDescriptionDetails;
import com.stock.dto.StockInfoDetails;
import com.stock.repository.StockDescriptionRepository;
import com.stock.repository.StockInfoRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StockDescriptionService {

    @Autowired
    private StockDescriptionRepository repository;

    public Mono<StockDescriptionDetails> save(StockDescriptionDetails details) {
        if (details == null) {
            return Mono.error(() -> new Throwable("StockInfoDetails is empty"));
        }
        return repository.save(details)
                .doOnNext(details1 -> log.error("details saved successfully. details: {} ", details1));
    }

    public Mono<StockDescriptionDetails> get(String symbol) {
        if (symbol == null) {
            return Mono.error(() -> new Throwable("symbol is empty"));
        }
        return repository.findById(symbol);
    }

    public Mono<StockDescriptionDetails> update(StockDescriptionDetails details) {
        if (details == null) {
            return Mono.error(() -> new Throwable("StockInfoDetails is empty"));
        }

        return get(details.getSymbol())
                .flatMap(stockInfo -> {
                    if (stockInfo.getCreatedDate() == null || stockInfo.getCreatedDate().isBefore(details.getCreatedDate())) {
                        return save(details);
                    } else {
                        return Mono.empty();
                    }
                });
    }

    public Mono<Void> delete(String symbol) {
        if (symbol == null) {
            return Mono.error(() -> new Throwable("symbol is empty"));
        }
        return repository.deleteById(symbol);
    }

    public Flux<StockDescriptionDetails> saveAll(List<StockDescriptionDetails> descriptionDetails) {
        if (CollectionUtils.isEmpty(descriptionDetails)) {
            log.error("List of Stock description is empty");
            return Flux.empty();
        }

        return repository.saveAll(descriptionDetails);
    }

    public Mono<List<String>> findDistinctSymbols() {
        return repository.findAll()
                .flatMap(stockDescriptionDetails -> Mono.justOrEmpty(stockDescriptionDetails.getSymbol()))
                .collect(Collectors.toList());
    }
}
