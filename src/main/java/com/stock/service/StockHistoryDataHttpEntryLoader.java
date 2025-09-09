package com.stock.service;

import com.stock.curl.CurlCommandGenerator;
import com.stock.dto.StockHistory;
import com.stock.dto.StockHistoryDetails;
import com.stock.dto.StockHistoryRequest;
import com.stock.dto.key.StockHistoryKey;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.resolver.DefaultAddressResolverGroup;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

@Slf4j
@Service
public class StockHistoryDataHttpEntryLoader {

    @Autowired
    private CurlCommandGenerator curlCommandGenerator;

    private static final String BASE_URL = "https://www.nseindia.com/";
    //private static final String URL = "https://archives.nseindia.com/content/equities/EQUITY_L.csv";
    //private static final String URL = "https://www.nseindia.com/api/quote-equity?symbol=INFY";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36";
    //private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    private static final String ACCEPT_LANGUAGE = "en-GB,en-US;q=0.9,en;q=0.8";
    private static final String ACCEPT_ENCODING = "gzip, deflate";

    public Mono<List<StockHistoryDetails>> getStockHistoryDetailsList(StockHistoryRequest request) {
        WebClient client = WebClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, ACCEPT_LANGUAGE)
                .defaultHeader(HttpHeaders.ACCEPT_ENCODING, ACCEPT_ENCODING)
                .defaultHeader(HttpHeaders.COOKIE, curlCommandGenerator.getCookie())
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create().resolver(DefaultAddressResolverGroup.INSTANCE).followRedirect(true)))
                .build();

        return fetchApiDataList(client, buildURL(request), request.getStockSymbol())
                .doOnError(e -> log.error("symbol -> {},  Error: {} ", request, e.getMessage()));
        // Establish session by making a GET request to the base URL
        /*return client.get()
                .retrieve()
                .toBodilessEntity()
                .flatMap(entity -> fetchApiDataList(client, buildURL(request), request.getStockSymbol())*/
                /*.onErrorResume(error -> {
                    log.info("⚠️ Stock history WebClient failed after retries. Trying curl fallback... Error: {}", error.getMessage());

                    String response = curlCommandGenerator.generateCurlCommandStockHistory(buildURL(request), request.getStockSymbol());
                    log.info("Stock History json fetched for url: {}, json: {} ", buildURL(request), response);
                    if (response != null && response.startsWith("{")) {
                        try {
                            List<StockHistoryDetails> stockHistoryDetails = StockHistoryDataHttpEntryLoader.convertResponseToDto(response);
                            return Mono.justOrEmpty(stockHistoryDetails);
                            *//*Map<LocalDate, StockHistoryDetails> stockHistoryDetailsMap = stockHistoryDetails.stream().map(details -> Pair.of(Pair.of(details.getHistoryDate(), details)))
                                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                            return Mono.justOrEmpty(StockHistory.builder()
                                    .key(StockHistoryKey.builder().key(stockHistoryDetails.stream().findFirst().get().getStockName()).build())
                                    .stockHistoryDetails(stockHistoryDetailsMap)
                                    .build());*//*
                        } catch (Exception e) {
                            log.error("Stock History Failed to parse JSON from curl: {}", e.getMessage());
                        }
                    }
                    return Mono.empty();
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("⚠️Stock History WebClient failed after retries. Trying curl fallback... url: {}", buildURL(request));

                    String response = curlCommandGenerator.generateCurlCommandStockHistory(buildURL(request), request.getStockSymbol());

                    log.info("Stock History json fetched for url: {}, json: {} ", buildURL(request), response);
                    if (response != null && response.startsWith("{")) {
                        try {
                            List<StockHistoryDetails> stockHistoryDetails = StockHistoryDataHttpEntryLoader.convertResponseToDto(response);
                            return Mono.justOrEmpty(stockHistoryDetails);
                            *//*Map<LocalDate, StockHistoryDetails> stockHistoryDetailsMap = stockHistoryDetails.stream().map(details -> Pair.of(Pair.of(details.getHistoryDate(), details)))
                                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                            return Mono.justOrEmpty(StockHistory.builder()
                                    .key(StockHistoryKey.builder().key(request.getStockSymbol()).build())
                                    .stockHistoryDetails(stockHistoryDetailsMap)
                                    .build());*//*
                        } catch (Exception e) {
                            log.error("Stock History Failed to parse JSON from curl: {}", e.getMessage());
                        }
                    }
                    return Mono.empty();
                }))*/
                //.doOnError(e -> log.error("symbol -> {},  Error: {} ", request, e.getMessage())));
    }

    public Mono<StockHistory> getStockHistoryDetails(StockHistoryRequest request) {

        HttpClient httpClient = HttpClient.create()
                .followRedirect(true)
                .compress(true) // handle gzip responses
                .doOnConnected(conn -> conn.addHandlerLast(new HttpObjectAggregator(50 * 1024 * 1024))); // 50 MB

        WebClient client = WebClient.builder()
                .baseUrl(BASE_URL)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, ACCEPT_LANGUAGE)
                .defaultHeader(HttpHeaders.ACCEPT_ENCODING, ACCEPT_ENCODING)
                .defaultHeader(HttpHeaders.COOKIE, curlCommandGenerator.getCookie())
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create().resolver(DefaultAddressResolverGroup.INSTANCE).followRedirect(true)))
                .build();

        // Establish session by making a GET request to the base URL
        return client.get()
                .retrieve()
                .toBodilessEntity()
                .flatMap(entity -> fetchApiData(client, buildURLForCSVResp(request), request.getStockSymbol())
                        .onErrorResume(error -> {
                            /*log.info("⚠️ Stock history WebClient failed after retries. Trying curl fallback... Error: {}", error.getMessage());

                            String response = curlCommandGenerator.generateCurlCommandStockHistory(buildURL(request), request.getStockSymbol());
                            log.info("Stock History json fetched for url: {}, json: {} ", buildURL(request), response);
                            if (response != null && response.startsWith("{")) {
                                try {
                                    List<StockHistoryDetails> stockHistoryDetails = StockHistoryDataHttpEntryLoader.convertResponseToDto(response);
                            TreeMap<LocalDate, StockHistoryDetails> stockHistoryDetailsMap = stockHistoryDetails.stream()
                                    .collect(Collectors.toMap(
                                            StockHistoryDetails::getHistoryDate,   // key mapper
                                            details -> details,                     // value mapper
                                            (existing, replacement) -> replacement, // merge function
                                            TreeMap::new                            // supplier
                                    ));
                            return Mono.justOrEmpty(StockHistory.builder()
                                    .key(StockHistoryKey.builder().key(stockHistoryDetails.stream().findFirst().get().getStockName()).build())
                                    .stockHistoryDetails(stockHistoryDetailsMap)
                                    .build());
                                } catch (Exception e) {
                                    log.error("Stock History Failed to parse JSON from curl: {}", e.getMessage());
                                }
                            }*/
                            return Mono.empty();
                        })
                        .switchIfEmpty(Mono.defer(() -> {
                            /*log.info("⚠️Stock History WebClient failed after retries. Trying curl fallback... url: {}", buildURL(request));

                            String response = curlCommandGenerator.generateCurlCommandStockHistory(buildURL(request), request.getStockSymbol());

                            log.info("Stock History json fetched for url: {}, json: {} ", buildURL(request), response);
                            if (response != null && response.startsWith("{")) {
                                try {
                                    List<StockHistoryDetails> stockHistoryDetails = StockHistoryDataHttpEntryLoader.convertResponseToDto(response);
                                    TreeMap<LocalDate, StockHistoryDetails> stockHistoryDetailsMap = stockHistoryDetails.stream()
                                            .collect(Collectors.toMap(
                                                    StockHistoryDetails::getHistoryDate,   // key mapper
                                                    details -> details,                     // value mapper
                                                    (existing, replacement) -> replacement, // merge function
                                                    TreeMap::new                            // supplier
                                            ));
                                    return Mono.justOrEmpty(StockHistory.builder()
                                            .key(StockHistoryKey.builder().key(stockHistoryDetails.stream().findFirst().get().getStockName()).build())
                                            .stockHistoryDetails(stockHistoryDetailsMap)
                                            .build());
                                } catch (Exception e) {
                                    log.error("Stock History Failed to parse JSON from curl: {}", e.getMessage());
                                }
                            }*/
                            return Mono.empty();
                        }))
                        .doOnError(e -> log.error("symbol -> {},  Error: {} ", request, e.getMessage())));
    }

    private Mono<StockHistory> fetchApiData(WebClient client, String url, String stockSymbol) {
        // Print the raw response

        // Define a predicate to check for 401 status
        Predicate<Throwable> isRetryableError = throwable ->
                throwable instanceof WebClientResponseException &&
                        (((WebClientResponseException) throwable).getStatusCode().value() == 401 ||
                                ((WebClientResponseException) throwable).getStatusCode().value() == 403 );

        return client.get()
                .uri(url)
                .retrieve()
                .onStatus(status -> status.value() != 200, ClientResponse::createException)
                .bodyToMono(byte[].class)
                .map(StockHistoryDataHttpEntryLoader::decompressGzip)
                .map(String::new)
                .map(s -> convertCSVResponseToDto(s, stockSymbol))
                .flatMap(stockHistoryDetails -> Flux.fromIterable(stockHistoryDetails)
                        .flatMap(details -> Mono.justOrEmpty(Pair.of(details.getHistoryDate(), details)))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                        .flatMap(history -> Mono.justOrEmpty(StockHistory.builder()
                                        .key(StockHistoryKey.builder().key(history.values().stream().findFirst().get().getStockName()).build())
                                        .stockHistoryDetails(new TreeMap<>(history))
                                .build())))
                .doOnNext(stockHistoryDetails -> log.info(" Fetched Stock History NSE details. details: {} ", stockHistoryDetails))
                .switchIfEmpty(Mono.defer(() -> {
                    /*log.info("⚠️ Stock History WebClient failed after retries. Trying curl fallback... url: {}", url);

                    String response = curlCommandGenerator.generateCurlCommandStockHistory(url, stockSymbol);
                    if (response != null && response.startsWith("{")) {
                        try {
                            List<StockHistoryDetails> stockHistoryDetails = StockHistoryDataHttpEntryLoader.convertResponseToDto(response);
                            TreeMap<LocalDate, StockHistoryDetails> stockHistoryDetailsMap = stockHistoryDetails.stream()
                                    .collect(Collectors.toMap(
                                            StockHistoryDetails::getHistoryDate,   // key mapper
                                            details -> details,                     // value mapper
                                            (existing, replacement) -> replacement, // merge function
                                            TreeMap::new                            // supplier
                                    ));
                            return Mono.justOrEmpty(StockHistory.builder()
                                    .key(StockHistoryKey.builder().key(stockHistoryDetails.stream().findFirst().get().getStockName()).build())
                                    .stockHistoryDetails(stockHistoryDetailsMap)
                                    .build());
                        } catch (Exception e) {
                            log.error("Stock History Failed to parse JSON from curl: {}", e.getMessage());
                        }
                    }*/
                    return Mono.empty();
                }))
                .retryWhen(Retry.from(retrySignals ->
                        retrySignals
                                .flatMap(retrySignal -> {
                                    if (isRetryableError.test(retrySignal.failure())) {
                                        return Mono.just(retrySignal);
                                    }
                                    /*log.info("⚠️ Stock History WebClient failed after retries. Trying curl fallback... url: {}", url);

                                    String response = curlCommandGenerator.generateCurlCommandStockHistory(url, stockSymbol);
                                    if (response != null && response.startsWith("{")) {
                                        try {
                                            List<StockHistoryDetails> stockHistoryDetails = StockHistoryDataHttpEntryLoader.convertResponseToDto(response);
                                            return Mono.justOrEmpty(stockHistoryDetails);
                                            *//*Map<LocalDate, StockHistoryDetails> stockHistoryDetailsMap = stockHistoryDetails.stream().map(details -> Pair.of(Pair.of(details.getHistoryDate(), details)))
                                                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                                            return Mono.justOrEmpty(StockHistory.builder()
                                                    .key(StockHistoryKey.builder().key(stockHistoryDetails.stream().findFirst().get().getStockName()).build())
                                                    .stockHistoryDetails(stockHistoryDetailsMap)
                                                    .build());*//*
                                        } catch (Exception e) {
                                            log.error("Stock History Failed to parse JSON from curl: {}", e.getMessage());
                                        }
                                    }*/
                                    return Mono.empty();
                                })
                                .delayElements(Duration.ofSeconds(2))// Delay between retries
                                .take(3) // Retry up to 3 times
                                .doOnNext(retrySignal -> log.info("Starting retry logic..."))
                ))
                .doOnError(e -> log.error("Stock History Failed to fetch API data for symbol: {} after retries: {} ", url, e.getMessage()))
                .onErrorResume(error -> {
                    /*log.info("⚠️ Stock History WebClient failed after retries. Trying curl fallback... Error: {}", error.getMessage());

                    String response = curlCommandGenerator.generateCurlCommandStockHistory(url, stockSymbol);
                    if (response != null && response.startsWith("{")) {
                        try {
                            List<StockHistoryDetails> stockHistoryDetails = StockHistoryDataHttpEntryLoader.convertResponseToDto(response);
                            TreeMap<LocalDate, StockHistoryDetails> stockHistoryDetailsMap = stockHistoryDetails.stream()
                                    .collect(Collectors.toMap(
                                            StockHistoryDetails::getHistoryDate,   // key mapper
                                            details -> details,                     // value mapper
                                            (existing, replacement) -> replacement, // merge function
                                            TreeMap::new                            // supplier
                                    ));
                            return Mono.justOrEmpty(StockHistory.builder()
                                    .key(StockHistoryKey.builder().key(stockHistoryDetails.stream().findFirst().get().getStockName()).build())
                                    .stockHistoryDetails(stockHistoryDetailsMap)
                                    .build());
                        } catch (Exception e) {
                            log.error("Stock History Failed to parse JSON from curl: {}", e.getMessage());
                        }
                    }*/
                    return Mono.empty();
                });
    }

    private Mono<List<StockHistoryDetails>> fetchApiDataList(WebClient client, String url, String stockSymbol) {
        // Print the raw response

        // Define a predicate to check for 401 status
        Predicate<Throwable> isRetryableError = throwable ->
                throwable instanceof WebClientResponseException &&
                        (((WebClientResponseException) throwable).getStatusCode().value() == 401 ||
                                ((WebClientResponseException) throwable).getStatusCode().value() == 403 );

        return client.get()
                .uri(url)
                .retrieve()
                .onStatus(status -> status.value() != 200, ClientResponse::createException)
                .bodyToMono(byte[].class)
                .map(StockHistoryDataHttpEntryLoader::decompressGzip)
                .map(String::new)
                .map(response -> {
                    List<StockHistoryDetails> details = StockHistoryDataHttpEntryLoader.convertResponseToDto(response);
                    log.info("Fetched {} records from NSE", details.size());
                    return details;
                })
                /*.flatMap(stockHistoryDetails -> Flux.fromIterable(stockHistoryDetails)
                        .flatMap(details -> Mono.justOrEmpty(Pair.of(details.getHistoryDate(), details)))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                        .flatMap(history -> Mono.justOrEmpty(StockHistory.builder()
                                        .key(StockHistoryKey.builder().key(history.values().stream().findFirst().get().getStockName()).build())
                                        .stockHistoryDetails(history)
                                .build())))*/
                .doOnNext(stockHistoryDetails -> log.info(" Fetched Stock History NSE details. details: {} ", stockHistoryDetails))
                .switchIfEmpty(Mono.defer(() -> {
//                    log.info("⚠️ Stock History WebClient failed after retries. Trying curl fallback... url: {}", url);
//
//                    String response = curlCommandGenerator.generateCurlCommandStockHistory(url, stockSymbol);
//                    if (response != null && response.startsWith("{")) {
//                        try {
//                            List<StockHistoryDetails> stockHistoryDetails = StockHistoryDataHttpEntryLoader.convertResponseToDto(response);
//                            return Mono.justOrEmpty(stockHistoryDetails);
//                            /*Map<LocalDate, StockHistoryDetails> stockHistoryDetailsMap = stockHistoryDetails.stream().map(details -> Pair.of(Pair.of(details.getHistoryDate(), details)))
//                                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
//                            return Mono.justOrEmpty(StockHistory.builder()
//                                    .key(StockHistoryKey.builder().key(stockHistoryDetails.stream().findFirst().get().getStockName()).build())
//                                    .stockHistoryDetails(stockHistoryDetailsMap)
//                                    .build());*/
//                        } catch (Exception e) {
//                            log.error("Stock History Failed to parse JSON from curl: {}", e.getMessage());
//                        }
//                    }
                    return Mono.empty();
                }))
                .retryWhen(Retry.from(retrySignals ->
                        retrySignals
                                .flatMap(retrySignal -> {
                                    if (isRetryableError.test(retrySignal.failure())) {
                                        return Mono.just(retrySignal);
                                    }
                                    /*log.info("⚠️ Stock History WebClient failed after retries. Trying curl fallback... url: {}", url);

                                    String response = curlCommandGenerator.generateCurlCommandStockHistory(url, stockSymbol);
                                    if (response != null && response.startsWith("{")) {
                                        try {
                                            List<StockHistoryDetails> stockHistoryDetails = StockHistoryDataHttpEntryLoader.convertResponseToDto(response);
                                            return Mono.justOrEmpty(stockHistoryDetails);
                                            *//*Map<LocalDate, StockHistoryDetails> stockHistoryDetailsMap = stockHistoryDetails.stream().map(details -> Pair.of(Pair.of(details.getHistoryDate(), details)))
                                                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                                            return Mono.justOrEmpty(StockHistory.builder()
                                                    .key(StockHistoryKey.builder().key(stockHistoryDetails.stream().findFirst().get().getStockName()).build())
                                                    .stockHistoryDetails(stockHistoryDetailsMap)
                                                    .build());*//*
                                        } catch (Exception e) {
                                            log.error("Stock History Failed to parse JSON from curl: {}", e.getMessage());
                                        }
                                    }*/
                                    return Mono.empty();
                                })
                                .delayElements(Duration.ofSeconds(2))// Delay between retries
                                .take(3) // Retry up to 3 times
                                .doOnNext(retrySignal -> log.info("Starting retry logic..."))
                ))
                .doOnError(e -> log.error("Stock History Failed to fetch API data for symbol: {} after retries: {} ", url, e.getMessage()))
                .onErrorResume(error -> {
                    /*log.info("⚠️ Stock History WebClient failed after retries. Trying curl fallback... Error: {}", error.getMessage());

                    String response = curlCommandGenerator.generateCurlCommandStockHistory(url, stockSymbol);
                    if (response != null && response.startsWith("{")) {
                        try {
                            List<StockHistoryDetails> stockHistoryDetails = StockHistoryDataHttpEntryLoader.convertResponseToDto(response);
                            return Mono.justOrEmpty(stockHistoryDetails);
                            *//*Map<LocalDate, StockHistoryDetails> stockHistoryDetailsMap = stockHistoryDetails.stream().map(details -> Pair.of(Pair.of(details.getHistoryDate(), details)))
                                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                            return Mono.justOrEmpty(StockHistory.builder()
                                    .key(StockHistoryKey.builder().key(stockHistoryDetails.stream().findFirst().get().getStockName()).build())
                                    .stockHistoryDetails(stockHistoryDetailsMap)
                                    .build());*//*
                        } catch (Exception e) {
                            log.error("Stock History Failed to parse JSON from curl: {}", e.getMessage());
                        }
                    }*/
                    return Mono.empty();
                });
    }

    private static byte[] decompressGzip(byte[] compressed) {
        try (GZIPInputStream gis = new GZIPInputStream(new java.io.ByteArrayInputStream(compressed));
             BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(gis, StandardCharsets.UTF_8))) {
            return bufferedReader.lines().collect(Collectors.joining("\n")).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Stock History Failed to decompress GZIP", e);
        }
    }


    private static List<StockHistoryDetails> convertResponseToDto(String jsonResponse) {
        //String jsonResponse = "{\"info\":{\"symbol\":\"RELIANCE\",\"companyName\":\"Reliance Industries Limited\",\"industry\":\"REFINERIES\",\"activeSeries\":[\"EQ\"],\"debtSeries\":[],\"isFNOSec\":true,\"isCASec\":false,\"isSLBSec\":true,\"isDebtSec\":false,\"isSuspended\":false,\"tempSuspendedSeries\":[],\"isETFSec\":false,\"isDelisted\":false,\"isin\":\"INE002A01018\",\"isMunicipalBond\":false,\"isTop10\":false,\"identifier\":\"RELIANCEEQN\"},\"metadata\":{\"series\":\"EQ\",\"symbol\":\"RELIANCE\",\"isin\":\"INE002A01018\",\"status\":\"Listed\",\"listingDate\":\"29-Nov-1995\",\"industry\":\"Refineries & Marketing\",\"lastUpdateTime\":\"26-Jun-2024 10:56:52\",\"pdSectorPe\":25.02,\"pdSymbolPe\":25.02,\"pdSectorInd\":\"NIFTY 500                                         \"},\"securityInfo\":{\"boardStatus\":\"Main\",\"tradingStatus\":\"Active\",\"tradingSegment\":\"Normal Market\",\"sessionNo\":\"-\",\"slb\":\"Yes\",\"classOfShare\":\"Equity\",\"derivatives\":\"Yes\",\"surveillance\":{\"surv\":null,\"desc\":null},\"faceValue\":10,\"issuedSize\":6765813926},\"sddDetails\":{\"SDDAuditor\":\"-\",\"SDDStatus\":\"-\"},\"priceInfo\":{\"lastPrice\":2947.7,\"change\":39.399999999999636,\"pChange\":1.3547433208403408,\"previousClose\":2908.3,\"open\":2892.1,\"close\":0,\"vwap\":2925.97,\"lowerCP\":\"2617.50\",\"upperCP\":\"3199.10\",\"pPriceBand\":\"No Band\",\"basePrice\":2908.3,\"intraDayHighLow\":{\"min\":2890.25,\"max\":2949.9,\"value\":2947.7},\"weekHighLow\":{\"min\":2220.3,\"minDate\":\"26-Oct-2023\",\"max\":3029,\"maxDate\":\"03-Jun-2024\",\"value\":2947.7},\"iNavValue\":null,\"checkINAV\":false},\"industryInfo\":{\"macro\":\"Energy\",\"sector\":\"Oil Gas & Consumable Fuels\",\"industry\":\"Petroleum Products\",\"basicIndustry\":\"Refineries & Marketing\"},\"preOpenMarket\":{\"preopen\":[{\"price\":2617.5,\"buyQty\":0,\"sellQty\":25},{\"price\":2618,\"buyQty\":0,\"sellQty\":15},{\"price\":2680,\"buyQty\":0,\"sellQty\":66},{\"price\":2762.9,\"buyQty\":0,\"sellQty\":5106},{\"price\":2892.1,\"buyQty\":0,\"sellQty\":0,\"iep\":true},{\"price\":3053.7,\"buyQty\":3578,\"sellQty\":0},{\"price\":3080,\"buyQty\":3,\"sellQty\":0},{\"price\":3100,\"buyQty\":1250,\"sellQty\":0},{\"price\":3199.1,\"buyQty\":401,\"sellQty\":0}],\"ato\":{\"buy\":6022,\"sell\":12452},\"IEP\":2892.1,\"totalTradedVolume\":119958,\"finalPrice\":2892.1,\"finalQuantity\":119958,\"lastUpdateTime\":\"26-Jun-2024 09:07:47\",\"totalBuyQuantity\":63088,\"totalSellQuantity\":131612,\"atoBuyQty\":6022,\"atoSellQty\":12452,\"Change\":-16.200000000000273,\"perChange\":-0.5570264415638095,\"prevClose\":2908.3}}"; // Replace with your actual JSON string


        try {
            List<StockHistoryDetails> stockHistoryDetails = new ArrayList<>();
            log.error("Json response. resp: {}", jsonResponse);
            JSONObject object = new JSONObject(jsonResponse);
            JSONArray data = object.getJSONArray("data");
            for (int i = 0; i < data.length(); i++) {
                JSONObject indexedData = (JSONObject)data.get(i);
                stockHistoryDetails.add(StockHistoryDetails.builder()
                        .series(indexedData.getString("CH_SERIES"))
                        .stockName(indexedData.getString("CH_SYMBOL"))
                        .historyDate(LocalDate.parse(indexedData.getString("CH_TIMESTAMP")))
                        .high(Double.parseDouble(String.valueOf(indexedData.getBigDecimal("CH_TRADE_HIGH_PRICE"))))
                        .low(Double.parseDouble(String.valueOf(indexedData.getBigDecimal("CH_TRADE_LOW_PRICE"))))
                        .open(Double.parseDouble(String.valueOf(indexedData.getBigDecimal("CH_OPENING_PRICE"))))
                        .close(Double.parseDouble(String.valueOf(indexedData.getBigDecimal("CH_CLOSING_PRICE"))))
                        .ltp(Double.parseDouble(String.valueOf(indexedData.getBigDecimal("CH_LAST_TRADED_PRICE"))))
                        .pevClose(Double.parseDouble(String.valueOf(indexedData.getBigDecimal("CH_PREVIOUS_CLS_PRICE"))))
                        .fiftyTwoWeekHigh(Double.parseDouble(String.valueOf(indexedData.getBigDecimal("CH_52WEEK_HIGH_PRICE"))))
                        .fiftyTwoWeekLow(Double.parseDouble(String.valueOf(indexedData.getBigDecimal("CH_52WEEK_LOW_PRICE"))))
                        .totalTrades(String.valueOf(indexedData.getBigDecimal("CH_TOTAL_TRADES")))
                        .volume(String.valueOf(indexedData.getBigDecimal("CH_TOT_TRADED_QTY")))
                        .value(String.valueOf(indexedData.getBigDecimal("CH_TOT_TRADED_VAL")))
                        .isin(indexedData.getString("CH_ISIN"))
                        .build());
            }
            return stockHistoryDetails;
        } catch (Exception e) {
            log.error("Error while converting stock history response to list. ", e);
            return new ArrayList<>();
        }
    }
        /*ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        JsonNode root = null;
        try {
            root = mapper.readTree(jsonResponse);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        JsonNode data = root.get("data");

        List<StockHistoryDetails> stockHistoryDetails = new ArrayList<>();
        for (JsonNode node : data) {
            StockHistoryDetails details = StockHistoryDetails.builder()
                    .series(node.path("CH_SERIES").asText(""))
                    .stockName(node.path("CH_SYMBOL").asText(""))
                    .historyDate(LocalDate.parse(node.path("CH_TIMESTAMP").asText()))
                    .high(node.path("CH_TRADE_HIGH_PRICE").asDouble(0.0))
                    .low(node.path("CH_TRADE_LOW_PRICE").asDouble(0.0))
                    .open(node.path("CH_OPENING_PRICE").asDouble(0.0))
                    .close(node.path("CH_CLOSING_PRICE").asDouble(0.0))
                    .ltp(node.path("CH_LAST_TRADED_PRICE").asDouble(0.0))
                    .pevClose(node.path("CH_PREVIOUS_CLS_PRICE").asDouble(0.0))
                    .fiftyTwoWeekHigh(node.path("CH_52WEEK_HIGH_PRICE").asDouble(0.0))
                    .fiftyTwoWeekLow(node.path("CH_52WEEK_LOW_PRICE").asDouble(0.0))
                    .totalTrades(node.path("CH_TOTAL_TRADES").asText("0"))
                    .volume(node.path("CH_TOT_TRADED_QTY").asText("0"))
                    .value(node.path("CH_TOT_TRADED_VAL").asText("0"))
                    .isin(node.path("CH_ISIN").asText(""))
                    .build();

            stockHistoryDetails.add(details);
        }
        return stockHistoryDetails;*/

    private String buildURL(StockHistoryRequest request) {
        StringBuilder URL = new StringBuilder("https://www.nseindia.com/api/historical/cm/equity?");
        String url = URL
                .append("symbol")
                .append("=")
                .append(request.getStockSymbol())
                .append("&")
                .append("series")
                .append("=")
                .append("[")
                .append("\"")
                .append(request.getSeries())
                .append("\"")
                .append("]")
                .append("&")
                .append("from")
                .append("=")
                .append(request.getFrom())
                .append("&")
                .append("to")
                .append("=")
                .append(request.getTo())
                .toString();
        log.error("Stock history build url. url: {}", url);
        return url;
    }

    private String buildURLForCSVResp(StockHistoryRequest request) {
        StringBuilder URL = new StringBuilder("https://www.nseindia.com/api/historical/cm/equity?");
        String url = URL
                .append("symbol")
                .append("=")
                .append(request.getStockSymbol())
                .append("&")
                .append("series")
                .append("=")
                .append("[")
                .append("\"")
                .append(request.getSeries())
                .append("\"")
                .append("]")
                .append("&")
                .append("from")
                .append("=")
                .append(request.getFrom())
                .append("&")
                .append("to")
                .append("=")
                .append(request.getTo())
                .append("&")
                .append("csv")
                .append("=")
                .append("true")
                .toString();
        log.error("Stock history build url. url: {}", url);
        return url;
    }


    private static List<StockHistoryDetails> convertCSVResponseToDto(String csvResponse, String stockName) {
        List<StockHistoryDetails> stockHistoryDetails = new ArrayList<>();
        try {
            log.info("CSV response: {}", csvResponse);

            // Split lines
            String[] lines = csvResponse.split("\n");

            if (lines.length <= 1) {
                log.warn("No stock history data found in CSV response");
                return stockHistoryDetails;
            }

            // First line is header, skip it
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;

                // Remove quotes and split by comma
                //String[] values = line.replaceAll("\"", "").split("\",\"", -1);
                String[] values = line.split("\",\"", -1);


                // Defensive: ensure correct number of columns
                if (values.length < 14) {
                    log.warn("Skipping invalid CSV line: {}", line);
                    continue;
                }

                // Map CSV → DTO
                StockHistoryDetails details = StockHistoryDetails.builder()
                        .historyDate(LocalDate.parse(values[0].replaceAll("\"", "").trim(), DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)))
                        .series(values[1].trim())
                        .open(parseDouble(values[2]))
                        .high(parseDouble(values[3]))
                        .low(parseDouble(values[4]))
                        .pevClose(parseDouble(values[5]))
                        .ltp(parseDouble(values[6]))
                        .close(parseDouble(values[7]))
                        .vwap(parseDouble(values[8]))
                        .fiftyTwoWeekHigh(parseDouble(values[9]))
                        .fiftyTwoWeekLow(parseDouble(values[10]))
                        .volume(values[11].trim())
                        .value(values[12].trim())
                        .totalTrades(values[13].replaceAll("\"", "").trim())
                        .stockName(stockName)  // NSE CSV doesn’t provide symbol, you can inject from method param
                        .isin("NA")            // CSV doesn’t have ISIN
                        .build();

                stockHistoryDetails.add(details);
            }
        } catch (Exception e) {
            log.error("Error while converting CSV stock history to DTO list", e);
        }
        return stockHistoryDetails;
    }

    // Helper to safely parse double
    private static Double parseDouble(String value) {
        try {
            return Double.parseDouble(value.replace(",", "").trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

}
