package com.stock.service;

import com.stock.dto.NiftyIndexStockDetails;
import com.stock.dto.StockInfoDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class FetchNiftyFiftyIndexSTockDetailsService {

    @Autowired
    private FetchNiftyIndexStockDetailsService service;

    @Autowired
    private StockInfoHttpEntryLoader entryLoader;

    public Mono<NiftyIndexStockDetails> getNiftyIndexStockDetails() throws IOException {
        return service.fetchAndSave()
                .switchIfEmpty(Mono.defer(Mono::empty));
    }

    public Mono<NiftyIndexStockDetails> upsert(NiftyIndexStockDetails details) {
        return service.update(details);
    }

    public Mono<Void> deleteStockById(LocalDate date) {
        return service.deleteById(date);
    }
}
