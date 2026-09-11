package com.stock.entryloader;

import com.datastax.oss.driver.shaded.guava.common.collect.Maps;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.dto.StockInfoDTO;
import com.stock.dto.StockInfoDetails;
import com.stock.service.NseSessionManager;
import io.netty.resolver.DefaultAddressResolverGroup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

@Slf4j
@Service
public class StockInfoHttpEntryLoader {

    private static final String BASE_URL =
            "https://www.nseindia.com/";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/137.0.0.0 Safari/537.36";

    private static final String ACCEPT_LANGUAGE =
            "en-GB,en-US;q=0.9,en;q=0.8";

    private static final String ACCEPT_ENCODING =
            "gzip, deflate";

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper()
                    .configure(
                            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                            false);

    private final NseSessionManager nseSessionManager;

    public StockInfoHttpEntryLoader(
            NseSessionManager nseSessionManager) {

        this.nseSessionManager = nseSessionManager;
    }

    public Mono<StockInfoDetails> getStockDetails(
            String symbol) {

        return nseSessionManager.generateFreshSessionCookie()
                .map(this::buildWebClient)
                .flatMap(client ->
                        fetchApiData(client, symbol))
                .doOnNext(details ->
                        log.info(
                                "Fetched NSE stock details. Symbol={}, Details={}",
                                symbol,
                                details))
                .doOnError(error ->
                        log.error(
                                "Failed fetching NSE stock details. Symbol={}, Error={}",
                                symbol,
                                error.getMessage()));
    }

    private WebClient buildWebClient(
            String cookieHeader) {

        HttpClient httpClient =
                HttpClient.create()
                        .resolver(
                                DefaultAddressResolverGroup.INSTANCE)
                        .followRedirect(true);

        WebClient.Builder builder =
                WebClient.builder()
                        .baseUrl(BASE_URL)
                        .defaultHeader(
                                HttpHeaders.USER_AGENT,
                                USER_AGENT)
                        .defaultHeader(
                                HttpHeaders.ACCEPT_LANGUAGE,
                                ACCEPT_LANGUAGE)
                        .defaultHeader(
                                HttpHeaders.ACCEPT_ENCODING,
                                ACCEPT_ENCODING)
                        .clientConnector(
                                new ReactorClientHttpConnector(
                                        httpClient));

        if (cookieHeader != null &&
                !cookieHeader.isBlank()) {

            builder.defaultHeader(
                    HttpHeaders.COOKIE,
                    cookieHeader);

            log.info(
                    "✅ NSE cookie attached. Length={}",
                    cookieHeader.length());
        } else {

            log.warn(
                    "⚠️ No NSE cookie available");
        }

        return builder.build();
    }

    private Mono<StockInfoDetails> fetchApiData(
            WebClient client,
            String stockSymbol) {

        Predicate<Throwable> isRetryableError =
                throwable ->
                        throwable instanceof
                                WebClientResponseException ex
                                &&
                                (ex.getStatusCode().value() == 401
                                        || ex.getStatusCode().value() == 403);

        return client.get()
                .uri(uriBuilder ->
                        uriBuilder.path("/api/quote-equity")
                                .queryParam(
                                        "symbol",
                                        stockSymbol)
                                .build())
                .retrieve()
                .onStatus(
                        status -> status.value() != 200,
                        ClientResponse::createException)
                .bodyToMono(byte[].class)
                .map(StockInfoHttpEntryLoader::decodeResponse)
                .map(bytes ->
                        new String(
                                bytes,
                                StandardCharsets.UTF_8))
                .map(StockInfoHttpEntryLoader::convertResponseToDto)
                .filter(dto -> dto != null)
                .map(stockInfoDto -> {

                    HashMap<String, StockInfoDTO> stockInfoMap =
                            Maps.newHashMapWithExpectedSize(1);

                    if (stockInfoDto.getInfo() != null) {

                        stockInfoMap.put(
                                stockInfoDto.getInfo().getSymbol(),
                                stockInfoDto);
                    }

                    return StockInfoDetails.builder()
                            .key(LocalDate.now().toString())
                            .stockInfo(stockInfoMap)
                            .build();
                })
                .retryWhen(
                        Retry.from(retrySignals ->
                                retrySignals
                                        .flatMap(signal -> {

                                            if (isRetryableError.test(
                                                    signal.failure())) {

                                                log.warn(
                                                        "Retrying NSE request due to {}",
                                                        signal.failure()
                                                                .getMessage());

                                                return Mono.just(signal);
                                            }

                                            return Mono.error(
                                                    signal.failure());
                                        })
                                        .delayElements(
                                                Duration.ofSeconds(2))
                                        .take(3)))
                .doOnError(error ->
                        log.error(
                                "Failed to fetch NSE data for symbol {}. Error={}",
                                stockSymbol,
                                error.getMessage()));
    }

    private static byte[] decodeResponse(
            byte[] responseBytes) {

        try {

            return decompressGzip(responseBytes);

        } catch (Exception ignored) {

            return responseBytes;
        }
    }

    private static byte[] decompressGzip(
            byte[] compressed) {

        try (GZIPInputStream gis =
                     new GZIPInputStream(
                             new ByteArrayInputStream(
                                     compressed));

             BufferedReader reader =
                     new BufferedReader(
                             new InputStreamReader(
                                     gis,
                                     StandardCharsets.UTF_8))) {

            return reader.lines()
                    .collect(Collectors.joining("\n"))
                    .getBytes(StandardCharsets.UTF_8);

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to decompress GZIP",
                    e);
        }
    }

    private static StockInfoDTO convertResponseToDto(
            String jsonResponse) {

        try {

            return OBJECT_MAPPER.readValue(
                    jsonResponse,
                    StockInfoDTO.class);

        } catch (Exception e) {

            log.error(
                    "Failed to parse NSE response",
                    e);

            return null;
        }
    }
}