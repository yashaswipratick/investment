package com.stock.service;

import com.stock.dto.*;
import com.stock.dto.key.StockHistoryKey;
import com.stock.util.Utility;
import com.stock.util.WorkingDaysSlots;
import jnr.constants.platform.Local;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class StockHistoryDataIntegrator {

    @Autowired
    private StockHistoryDataService stockHistoryDataService;

    @Autowired
    private StockHistoryDataHttpEntryLoader entryLoader;

    @Autowired
    private SectorWiseStockDataIntegrator sectorWiseStockDataIntegrator;

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
                .flatMap(stockHistory -> stockHistoryDataService.save(stockHistory))
                .doOnNext(stockHistory -> log.info("NextApi: data saved for {}. records: {}",
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
}
