package com.stock.calculator;

import com.stock.dto.StockHistoryDetails;
import com.stock.service.StockHistoryDataIntegrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
public class StockStrategyService {

    @Autowired
    private StockHistoryDataIntegrator stockHistoryDataIntegrator;

    public Mono<List<String>> stocksDailyBreakoutWithVolume(Integer breakoutDays) {
        return stockHistoryDataIntegrator.getAll() // Mono<Map<String, TreeMap<LocalDate, StockHistoryDetails>>>
                .flatMapMany(stockHistoryMap ->
                        Flux.fromIterable(stockHistoryMap.entrySet()) // Each stock symbol and its history
                                .flatMap(entry -> {
                                    String stockSymbol = entry.getKey();
                                    TreeMap<LocalDate, StockHistoryDetails> history = entry.getValue();

                                    if (history.isEmpty() || history.size() < breakoutDays) {
                                        return Mono.empty(); // not enough data
                                    }

                                    // Latest record (last day)
                                    Map.Entry<LocalDate, StockHistoryDetails> latestEntry = history.firstEntry();
                                    double latestClose = latestEntry.getValue().getClose();
                                    long latestVolume = Long.parseLong(latestEntry.getValue().getVolume().replace(",", ""));

                                    // Previous breakout days (exclude today)
                                    List<StockHistoryDetails> lastdaysList = history.entrySet().stream()
                                            .skip(1) // skip the latest
                                            .limit(breakoutDays)
                                            .map(Map.Entry::getValue)
                                            .toList();

                                    // Max close from last breakout days
                                    double maxClose = lastdaysList.stream()
                                            .mapToDouble(StockHistoryDetails::getClose)
                                            .max()
                                            .orElse(Double.MIN_VALUE);

                                    // SMA(breakoutDays) of volumes from last breakout days
                                    double avgVolume = lastdaysList.stream()
                                            .mapToLong(s -> Long.parseLong(s.getVolume().replace(",", "")))
                                            .average()
                                            .orElse(0.0);

                                    // Apply breakout conditions
                                    boolean priceBreakout = latestClose > maxClose;
                                    boolean volumeBreakout = latestVolume > avgVolume;

                                    if (priceBreakout && volumeBreakout) {
                                        log.info("📈 Breakout detected for {} on {} -> Close={} (>{}), Volume={} (>{})",
                                                stockSymbol,
                                                latestEntry.getKey(),
                                                latestClose, maxClose,
                                                latestVolume, avgVolume);

                                        return Mono.just(stockSymbol);
                                    }

                                    return Mono.empty();
                                })
                )
                .collectList()
                .doOnNext(breakoutStocks -> log.info("✅ Stocks with breakout today: {}", breakoutStocks));
    }

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

    public Mono<List<String>> stocksCrossing200SMA() {
        return stockHistoryDataIntegrator.getAll() // Mono<Map<String, TreeMap<LocalDate, StockHistoryDetails>>>
                .flatMapMany(stockHistoryMap ->
                        Flux.fromIterable(stockHistoryMap.entrySet())
                                .flatMap(entry -> {
                                    String stockSymbol = entry.getKey();
                                    TreeMap<LocalDate, StockHistoryDetails> history = entry.getValue();

                                    // Ensure we have at least 200 days of data
                                    if (history.size() < 200) {
                                        return Mono.empty();
                                    }

                                    // Latest record (most recent day)
                                    Map.Entry<LocalDate, StockHistoryDetails> latestEntry = history.firstEntry();
                                    StockHistoryDetails latest = latestEntry.getValue();
                                    double latestClose = latest.getClose();

                                    // Get the last 200 days excluding latest
                                    List<StockHistoryDetails> last200Days = history.values().stream()
                                            .skip(1) // skip latest
                                            .limit(200)
                                            .toList();

                                    // Calculate 200-day SMA of close
                                    double sma200 = last200Days.stream()
                                            .mapToDouble(StockHistoryDetails::getClose)
                                            .average()
                                            .orElse(0.0);

                                    // Check if latest close crossed above 200-day SMA
                                    if (latestClose > sma200) {
                                        return Mono.just(stockSymbol);
                                    }

                                    return Mono.empty();
                                })
                )
                .collectList()
                .doOnNext(stocks -> log.info("📈 Stocks crossing 200-day SMA: {}", stocks));
    }

    public Mono<List<String>> getCommonStocks(Integer breakoutDays) {
        return stockHistoryDataIntegrator.getAll() // Fetch DB once
                .map(stockHistoryMap -> {
                    Set<String> breakoutStocks = new HashSet<>();
                    Set<String> uptrendStocks = new HashSet<>();
                    Set<String> smaStocks = new HashSet<>();

                    // Iterate once over all stocks
                    for (Map.Entry<String, TreeMap<LocalDate, StockHistoryDetails>> entry : stockHistoryMap.entrySet()) {
                        String stockSymbol = entry.getKey();
                        TreeMap<LocalDate, StockHistoryDetails> history = entry.getValue();

                        if (history.isEmpty()) continue;

                        // ✅ 1. Breakout with volume
                        if (history.size() >= breakoutDays) {
                            Map.Entry<LocalDate, StockHistoryDetails> latestEntry = history.firstEntry();
                            double latestClose = latestEntry.getValue().getClose();
                            long latestVolume = Long.parseLong(latestEntry.getValue().getVolume().replace(",", ""));

                            List<StockHistoryDetails> lastDays = history.entrySet().stream()
                                    .skip(1)
                                    .limit(breakoutDays)
                                    .map(Map.Entry::getValue)
                                    .toList();

                            double maxClose = lastDays.stream().mapToDouble(StockHistoryDetails::getClose).max().orElse(Double.MIN_VALUE);
                            double avgVolume = lastDays.stream().mapToLong(s -> Long.parseLong(s.getVolume().replace(",", ""))).average().orElse(0.0);

                            if (latestClose > maxClose && latestVolume > avgVolume) {
                                breakoutStocks.add(stockSymbol);
                            }
                        }

                        // ✅ 2. Uptrend adaptive rules
                        StockHistoryDetails latest = history.firstEntry().getValue();
                        double latestClose = latest.getClose();
                        double latestOpen = latest.getOpen();
                        long latestVolume = Long.parseLong(latest.getVolume().replace(",", ""));

                        boolean closeVsOpen = latestClose > (latestOpen * 1.03);
                        boolean openAbove100 = latestOpen > 100;
                        boolean volumeAbove30k = latestVolume > 30000;
                        boolean closeBelow1500 = latestClose < 1500;
                        boolean volumeAbove1M = latestVolume > 1_000_000;

                        if (closeVsOpen && openAbove100 && volumeAbove30k && closeBelow1500 && volumeAbove1M) {
                            uptrendStocks.add(stockSymbol);
                        }

                        // ✅ 3. Crossing 200 SMA
                        if (history.size() >= 200) {
                            List<StockHistoryDetails> last200Days = history.values().stream()
                                    .skip(1) // exclude latest
                                    .limit(200)
                                    .toList();

                            double sma200 = last200Days.stream().mapToDouble(StockHistoryDetails::getClose).average().orElse(0.0);

                            if (latestClose > sma200) {
                                smaStocks.add(stockSymbol);
                            }
                        }
                    }

                    // ✅ Find intersection (common stocks)
                    breakoutStocks.retainAll(uptrendStocks);
                    breakoutStocks.retainAll(smaStocks);

                    List<String> list = new ArrayList<>(breakoutStocks);
                    Collections.sort(list);
                    return list;
                })
                .doOnNext(common -> log.info("🔥 Common stocks in all 3 strategies: {}", common));
    }

}
