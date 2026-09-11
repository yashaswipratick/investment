package com.stock.service;

import com.stock.dto.StockHistory;
import com.stock.dto.StockHistoryDetails;
import com.stock.dto.key.StockHistoryKey;
import com.stock.repository.StockHistoryDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDate;
import java.io.IOException;
import java.io.StringWriter;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.*;
import java.util.stream.Collectors;
import com.opencsv.CSVWriter;

@Service
@Slf4j
public class StockHistoryDataService {

    @Autowired
    private StockHistoryDataRepository repository;

    public Mono<StockHistory> save(StockHistory details) {
        if (details == null) {
            return Mono.error(() -> new Throwable("StockHistory is empty"));
        }
        return repository.save(details)
                .doOnNext(saved -> log.info("Stock history saved successfully. key: {}, count: {}", saved.getKey(), saved.getStockHistoryDetails().size()))
                .doOnError(error -> {
                    if (error instanceof QueryTimeoutException) {
                        log.error("Query timed out while saving stock history. key: {}", details.getKey());
                    } else {
                        log.error("Error saving stock history: {}", error.getMessage());
                    }
                })
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .filter(throwable -> throwable instanceof QueryTimeoutException)
                        .doBeforeRetry(retrySignal -> log.info("Retry attempt #{} for stock history. key: {}", retrySignal.totalRetries(), details.getKey())))
                .onErrorResume(error -> {
                    log.error("Failed to save stock history after retries: {}", error.getMessage());
                    return Mono.error(error);
                });
    }

    public Mono<List<StockHistory>> saveAll(List<StockHistory> details) {
        if (details == null) {
            return Mono.error(() -> new Throwable("StockInfoDetails is empty"));
        }
        return repository.saveAll(details)
                .collect(Collectors.toList());
    }


    public Mono<StockHistory> get(StockHistoryKey key) {
        return repository.findById(key)
                .switchIfEmpty(Mono.defer(Mono::empty))
                .doOnNext(details -> log.info("stock History Details fetched for date. key: {} ", key));
    }

    public Mono<String> getCsv(String stockName) {
        if (stockName == null || stockName.isBlank()) {
            return Mono.error(new IllegalArgumentException("stockName must not be blank"));
        }

        String normalizedStockName = stockName.trim().toUpperCase(Locale.ROOT);
        return repository.findById(StockHistoryKey.builder().key(normalizedStockName).build())
                .switchIfEmpty(Mono.error(new NoSuchElementException(
                        "No stock history found for stock: " + normalizedStockName)))
                .map(stockHistory -> {
                    StringWriter output = new StringWriter();
                    try (CSVWriter writer = new CSVWriter(output)) {
                        writer.writeNext(new String[]{
                                "historyDate", "series", "stockName", "open", "high", "low",
                                "prevClose", "ltp", "close", "vwap", "fiftyTwoWeekHigh",
                                "fiftyTwoWeekLow", "volume", "value", "totalTrades", "isin"
                        });

                        if (stockHistory.getStockHistoryDetails() != null) {
                            stockHistory.getStockHistoryDetails().forEach((date, details) -> writer.writeNext(new String[]{
                                    String.valueOf(date),
                                    nullToEmpty(details.getSeries()),
                                    nullToEmpty(details.getStockName()),
                                    value(details.getOpen()),
                                    value(details.getHigh()),
                                    value(details.getLow()),
                                    value(details.getPrevClose()),
                                    value(details.getLtp()),
                                    value(details.getClose()),
                                    value(details.getVwap()),
                                    value(details.getFiftyTwoWeekHigh()),
                                    value(details.getFiftyTwoWeekLow()),
                                    nullToEmpty(details.getVolume()),
                                    nullToEmpty(details.getValue()),
                                    nullToEmpty(details.getTotalTrades()),
                                    nullToEmpty(details.getIsin())
                            }));
                        }
                    } catch (IOException e) {
                        throw new IllegalStateException("Failed to generate stock history CSV", e);
                    }
                    return output.toString();
                });
    }

    public Mono<byte[]> getCsvZip(String stockNames) {
        if (stockNames == null || stockNames.isBlank()) {
            return Mono.error(new IllegalArgumentException("stockNames must not be empty"));
        }

        List<String> normalized = Arrays.stream(stockNames.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();

        if (normalized.isEmpty()) {
            return Mono.error(new IllegalArgumentException("stockNames must contain at least one stock"));
        }

        return Flux.fromIterable(normalized)
                .flatMap(stockName -> repository.findById(StockHistoryKey.builder().key(stockName).build())
                        .map(stockHistory -> Map.entry(stockName, stockHistory)))
                .collectList()
                .flatMap(histories -> {
                    if (histories.isEmpty()) {
                        return Mono.error(new NoSuchElementException("No stock history found for requested stocks"));
                    }

                    try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                         ZipOutputStream zip = new ZipOutputStream(output)) {
                        for (Map.Entry<String, StockHistory> entry : histories) {
                            String filename = entry.getKey() + "_stock_history.csv";
                            zip.putNextEntry(new ZipEntry(filename));
                            zip.write(toCsv(entry.getValue()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            zip.closeEntry();
                        }
                        zip.finish();
                        return Mono.just(output.toByteArray());
                    } catch (IOException e) {
                        return Mono.error(new IllegalStateException("Failed to generate stock history ZIP", e));
                    }
                });
    }

    private static String toCsv(StockHistory stockHistory) {
        StringWriter output = new StringWriter();
        try (CSVWriter writer = new CSVWriter(output)) {
            writer.writeNext(new String[]{
                    "historyDate", "series", "stockName", "open", "high", "low",
                    "prevClose", "ltp", "close", "vwap", "fiftyTwoWeekHigh",
                    "fiftyTwoWeekLow", "volume", "value", "totalTrades", "isin"
            });
            if (stockHistory.getStockHistoryDetails() != null) {
                stockHistory.getStockHistoryDetails().forEach((date, details) -> writer.writeNext(new String[]{
                        String.valueOf(date), nullToEmpty(details.getSeries()), nullToEmpty(details.getStockName()),
                        value(details.getOpen()), value(details.getHigh()), value(details.getLow()),
                        value(details.getPrevClose()), value(details.getLtp()), value(details.getClose()),
                        value(details.getVwap()), value(details.getFiftyTwoWeekHigh()), value(details.getFiftyTwoWeekLow()),
                        nullToEmpty(details.getVolume()), nullToEmpty(details.getValue()),
                        nullToEmpty(details.getTotalTrades()), nullToEmpty(details.getIsin())
                }));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate stock history CSV", e);
        }
        return output.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String value(Double value) {
        return value == null ? "" : value.toString();
    }

    public Mono<Map<String, TreeMap<LocalDate, StockHistoryDetails>>> getAll() {
        return repository.findAll()
                .flatMap(stockHistory -> {
                    if (MapUtils.isNotEmpty(stockHistory.getStockHistoryDetails())) {
                        return Mono.justOrEmpty(Pair.of(stockHistory.getKey().getKey(), stockHistory.getStockHistoryDetails().entrySet()));
                    }
                    return Mono.empty();
                })
                .collect(Collectors.toMap(
                        Pair::getLeft, // stock symbol as key
                        pair -> {
                            // TreeMap in natural order (chronological, oldest first) - do NOT use reverseOrder()
                            TreeMap<LocalDate, StockHistoryDetails> tree = new TreeMap<>();
                            for (Map.Entry<LocalDate, StockHistoryDetails> entry : pair.getRight()) {
                                tree.put(entry.getKey(), entry.getValue());
                            }
                            return tree;
                        },
                        (existing, incoming) -> {
                            existing.putAll(incoming);
                            return existing;
                        }
                ))
                .switchIfEmpty(Mono.defer(Mono::empty));
    }

    public Mono<Void> delete(StockHistoryKey key) {
        if (key == null) {
            return Mono.error(new Throwable("symbol is empty"));
        }
        return repository.deleteById(key)
                .doFinally(signalType -> log.info("Stock history details deleted for key: {}", key));
    }
}
