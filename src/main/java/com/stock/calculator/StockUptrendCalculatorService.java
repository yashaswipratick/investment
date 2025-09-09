package com.stock.calculator;

import com.stock.dto.StockHistoryDetails;
import com.stock.service.StockHistoryDataIntegrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

@Slf4j
@Service
public class StockUptrendCalculatorService {

    @Autowired
    private StockHistoryDataIntegrator stockHistoryDataIntegrator;

    public Mono<List<String>> detectUptrendStocksAdaptive() {
        return stockHistoryDataIntegrator.getAll()
                .flatMapMany(stockHistoryMap ->
                        Flux.fromIterable(stockHistoryMap.entrySet())
                                .flatMap(entry -> {
                                    String stockSymbol = entry.getKey();
                                    TreeMap<LocalDate, StockHistoryDetails> history = entry.getValue();

                                    if (history.isEmpty()) {
                                        return Mono.empty(); // no data
                                    }

                                    // Get latest record (most recent day)
                                    StockHistoryDetails latest = history.firstEntry().getValue();
                                    double latestClose = latest.getClose();
                                    double latestOpen = latest.getOpen();
                                    long latestVolume = Long.parseLong(latest.getVolume().replace(",", ""));

                                    // Apply your filters exactly as given
                                    boolean closeVsOpen = latestClose > (latestOpen * 1.03);
                                    boolean openAbove100 = latestOpen > 100;
                                    boolean volumeAbove30k = latestVolume > 30000;
                                    boolean closeBelow1500 = latestClose < 1500;
                                    boolean volumeAbove1M = latestVolume > 1_000_000;

                                    // Only keep stocks that satisfy ALL conditions
                                    if (closeVsOpen && openAbove100 && volumeAbove30k && closeBelow1500 && volumeAbove1M) {
                                        return Mono.just(stockSymbol);
                                    }

                                    return Mono.empty();
                                })
                )
                .collectList()
                .doOnNext(stocks -> log.info("✅ Stocks matching custom rules: {}", stocks));
    }

}
