package com.stock.service;

import com.stock.dto.StockInfoDetails;
import com.stock.entryloader.StockInfoHttpEntryLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class StockDetailsIntegrator {

    @Autowired
    private StockDetailsService stockDetailsService;

    @Autowired
    private StockInfoHttpEntryLoader entryLoader;

    public Mono<StockInfoDetails> getStockDetailForProvidedSymbol(String key) {
        return stockDetailsService.get(key)
                .switchIfEmpty(Mono.defer(() -> entryLoader.getStockDetails(key)));
    }

    public Mono<StockInfoDetails> update(StockInfoDetails details) {
        if (details == null) {
            log.error("Provide Stock info details are wrong. Update Skipped, details: {} ", details );
            return Mono.empty();
        }
        return stockDetailsService.update(details);
    }

    public Mono<Void> deleteStock(String key) {
        return stockDetailsService.delete(key);
    }
}
