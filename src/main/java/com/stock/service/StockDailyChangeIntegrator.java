package com.stock.service;

import com.stock.dto.StocksDailyChange;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class StockDailyChangeIntegrator {

    @Autowired
    private StockDailyChangeService service;

    public Mono<StocksDailyChange> save(StocksDailyChange entity) {
        if (entity == null) {
            log.error("entity cant be null. skipping save for stock daily change");
            return Mono.empty();
        }
        return service.save(entity);
    }

    public Mono<StocksDailyChange> update(StocksDailyChange entity) {
        if (entity == null) {
            log.error("entity cant be null. skipping save for stock daily change");
            return Mono.empty();
        }
        return service.update(entity);
    }

    public Mono<StocksDailyChange> get(String key, boolean forceUpdate) {
        if (StringUtils.isEmpty(key)) {
            log.error("can't fetch stock daily change data for null key. key: {} ", key);
            return Mono.empty();
        }
        return service.get(key, forceUpdate);
    }

    public Mono<Void> delete(String key) {
        if (StringUtils.isEmpty(key)) {
            log.error("can't fetch stock daily change data for null key. key: {} ", key);
            return Mono.empty();
        }
        return service.delete(key);
    }
}
