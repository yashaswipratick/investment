package com.stock.service;

import com.stock.dto.*;
import com.stock.dto.key.StockHistoryKey;
import com.stock.entryloader.StockHistoryDataHttpEntryLoader;
import com.stock.util.Utility;
import com.stock.util.WorkingDaysSlots;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class StockHistoryDataIntegrator {

    @Autowired
    private StockHistoryDataService stockHistoryDataService;

    @Autowired
    private StockHistoryDataHttpEntryLoader entryLoader;

    @Autowired
    private SectorWiseStockDataIntegrator sectorWiseStockDataIntegrator;

    private static final DateTimeFormatter NSE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    /**
     * Fetches stock history from NSE NextApi (GetQuoteApi) using cookie from cookie.txt
     * merged with a fresh session cookie, then saves to the stock_history table.
     *
     * @param request must contain stockSymbol, series, from (dd-MM-yyyy), to (dd-MM-yyyy)
     */
    public Mono<StockHistory> fetchAndSaveFromNextApi(StockHistoryRequest request) {
        if (request == null) {
            log.error("Request is null. NextApi fetch skipped.");
            return Mono.empty();
        }
        return entryLoader.getStockHistoryFromNextApi(request)
                .flatMap(stockHistory -> stockHistoryDataService.save(stockHistory)
                        .doOnNext(saved -> log.info("NextApi: data saved for {}. records: {}",
                                saved.getKey().getKey(), saved.getStockHistoryDetails().size()))
                        .onErrorResume(saveError -> {
                            log.error("NextApi: save failed for {}. Returning fetched response anyway. error: {}",
                                    stockHistory.getKey().getKey(), saveError.getMessage());
                            return Mono.just(stockHistory);
                        }))
                .doOnNext(stockHistory -> log.info("NextApi: response returned for {}. records: {}",
                        stockHistory.getKey().getKey(), stockHistory.getStockHistoryDetails().size()));
    }

    public Mono<StockHistory> fetchAndSave(StockHistoryRequest request) {
        if (request == null) {
            log.error("Provide Stock info details are wrong. Update Skipped, details: {} ", request);
            return Mono.empty();
        }
        List<StockHistoryRequest> stockHistoryRequest = getStockHistoryRequests(request);

        return fetchStockHistoryDetailsFromNSE(stockHistoryRequest)
                .flatMap(stockHistory -> stockHistoryDataService.save(stockHistory));
    }

    public Mono<StockHistory> fetchCSVAndSave(StockHistoryRequest request) {
        if (request == null) {
            log.error("Provide Stock info details are wrong. Update Skipped, details: {} ", request);
            return Mono.empty();
        }

        return fetchStockHistoryDetailsFromNSECSV(request)
                .flatMap(stockHistory -> stockHistoryDataService.save(stockHistory))
                .doOnNext(stockHistory -> log.info("CSV data fetched. stock: {}, count: {} ", stockHistory.getKey().getKey(), stockHistory.getStockHistoryDetails().size()));
    }

    public Mono<StockHistory> save(StockHistory details) {
        return stockHistoryDataService.save(details);
    }

    public Mono<List<StockHistory>> saveAll(List<StockHistory> details) {
        return stockHistoryDataService.saveAll(details);
    }

    public Mono<StockHistory> fetchStockHistoryDetailsFromNSE(List<StockHistoryRequest> stockHistoryRequest) {
        return Flux.fromIterable(stockHistoryRequest)
                .flatMap(req -> entryLoader.getStockHistoryDetailsList(req)
                        .flatMapMany(Flux::fromIterable), 10)
                .collect(Collectors.toList())
                .flatMap(stockHistories -> {
                    if (stockHistories.isEmpty()) {
                        log.warn("No stock history data fetched from NSE");
                        return Mono.empty();
                    }
                    // Build TreeMap<LocalDate, StockHistoryDetails>
                    TreeMap<LocalDate, StockHistoryDetails> stockHistoryDetailsMap =
                            stockHistories.stream()
                                    .collect(Collectors.toMap(
                                            StockHistoryDetails::getHistoryDate,   // key mapper
                                            details -> details,                     // value mapper
                                            (existing, replacement) -> replacement, // merge function
                                            TreeMap::new                            // supplier
                                    ));
                    // Get stock symbol from first element (optimized - single stream call)
                    String stockSymbol = stockHistories.get(0).getStockName();
                    return Mono.justOrEmpty(StockHistory.builder()
                                    .key(StockHistoryKey.builder().key(stockSymbol).build())
                                    .stockHistoryDetails(stockHistoryDetailsMap)
                                    .build())
                            .doOnNext(details -> log.info("stock history data fetched. count: {}, key: {} ", details.getStockHistoryDetails().size(), details.getKey()));
                });
    }

    public List<StockHistoryRequest> getStockHistoryRequests(StockHistoryRequest request) {
        List<TreeSet<String>> dateSlots = WorkingDaysSlots.getDateSlots(request.getTo(), request.getNumOfDays());
        return dateSlots.stream().map(localDates -> StockHistoryRequest.builder()
                        .stockSymbol(request.getStockSymbol())
                        .series(request.getSeries())
                        .from(localDates.first())
                        .to(localDates.last())
                        .build())
                .collect(Collectors.toList());
    }

    public Mono<Map<String, StockHistory>> fetchStockDetailsForGivenSector(String sector) {
        return sectorWiseStockDataIntegrator.get(sector)
                .flatMapMany(details -> Flux.fromIterable(details.getStocks()))
                .flatMap(stockSymbol -> {
                    List<StockHistoryRequest> stockHistoryRequest =
                            getStockHistoryRequests(
                                    StockHistoryRequest.builder()
                                            .stockSymbol(stockSymbol)
                                            .to(Utility.dateFormatterCurrentDay())
                                            //.from(Utility.getDateMinusDays(LocalDate.now(), 250))
                                            .series("EQ")
                                            .numOfDays(250)
                                            .build()
                            );

                    return fetchStockHistoryDetailsFromNSE(stockHistoryRequest)
                            .flatMap(this::save)
                            .flatMap(details -> Mono.justOrEmpty(Pair.of(stockSymbol, details)));
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                .doOnNext(details -> log.info("stock history fetched for sector. sector: {},  count: {} ", sector, details.size()));
    }

    public Mono<Map<String, StockHistory>> fetchStockDetailsForGivenSectors(List<String> sectors) {
        return Flux.fromIterable(sectors)
                .flatMap(sector -> sectorWiseStockDataIntegrator.get(sector)
                        .flatMapMany(details -> Flux.fromIterable(details.getStocks()))
                )
                .flatMap(stockSymbol -> {
                    List<StockHistoryRequest> stockHistoryRequest =
                            getStockHistoryRequests(
                                    StockHistoryRequest.builder()
                                            .stockSymbol(stockSymbol)
                                            .to(Utility.dateFormatterCurrentDay())
                                            //.from(Utility.getDateMinusDays(LocalDate.now(), 250))
                                            .series("EQ")
                                            .numOfDays(250)
                                            .build()
                            );

                    return fetchStockHistoryDetailsFromNSE(stockHistoryRequest)
                            .flatMap(this::save)
                            .flatMap(details -> Mono.justOrEmpty(Pair.of(stockSymbol, details)));
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                .doOnNext(details -> log.info("Stock history fetched for all sectors. sectors: {}, total stocks: {} ",
                        sectors, details.size()));
    }


    public Mono<Map<String, TreeMap<LocalDate, StockHistoryDetails>>> getAll() {
        return stockHistoryDataService.getAll();
    }


    private LocalDate convertToLocalDate(String dateStr) {
        // Define the formatter matching your string
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        // Parse the string to LocalDate
        LocalDate localDate = LocalDate.parse(dateStr, formatter);

        return localDate;
    }

    public Mono<StockHistory> fetchStockHistoryDetailsFromNSECSV(StockHistoryRequest stockHistoryRequest) {
        return entryLoader.getStockHistoryDetails(stockHistoryRequest)
                .flatMap(stockHistories -> {
                    // Build TreeMap<LocalDate, StockHistoryDetails>
                    TreeMap<LocalDate, StockHistoryDetails> stockHistoryDetailsMap = new TreeMap<>(stockHistories.getStockHistoryDetails());
                    return Mono.justOrEmpty(StockHistory.builder()
                                    .key(StockHistoryKey.builder().key(stockHistories.getKey().getKey()).build())
                                    .stockHistoryDetails(stockHistoryDetailsMap)
                                    .build())
                            .doOnNext(details -> log.info("stock history data fetched. count: {}, details: {}, key: {} ", details.getStockHistoryDetails().size(), details, details.getKey()));
                });
    }

    /**
     * Fetches data for a large date range by splitting it into 3-month calendar chunks,
     * calling NSE NextApi sequentially for each chunk with a 3-second inter-call delay,
     * merging all results, and saving ONCE to Cassandra.
     *
     * This avoids sending a single massive request to NSE (e.g. 1050 calendar days)
     * which causes very large/unreliable responses.
     *
     * @param symbol      NSE stock symbol
     * @param series      e.g. "EQ"
     * @param from        start date (inclusive)
     * @param to          end date (inclusive)
     * @param chunkMonths calendar-month window per NSE call (recommended: 3)
     */
    public Mono<StockHistory> fetchChunkedFromNextApiAndSave(
            String symbol, String series, LocalDate from, LocalDate to, int chunkMonths) {

        List<Pair<LocalDate, LocalDate>> chunks = buildCalendarChunks(from, to, chunkMonths);
        log.info("[ChunkedFetch] {} — {} chunk(s) of {}m each | {} → {}",
                symbol, chunks.size(), chunkMonths, from, to);

        // Mutable map; safe here — single reactive chain, no concurrency
        TreeMap<LocalDate, StockHistoryDetails> merged = new TreeMap<>();

        return Flux.fromIterable(chunks)
                .concatMap(chunk -> {
                    String chunkFrom = chunk.getLeft().format(NSE_FMT);
                    String chunkTo   = chunk.getRight().format(NSE_FMT);
                    log.info("[ChunkedFetch] {} fetching chunk {} → {}", symbol, chunkFrom, chunkTo);

                    StockHistoryRequest req = StockHistoryRequest.builder()
                            .stockSymbol(symbol)
                            .series(series)
                            .from(chunkFrom)
                            .to(chunkTo)
                            .build();

                    return entryLoader.getStockHistoryFromNextApi(req)
                            .doOnNext(sh -> {
                                int count = sh.getStockHistoryDetails() != null
                                        ? sh.getStockHistoryDetails().size() : 0;
                                log.info("[ChunkedFetch] {} chunk {} → {} got {} records",
                                        symbol, chunkFrom, chunkTo, count);
                                if (sh.getStockHistoryDetails() != null) {
                                    merged.putAll(sh.getStockHistoryDetails());
                                }
                            })
                            .onErrorResume(err -> {
                                log.error("[ChunkedFetch] {} chunk {} → {} failed: {}",
                                        symbol, chunkFrom, chunkTo, err.getMessage());
                                return Mono.empty();
                            })
                            // 3-second polite delay between NSE calls
                            .delayElement(Duration.ofSeconds(3));
                })
                .collectList()
                .flatMap(ignored -> {
                    if (merged.isEmpty()) {
                        log.warn("[ChunkedFetch] {} — all chunks returned empty. Nothing to save.", symbol);
                        return Mono.empty();
                    }
                    log.info("[ChunkedFetch] {} — saving merged {} records to Cassandra", symbol, merged.size());
                    StockHistory full = StockHistory.builder()
                            .key(StockHistoryKey.builder().key(symbol).build())
                            .stockHistoryDetails(merged)
                            .build();
                    return stockHistoryDataService.save(full);
                });
    }

    /**
     * Splits the closed interval [from, to] into consecutive chunks of
     * {@code chunkMonths} calendar months. Returns empty list when from > to.
     */
    public static List<Pair<LocalDate, LocalDate>> buildCalendarChunks(
            LocalDate from, LocalDate to, int chunkMonths) {
        List<Pair<LocalDate, LocalDate>> chunks = new ArrayList<>();
        if (from.isAfter(to)) return chunks;
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            LocalDate end = cursor.plusMonths(chunkMonths).minusDays(1);
            if (end.isAfter(to)) end = to;
            chunks.add(Pair.of(cursor, end));
            cursor = end.plusDays(1);
        }
        return chunks;
    }
}
