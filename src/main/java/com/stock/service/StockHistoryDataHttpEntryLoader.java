package com.stock.service;

import com.stock.dto.StockHistory;
import com.stock.dto.StockHistoryDetails;
import com.stock.dto.StockHistoryRequest;
import com.stock.dto.key.StockHistoryKey;
import io.netty.handler.codec.http.HttpObjectAggregator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONArray;
import org.json.JSONObject;
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

    private final NseSessionManager nseSessionManager;

    public StockHistoryDataHttpEntryLoader(NseSessionManager nseSessionManager) {
        this.nseSessionManager = nseSessionManager;
    }

    private static final String BASE_URL = "https://www.nseindia.com/";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36";
    private static final String ACCEPT_LANGUAGE = "en-GB,en-US;q=0.9,en;q=0.8";
    private static final String ACCEPT_ENCODING = "gzip, deflate";

    private WebClient buildWebClient(String cookieHeader, boolean largePayload) {
        HttpClient httpClient = HttpClient.create().followRedirect(true);


        if (largePayload) {
            httpClient = httpClient
                    .compress(true)
                    .doOnConnected(conn -> conn.addHandlerLast(new HttpObjectAggregator(50 * 1024 * 1024))); // 50 MB
        }

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, ACCEPT_LANGUAGE)
                .defaultHeader(HttpHeaders.ACCEPT_ENCODING, ACCEPT_ENCODING)
                .clientConnector(new ReactorClientHttpConnector(httpClient));

        if (cookieHeader != null && !cookieHeader.isBlank()) {
            builder.defaultHeader(HttpHeaders.COOKIE, cookieHeader);
        }

        return builder.build();
    }


    private boolean isInvalidRequest(StockHistoryRequest request) {
        return request == null
                || request.getStockSymbol() == null
                || request.getStockSymbol().isBlank()
                || request.getSeries() == null
                || request.getSeries().isBlank()
                || request.getFrom() == null
                || request.getFrom().isBlank()
                || request.getTo() == null
                || request.getTo().isBlank();
    }


    public Mono<List<StockHistoryDetails>> getStockHistoryDetailsList(StockHistoryRequest request) {
        if (isInvalidRequest(request)) {
            log.warn("Invalid stock history request for list flow: {}", request);
            return Mono.empty();
        }

        // Generate fresh NSE session cookie from warm-up call (no file needed)
        return nseSessionManager.generateFreshSessionCookie()
                .map(cookie -> buildWebClient(cookie, false))
                .flatMap(client -> fetchApiDataList(client, buildURL(request)))
                .doOnError(e -> log.error("symbol -> {},  Error: {} ", request, e.getMessage()));
    }

    public Mono<StockHistory> getStockHistoryDetails(StockHistoryRequest request) {
        if (isInvalidRequest(request)) {
            log.warn("Invalid stock history request for CSV flow: {}", request);
            return Mono.empty();
        }

        // Generate fresh NSE session cookie from warm-up call (no file needed)
        return nseSessionManager.generateFreshSessionCookie()
                .map(cookie -> buildWebClient(cookie, true))
                .flatMap(client -> fetchApiData(client, buildURLForCSVResp(request), request.getStockSymbol()));
    }

    private Mono<StockHistory> fetchApiData(WebClient client, String url, String stockSymbol) {
        // Print the raw response

        // Define a predicate to check for 401 status
        Predicate<Throwable> isRetryableError = throwable ->
                throwable instanceof WebClientResponseException webClientResponseException &&
                        (webClientResponseException.getStatusCode().value() == 401 ||
                                webClientResponseException.getStatusCode().value() == 403);

        return client.get()
                .uri(url)
                .retrieve()
                .onStatus(status -> status.value() != 200, ClientResponse::createException)
                .bodyToMono(byte[].class)
                .map(StockHistoryDataHttpEntryLoader::decodeResponse)
                .map(String::new)
                .map(s -> convertNextApiResponseToDto(s, stockSymbol))
                .flatMap(stockHistoryDetails -> {
                    if (stockHistoryDetails.isEmpty()) {
                        log.warn("No stock history details returned from API for symbol: {}", stockSymbol);
                        return Mono.empty();
                    }
                    return Flux.fromIterable(stockHistoryDetails)
                            .flatMap(details -> Mono.justOrEmpty(Pair.of(details.getHistoryDate(), details)))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                            .flatMap(history -> {
                                if (history.isEmpty()) {
                                    log.warn("History map is empty after collection for symbol: {}", stockSymbol);
                                    return Mono.empty();
                                }
                                String stockName = stockHistoryDetails.get(0).getStockName();
                                return Mono.justOrEmpty(StockHistory.builder()
                                        .key(StockHistoryKey.builder().key(stockName).build())
                                        .stockHistoryDetails(new TreeMap<>(history))
                                        .build());
                            });
                })
                .doOnNext(stockHistoryDetails -> log.info(" Fetched Stock History NSE details. details: {} ", stockHistoryDetails))
                .switchIfEmpty(Mono.empty())
                .retryWhen(Retry.from(retrySignals ->
                        retrySignals
                                .flatMap(retrySignal -> {
                                    if (isRetryableError.test(retrySignal.failure())) {
                                        return Mono.just(retrySignal);
                                    }
                                    return Mono.empty();
                                })
                                .delayElements(Duration.ofSeconds(2))// Delay between retries
                                .take(3) // Retry up to 3 times
                                .doOnNext(retrySignal -> log.info("Starting retry logic..."))
                ))
                .doOnError(e -> log.error("Stock History Failed to fetch API data for symbol: {} after retries: {} ", url, e.getMessage()))
                .onErrorResume(error -> Mono.empty());
    }

    private static List<StockHistoryDetails> convertNextApiResponseToDto(String response, String stockName) {
        if (response == null || response.isBlank()) {
            log.warn("NextApi payload is empty for symbol: {}", stockName);
            return new ArrayList<>();
        }
        String trimmed = response.trim();
        String payloadType = trimmed.startsWith("[") ? "json-array" : "csv";
        log.info("NextApi payload detected for {}: {}", stockName, payloadType);
        if (trimmed.startsWith("[")) {
            return convertNextApiJsonArrayToDto(trimmed, stockName);
        }
        return convertCSVResponseToDto(trimmed, stockName);
    }

    private static List<StockHistoryDetails> convertNextApiJsonArrayToDto(String jsonArrayResponse, String stockName) {
        List<StockHistoryDetails> stockHistoryDetails = new ArrayList<>();
        try {
            JSONArray data = new JSONArray(jsonArrayResponse);
            if (data.isEmpty()) {
                log.warn("No stock history data found in NextApi JSON array response");
                return stockHistoryDetails;
            }

            for (int i = 0; i < data.length(); i++) {
                JSONObject row = data.getJSONObject(i);
                String symbolFromRow = row.optString("chSymbol", stockName);
                String timestamp = row.optString("mtimestamp", "");
                if (timestamp.isBlank()) {
                    continue;
                }

                StockHistoryDetails details = StockHistoryDetails.builder()
                        .historyDate(LocalDate.parse(timestamp, DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)))
                        .series(row.optString("chSeries", ""))
                        .open(row.optDouble("chOpeningPrice", 0.0))
                        .high(row.optDouble("chTradeHighPrice", 0.0))
                        .low(row.optDouble("chTradeLowPrice", 0.0))
                        .prevClose(row.optDouble("chPreviousClsPrice", 0.0))
                        .ltp(row.optDouble("chLastTradedPrice", 0.0))
                        .close(row.optDouble("chClosingPrice", 0.0))
                        .vwap(row.optDouble("vwap", 0.0))
                        .fiftyTwoWeekHigh(row.optDouble("ch52WeekHighPrice", 0.0))
                        .fiftyTwoWeekLow(row.optDouble("ch52WeekLowPrice", 0.0))
                        .volume(String.valueOf(row.opt("chTotTradedQty")))
                        .value(String.valueOf(row.opt("chTotTradedVal")))
                        .totalTrades(String.valueOf(row.opt("chTotalTrades")))
                        .stockName(symbolFromRow)
                        .isin("NA")
                        .build();
                stockHistoryDetails.add(details);
            }
            log.info("Parsed {} stock history records from NextApi JSON response", stockHistoryDetails.size());
            return stockHistoryDetails;
        } catch (Exception e) {
            log.error("Error while converting NextApi JSON array response to DTO list", e);
            return new ArrayList<>();
        }
    }

    private Mono<List<StockHistoryDetails>> fetchApiDataList(WebClient client, String url) {
        // Print the raw response

        // Define a predicate to check for 401 status
        Predicate<Throwable> isRetryableError = throwable ->
                throwable instanceof WebClientResponseException webClientResponseException &&
                        (webClientResponseException.getStatusCode().value() == 401 ||
                                webClientResponseException.getStatusCode().value() == 403);

        return client.get()
                .uri(url)
                .retrieve()
                .onStatus(status -> status.value() != 200, ClientResponse::createException)
                .bodyToMono(byte[].class)
                .map(StockHistoryDataHttpEntryLoader::decodeResponse)
                .map(String::new)
                .map(response -> {
                    List<StockHistoryDetails> details = StockHistoryDataHttpEntryLoader.convertResponseToDto(response);
                    log.info("Fetched {} records from NSE", details.size());
                    return details;
                })
                .doOnNext(stockHistoryDetails -> log.info(" Fetched Stock History NSE details. details: {} ", stockHistoryDetails))
                .switchIfEmpty(Mono.empty())
                .retryWhen(Retry.from(retrySignals ->
                        retrySignals
                                .flatMap(retrySignal -> {
                                    if (isRetryableError.test(retrySignal.failure())) {
                                        return Mono.just(retrySignal);
                                    }
                                    return Mono.empty();
                                })
                                .delayElements(Duration.ofSeconds(2))// Delay between retries
                                .take(3) // Retry up to 3 times
                                .doOnNext(retrySignal -> log.info("Starting retry logic..."))
                ))
                .doOnError(e -> log.error("Stock History Failed to fetch API data for symbol: {} after retries: {} ", url, e.getMessage()))
                .onErrorResume(error -> Mono.empty());
    }

    private static byte[] decompressGzip(byte[] compressed) {
        try (GZIPInputStream gis = new GZIPInputStream(new java.io.ByteArrayInputStream(compressed));
             BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(gis, StandardCharsets.UTF_8))) {
            return bufferedReader.lines().collect(Collectors.joining("\n")).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Stock History Failed to decompress GZIP", e);
        }
    }

    private static byte[] decodeResponse(byte[] responseBytes) {
        try {
            return decompressGzip(responseBytes);
        } catch (RuntimeException ignored) {
            // Response can be plain text/JSON/CSV when upstream already decompressed it.
            return responseBytes;
        }
    }


    private static List<StockHistoryDetails> convertResponseToDto(String jsonResponse) {
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
                        .prevClose(Double.parseDouble(String.valueOf(indexedData.getBigDecimal("CH_PREVIOUS_CLS_PRICE"))))
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

    private String buildURL(StockHistoryRequest request) {
        String url = String.format(
                "https://www.nseindia.com/api/historical/cm/equity?symbol=%s&series=[\"%s\"]&from=%s&to=%s",
                request.getStockSymbol(),
                request.getSeries(),
                request.getFrom(),
                request.getTo()
        );
        log.error("Stock history build url. url: {}", url);
        return url;
    }

    private String buildURLForNextApi(StockHistoryRequest request) {
        String url = String.format(
                "https://www.nseindia.com/api/NextApi/apiClient/GetQuoteApi" +
                "?functionName=getHistoricalTradeData&symbol=%s&series=%s&fromDate=%s&toDate=%s&csv=true",
                request.getStockSymbol(),
                request.getSeries(),
                request.getFrom(),
                request.getTo()
        );
        log.info("NSE NextApi URL: {}", url);
        return url;
    }

    /**
     * Fetches stock history from NSE NextApi (GetQuoteApi) using Playwright browser cookies.
     * 
     * Flow:
     * 1. Try to get cached browser cookies (fast, 55-min TTL)
     * 2. If cache miss: Playwright launches Chromium, visits NSE, extracts cookies
     * 3. Merge with static file cookie (if available)
     * 4. Make request to NSE with merged cookies
     * 5. Parse CSV response and save to Cassandra
     * 
     * This is more robust than the old method because:
     * - Automatically refreshes cookies every 50 minutes
     * - Works even if static cookie.txt expires
     * - Uses real browser automation (handles CloudFlare, JS execution, etc.)
     */
    public Mono<StockHistory> getStockHistoryFromNextApi(StockHistoryRequest request) {
        if (isInvalidRequest(request)) {
            log.warn("Invalid stock history request for NextApi flow: {}", request);
            return Mono.empty();
        }

        return nseSessionManager.generateCookieUsingBrowserAutomation()
                .map(cookie -> buildWebClient(cookie, true))
                .flatMap(client -> fetchApiData(client, buildURLForNextApi(request), request.getStockSymbol()));
    }

    private String buildURLForCSVResp(StockHistoryRequest request) {
        String url = String.format(
                "https://www.nseindia.com/api/historical/cm/equity?symbol=%s&series=[\"%s\"]&from=%s&to=%s&csv=true",
                request.getStockSymbol(),
                request.getSeries(),
                request.getFrom(),
                request.getTo()
        );
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
                if (!line.isEmpty()) {
                    String[] values = line.split("\",\"", -1);
                    if (values.length < 14) {
                        log.warn("Skipping invalid CSV line: {}", line);
                    } else {
                        StockHistoryDetails details = StockHistoryDetails.builder()
                                .historyDate(LocalDate.parse(values[0].replace("\"", "").trim(), DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)))
                                .series(values[1].trim())
                                .open(parseDouble(values[2]))
                                .high(parseDouble(values[3]))
                                .low(parseDouble(values[4]))
                                .prevClose(parseDouble(values[5]))
                                .ltp(parseDouble(values[6]))
                                .close(parseDouble(values[7]))
                                .vwap(parseDouble(values[8]))
                                .fiftyTwoWeekHigh(parseDouble(values[9]))
                                .fiftyTwoWeekLow(parseDouble(values[10]))
                                .volume(values[11].trim())
                                .value(values[12].trim())
                                .totalTrades(values[13].replace("\"", "").trim())
                                .stockName(stockName)  // NSE CSV doesn't provide symbol, inject from method param
                                .isin("NA")            // CSV doesn't have ISIN
                                .build();
                        stockHistoryDetails.add(details);
                    }
                }
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
