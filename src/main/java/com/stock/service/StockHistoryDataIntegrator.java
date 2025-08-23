package com.stock.service;

import com.stock.dto.StockHistory;
import com.stock.dto.StockHistoryDetails;
import com.stock.dto.StockHistoryRequest;
import com.stock.dto.StockInfoDetails;
import com.stock.dto.key.StockHistoryKey;
import com.stock.util.WorkingDaysSlots;
import jnr.constants.platform.Local;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
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

    public Mono<StockHistory> save(StockHistoryRequest request) {
        if (request == null) {
            log.error("Provide Stock info details are wrong. Update Skipped, details: {} ", request );
            return Mono.empty();
        }
        /*List<StockHistoryRequest> requests = new ArrayList<>();
        int i = 4;
        LocalDate parse = convertToLocalDate(request.getTo());
        while (i > 0) {
            StringBuilder from = new StringBuilder();
            from.append(parse.minusMonths(2).getDayOfMonth())
                    .append("-")
                    .append(parse.minusMonths(2).getMonthValue() < 10 ? "0" + parse.minusMonths(2).getMonthValue() : parse.minusMonths(2).getMonthValue())
                    .append("-")
                    .append(parse.minusMonths(2).getYear());

            StringBuilder to = new StringBuilder();
            to.append(parse.getDayOfMonth())
                    .append("-")
                    .append(parse.getMonthValue() < 10 ? "0" + parse.getMonthValue() : parse.getMonthValue())
                    .append("-")
                    .append(parse.getYear());

            requests.add(StockHistoryRequest.builder()
                            .stockSymbol(request.getStockSymbol())
                            .series(request.getSeries())
                            .from(from.toString())
                            .to(to.toString())
                    .build());
            parse = convertToLocalDate(from.toString());
            i--;
        }*/

        List<TreeSet<String>> dateSlots = WorkingDaysSlots.getDateSlots(request.getTo(), request.getNumOfDays());
        List<StockHistoryRequest> stockHistoryRequest = dateSlots.stream().map(localDates -> StockHistoryRequest.builder()
                .stockSymbol(request.getStockSymbol())
                .series(request.getSeries())
                .from(localDates.first())
                .to(localDates.last())
                .build()).collect(Collectors.toList());

        return Flux.fromIterable(stockHistoryRequest)
                .flatMap(req -> entryLoader.getStockHistoryDetailsList(req)
                        .flatMapMany(Flux::fromIterable))
                .collect(Collectors.toList())
                .flatMap(stockHistories -> {
                    // Build TreeMap<LocalDate, StockHistoryDetails>
                    TreeMap<LocalDate, StockHistoryDetails> stockHistoryDetailsMap =
                            stockHistories.stream()
                                    .collect(Collectors.toMap(
                                            StockHistoryDetails::getHistoryDate,   // key mapper
                                            details -> details,                     // value mapper
                                            (existing, replacement) -> replacement, // merge function
                                            TreeMap::new                            // supplier
                                    ));
                    return Mono.justOrEmpty(StockHistory.builder()
                            .key(StockHistoryKey.builder().key(stockHistories.stream().findFirst().map(StockHistoryDetails::getStockName).get()).build())
                            .stockHistoryDetails(stockHistoryDetailsMap)
                            .build());
                })
                .flatMap(stockHistory -> stockHistoryDataService.save(stockHistory));
        /*return entryLoader.getStockHistoryDetails(request)
                .flatMap(stockHistory -> stockHistoryDataService.save(stockHistory));*/
    }

    private LocalDate convertToLocalDate(String dateStr) {
        // Define the formatter matching your string
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        // Parse the string to LocalDate
        LocalDate localDate = LocalDate.parse(dateStr, formatter);

        return localDate;
    }
}
