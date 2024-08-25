package com.stock.scheduler;

import com.datastax.oss.driver.shaded.guava.common.collect.Maps;
import com.stock.dto.StockInfoDTO;
import com.stock.dto.StockInfoDetails;
import com.stock.service.StockDescriptionService;
import com.stock.service.StockDetailsService;
import com.stock.service.StockInfoHttpEntryLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class StockFetcherScheduler {

    @Autowired
    private StockDescriptionService stockDescriptionService;

    @Autowired
    private StockInfoHttpEntryLoader stockInfoHttpEntryLoader;

    @Autowired
    private StockDetailsService stockInfoService;

    private static final List<String> stockSymbol = Arrays.asList("ADANIPORTS", "ADANIPOWER", "BHEL", "HAL", "HMAAGRO",
            "IRCTC", "NHPC", "NOVAAGRI", "ONGC", "PFC", "RELIANCE", "RVNL", "SAIL", "SBIN", "TATATECH");

    /*private static final List<String> stockSymbol = Arrays.asList("INFY", "ADANIPORTS", "ADANIPOWER", "BHEL", "COALINDIA",
            "ELECON", "GAIL", "HAL", "HMAAGRO", "IOC", "IRCTC", "IREDA", "NHPC", "NOVAAGRI", "ONGC", "PFC", "RECLTD", "RELIANCE",
            "RVNL", "SAIL", "SBIN", "TATATECH");*/

    @Scheduled(fixedRate = 15000)
    public void fetchStockDetailsList() {
        Flux.fromIterable(stockSymbol)
                .flatMap(stockSymbol -> {
                    Mono<StockInfoDetails> stockDetails = stockInfoHttpEntryLoader.getStockDetails(stockSymbol);
                    return stockDetails.flatMap(detail -> {
                        Map<String, StockInfoDTO> map = new HashMap<>(detail.getStockInfo());
                        System.out.println("Data collected from nse website: " + map);
                        return Mono.justOrEmpty(map);
                    });
                })
                .collect(() -> new HashMap<String, StockInfoDTO>(), HashMap::putAll)
                .doOnNext(details -> {
                    // Print details to console
                    System.out.println("Stock details fetched: " + details);
                    System.out.println("Stock details fetched Keys: " + details.keySet());
                })
                .flatMap(details -> stockInfoService.save(StockInfoDetails.builder()
                        .key(LocalDate.now().toString())
                        .stockInfo(details)
                        .createdDate(LocalDateTime.now())
                        .build()))
                .doOnNext(details -> System.out.println("details saved for data: " + details)) // Collect results into a List
                .subscribe();
    }

    private Flux<String> getSymbolListToFetchStockInfo() {
        return stockDescriptionService.findDistinctSymbols()
                .flatMapMany(symbols -> {
                    // Print symbols fetched from DB
                    System.out.println("Symbols fetched from DB: " + symbols);

                    // Convert List<String> to Flux<String>
                    return Flux.fromIterable(symbols)
                            .filter(symbol -> symbol != null && !symbol.isEmpty()) // Optionally filter out null or empty symbols
                            .distinct(); // Ensure symbols are distinct
                })
                .switchIfEmpty(Flux.empty()); // Return an empty Flux if no symbols are found
    }
}
