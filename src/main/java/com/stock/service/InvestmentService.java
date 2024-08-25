package com.stock.service;

import com.stock.dto.IndexStockDetails;
import com.stock.repository.NiftyIndexStockDetailsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
public class InvestmentService {

    @Autowired
    private FetchNiftyIndexStockDetailsService service;

    public Mono<Map<String, Double>> invest(BigInteger amount) {
        return service.get(LocalDate.now())
                .switchIfEmpty(Mono.defer(() -> {
                    try {
                        return service.fetchAndSave();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }))
                .flatMap(details -> {
                    int size = details.getIndexStockDetailsMap().size();
                    BigInteger equiWeightStockAmount = amount.divide(BigInteger.valueOf(size));
                    Map<String, Double> shareMap = new HashMap<>();
                    for (Map.Entry<String, IndexStockDetails> entry : details.getIndexStockDetailsMap().entrySet()) {
                        shareMap.put(entry.getKey(), equiWeightStockAmount.intValue()/Double.parseDouble(entry.getValue().getLastPrice()));
                    }
                    return Mono.justOrEmpty(shareMap);
                });
    }
}
